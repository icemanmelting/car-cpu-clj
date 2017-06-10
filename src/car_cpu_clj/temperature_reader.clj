(ns car-cpu-clj.temperature-reader
  (:require [car-data-clj.db :as db]
            [car-data-clj.core :refer [make-request]]))

(def car-thermistor-alpha-value -0.00001423854206)
(def car-thermistor-beta-value 0.0007620444171)
(def car-thermistor-c-value -0.000006511973919)
(def car-pullup-resistor-value 975)
(def voltage-level 12)
(def pin-resolution 1023)
(def step (/ 15 pin-resolution))

(def max-temp (atom 0))

(defn reset-temp-atom [val]
  (reset! max-temp val))

(defn calculate-temperature [dashboard analog-level]
  (let [v-lvl (* analog-level step)
        resistance (/ (* v-lvl car-pullup-resistor-value) (- voltage-level v-lvl))
        temp (- (/ 1 (+ car-thermistor-alpha-value
                        (* car-thermistor-beta-value (Math/log resistance))
                        (* car-thermistor-c-value (Math/pow (Math/log resistance) 3))))
                273.15)]
    (.setTemp dashboard temp)
    (when (> temp @max-temp)
      (reset-temp-atom temp))
    temp))

(defn create-temperature-data [temp trip-id]
  (make-request {:op_type "car_temp_new"
                 :id (db/uuid)
                 :trip_id trip-id
                 :val temp}))


