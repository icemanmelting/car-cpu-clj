(ns car-cpu-clj.com-protocol-interpreter
  (:require [car-data-clj.core :as data :refer [make-request]]
            [car-data-clj.db :as db]
            [car-cpu-clj.temperature-reader :as temp]
            [car-cpu-clj.speed-rpm-reader :as speed]
            [overtone.at-at :refer [at every mk-pool now stop-and-reset-pool!]])
  (:import (pt.iceman.carscreentools Dashboard)))

(def abs-anomaly-off 128)
(def abs-anomaly-on 143)
(def battery-off 32)
(def battery-on 47)
(def brakes-oil-off 64)
(def brakes-oil-on 79)
(def high-beam-off 144)
(def high-beam-on 159)
(def oil-pressure-off 16)
(def oil-pressure-on 31)
(def parking-brake-off 48)
(def parking-brake-on 63)
(def turning-signs-off 80)
(def turning-signs-on 95)
(def spark-plugs-off 112)
(def spark-plugs-on 127)
(def reset-trip-km 1)
(def rpm-pulse 180)
(def speed-pulse 176)
(def temperature-value 192)
(def temperature-buffer-size 256)
(def diesel-value 224)
(def diesel-buffer-size 512)
(def ignition-on 171)
(def ignition-off 170)
(def turn-off 168)

(def ignition (atom false))
(def trip-id (atom (db/uuid)))
(def my-pool (mk-pool))

(defn avg [ar] (/ (reduce + ar) (count ar)))

(defn- create-log [type msg]
  (make-request {:op_type "car_log_new"
                 :id (db/uuid)
                 :trip_id @trip-id
                 :msg msg
                 :log_level type}))

(defn- if-car-running->error [dashboard msg]
  (when (> (.getRpm dashboard) 0)
    (create-log "ERROR" msg)))

(defn- reset-atoms []
  (reset! ignition true)
  (reset! trip-id (db/uuid))
  (temp/reset-temp-atom 0)
  (speed/reset-speed-atoms))

(defn- start-new-trip [dashboard settings-id]
  (reset-atoms)
  (make-request {:op_type "car_trip_new"
                 :id @trip-id
                 :starting_km (.getTotalDistance dashboard)})
  (every 200000 #(speed/create-speed-data (.getSpeed dashboard)
                                          (.getRpm dashboard)
                                          @trip-id)
         my-pool)
  (every 200000 #(temp/create-temperature-data (.getTemp dashboard)
                                               @trip-id)
         my-pool)
  (every 200000 #(make-request {:op_type "car_settings_up"
                                :id settings-id
                                :constant_km (.getTotalDistance dashboard)
                                :trip_km (.getDistance dashboard)})
         my-pool))

(defn- reset-dashboard [dashboard]
  (doto dashboard
    (.setDiesel 0)
    (.setTemp 0)
    (.setRpm 0)
    (.setSpeed 0)
    (.setGear 0)
    (.setAbs false)
    (.setSparkPlug false)
    (.setParking false)
    (.setTurnSigns false)
    (.setOilPressure false)
    (.setBrakesOil false)
    (.setBattery false)))

(def car-pullup-resistor-value 975)
(def voltage-level 10.05)
(def pin-resolution 1023)
(def step (/ 15 pin-resolution))
;;0.0020904884039643 x X2 - 0.76907118482305 x X + 70.582680644319

(defn fuel-value-interpreter [dashboard analog-level]
  (let [v-lvl (* (/ analog-level 0.68) step)                ;;dirty hack -> to be fixed later
        resistance (/ (* v-lvl car-pullup-resistor-value) (- voltage-level v-lvl))
        fuel-lvl (+ -50153.53 (/ (- 104.5813 -50153.53) (+ 1 (Math/pow (/ resistance 16570840000) 0.3447283))))]
    (if (< fuel-lvl 0)
      (.setDiesel dashboard 0)
      (.setDiesel dashboard fuel-lvl))
    (when (< fuel-lvl 6)
      (create-log "INFO" "Reserve fuel reached. Please refill tank!"))
    fuel-lvl))

(defmulti command->action-ignition-off
          (fn [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id] (:command cmd-map)))

(defmethod command->action-ignition-off ignition-on [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (try
    (start-new-trip dashboard settings-id)
    (create-log "INFO" "Ignition turned on")
    (.exec (Runtime/getRuntime) "/etc/init.d/turnonscreen.sh")
    (catch Exception e
      (create-log "ERROR" "Could not read script to turn on screen")))
  [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-off turn-off [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (try
    (.exec (Runtime/getRuntime) "/etc/init.d/turnOff.sh")
    (catch Exception e
      (create-log "ERROR" "Could not read script to turn cpu off")))
  [abs-km diesel-buffer temp-buffer settings-id])

(defmulti command->action-ignition-on
          (fn [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id] (:command cmd-map)))

(defmethod command->action-ignition-on abs-anomaly-off [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (.setAbs dashboard false)
  [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-on abs-anomaly-on [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (.setAbs dashboard true)
  (if-car-running->error dashboard "ABS sensor error. Maybe one of the speed sensors is broken?")
  [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-on battery-off [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (.setBattery dashboard false)
  [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-on battery-on [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (.setBattery dashboard true)
  (if-car-running->error dashboard "Battery not charging. Something wrong with the alternator.")
  [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-on brakes-oil-off [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (.setBrakesOil dashboard false)
  [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-on brakes-oil-on [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (.setBrakesOil dashboard true)
  (if-car-running->error dashboard "Brakes Oil pressure is too low!")
  [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-on high-beam-off [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (create-log "INFO" "High beams off")
  (.setHighBeams dashboard false)
  [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-on high-beam-on [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (create-log "INFO" "High beams on")
  (.setHighBeams dashboard true)
  [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-on oil-pressure-off [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (.setOilPressure dashboard false) [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-on oil-pressure-on [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (.setOilPressure dashboard true)
  (if-car-running->error dashboard "Engine's oil pressure is low.")
  [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-on parking-brake-off [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (create-log "INFO" "Car park brake disengaged")
  (.setParking dashboard false)
  [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-on parking-brake-on [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (create-log "INFO" "Car park brake engaged")
  (.setParking dashboard true)
  [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-on turning-signs-off [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (.setTurnSigns dashboard false)
  [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-on turning-signs-on [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (.setTurnSigns dashboard true)
  [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-on spark-plugs-off [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (.setSparkPlug dashboard false)
  [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-on spark-plugs-on [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (.setSparkPlug dashboard true)
  [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-on reset-trip-km [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (create-log "INFO" "Trip km reseted")
  (speed/reset-trip-distance dashboard)
  [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-on speed-pulse [dashboard {val :value :as cmd-map} abs-km diesel-buffer temp-buffer settings-id]
  (let [abs (speed/speed-distance-interpreter dashboard val abs-km)]
    [abs diesel-buffer temp-buffer settings-id]))

(defmethod command->action-ignition-on rpm-pulse [dashboard {val :value :as cmd-map} abs-km diesel-buffer temp-buffer settings-id]
  (speed/set-rpm dashboard val)
  [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-on diesel-value [dashboard {val :value :as cmd-map} abs-km diesel-buffer temp-buffer settings-id]
  (if (< (count diesel-buffer) diesel-buffer-size)
    [abs-km (conj diesel-buffer val) temp-buffer settings-id]
    (do
      (fuel-value-interpreter dashboard (avg diesel-buffer))
      [abs-km [] temp-buffer settings-id])))

(defmethod command->action-ignition-on temperature-value [dashboard {val :value :as cmd-map} abs-km diesel-buffer temp-buffer settings-id]
  (if (< (count temp-buffer) temperature-buffer-size)
    [abs-km diesel-buffer (conj temp-buffer val) settings-id]
    (let [temp (temp/calculate-temperature dashboard (avg temp-buffer))]
      (when (> temp 110)
        (create-log "INFO" "Engine temperature critical!"))
      [abs-km diesel-buffer [] settings-id])))

(defmethod command->action-ignition-on ignition-off [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (reset! ignition false)
  (stop-and-reset-pool! my-pool :strategy :kill)
  (make-request {:op_type "car_trip_up"
                 :id @trip-id
                 :ending_km abs-km
                 :max_temp @temp/max-temp
                 :max_speed @speed/max-speed
                 :trip_l @speed/trip-length})
  (at (+ 5000 (now)) #(try
                        (.exec (Runtime/getRuntime) "/etc/init.d/shutdownScreen.sh")
                        (catch Exception e
                          (create-log "INFO" "Could not read script to shutdown screen"))) my-pool)
  (reset-dashboard dashboard)
  (create-log "INFO" "Ignition turned off")
  (make-request {:op_type "car_settings_up"
                 :id settings-id
                 :constant_km abs-km
                 :trip_km @speed/trip-length})
  [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-on :default [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id])

(defmulti ignition-state
          (fn [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
            @ignition))

(defmethod ignition-state false [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (command->action-ignition-off dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id))

(defmethod ignition-state true [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (command->action-ignition-on dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id))