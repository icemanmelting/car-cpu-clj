(ns car-cpu-clj.ignition-reader
  (:require [car-cpu-clj.speed-rpm-reader :as speed]
            [car-cpu-clj.temperature-reader :as temp]
            [car-data-clj.core :refer [make-request]]
            [car-data-clj.db.postgresql :refer [uuid]]
            [car-cpu-clj.interpreter.interpreter :refer [trip-id car-id create-log reset-dashboard]]
            [overtone.at-at :refer [at every mk-pool now stop-and-reset-pool!]])
  (:import (pt.iceman.carscreentools Dashboard)))

(def ignition (atom false))

(def my-pool (mk-pool))

(defn reset-ignition-atom [val]
  (reset! ignition val))

(defn- reset-atoms []
  (reset! trip-id (uuid))
  (reset-ignition-atom true)
  (temp/reset-temp-atom 0)
  (speed/reset-speed-atoms))

(defn- start-new-trip [dashboard settings-id]
  (reset-atoms)
  (make-request {:op_type "car_trip_new"
                 :id @trip-id
                 :car_id @car-id
                 :starting_km (.getTotalDistance dashboard)})
  (every 200000 #(speed/create-speed-data (.getSpeed dashboard)
                                          (.getRpm dashboard)
                                          @trip-id)
         my-pool)
  (every 200000 #(temp/create-temperature-data (.getTemp dashboard)
                                               @trip-id)
         my-pool)
  (every 200000 #(make-request {:op_type "car_up"
                                :id settings-id
                                :constant_km (.getTotalDistance dashboard)
                                :trip_km (.getDistance dashboard)})
         my-pool))

(defn ->ignition-on [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (try
    (start-new-trip dashboard settings-id)
    (create-log "INFO" "Ignition turned on")
    (.exec (Runtime/getRuntime) "/etc/init.d/turnonscreen.sh")
    (catch Exception e
      (create-log "ERROR" "Could not read script to turn on screen")))
  [abs-km diesel-buffer temp-buffer settings-id])

(defn ->ignition-off [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
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
