(ns car-cpu-clj.interpreter.com-protocol-interpreter
  (:require [car-data-clj.core :as data :refer [make-request]]
            [car-data-clj.db :as db]
            [car-cpu-clj.temperature-reader :as temp]
            [car-cpu-clj.speed-rpm-reader :as speed]
            [car-cpu-clj.fuel-reader :as diesel]
            [car-cpu-clj.ignition-reader :as ignition]
            [car-cpu-clj.interpreter.interpreter :refer [create-log if-car-running->error]])
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
(def diesel-value 224)
(def ignition-on 171)
(def ignition-off 170)
(def turn-off 168)
;;0.0020904884039643 x X2 - 0.76907118482305 x X + 70.582680644319

(defmulti command->action-ignition-off
          (fn [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id] (:command cmd-map)))

(defmethod command->action-ignition-off ignition-on [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (ignition/->ignition-on dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id))

(defmethod command->action-ignition-off turn-off [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (try
    (.exec (Runtime/getRuntime) "/etc/init.d/turnOff.sh")
    (catch Exception e
      (create-log "ERROR" "Could not read script to turn cpu off")))
  [abs-km diesel-buffer temp-buffer settings-id])

(defmethod command->action-ignition-off :default [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
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

(defmethod command->action-ignition-on diesel-value [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (diesel/fuel-interpreter dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id))

(defmethod command->action-ignition-on temperature-value [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (temp/temperature-interpreter dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id))

(defmethod command->action-ignition-on ignition-off [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (ignition/->ignition-off dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id))

(defmethod command->action-ignition-on :default [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  [abs-km diesel-buffer temp-buffer settings-id])
