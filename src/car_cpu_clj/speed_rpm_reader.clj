(ns car-cpu-clj.speed-rpm-reader
  (:require [car-data-clj.db :as db]
            [car-data-clj.core :refer [make-request]]))

(def trip-length (atom 0))
(def max-speed (atom 0))

(defn reset-speed-atoms []
  (reset! max-speed 0)
  (reset! trip-length 0))

(defn calculate-distance [speed]
  (* (* 0.89288 (Math/pow 1.0073 speed) 0.00181)))

(defn speed-distance-interpreter [dashboard speed trip-km abs-km]
  (if (and (> speed 0) (<= speed 220) (> (.getRpm dashboard) 0))
    (let [distance (calculate-distance speed)
          trip (+ trip-km distance)
          abs (+ abs-km distance)]
      (when (> speed @max-speed)
        (reset! max-speed speed))
      (swap! trip-length + distance)
      (doto dashboard
        (.setDistance trip)
        (.setTotalDistance abs)
        (.setSpeed speed))
      [trip abs])
    [trip-km abs-km]))

(defn create-speed-data [speed rpm trip-id]
  (make-request {:op_type "car_speed_new"
                 :id (db/uuid)
                 :trip_id trip-id
                 :speed speed
                 :rpm rpm
                 :gear 0}))