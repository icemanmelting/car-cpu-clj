(ns car-cpu-clj.temperature-reader
  (:require [car-data-clj.db.postgresql :refer [uuid]]
            [car-data-clj.core :refer [make-request]]
            [car-cpu-clj.interpreter.interpreter :refer [step car-pullup-resistor-value voltage-level create-log avg]])
  (:import (pt.iceman.carscreentools Dashboard)))

(def car-thermistor-alpha-value -0.00001423854206)
(def car-thermistor-beta-value 0.0007620444171)
(def car-thermistor-c-value -0.000006511973919)

(def max-temp (atom 0))

(def temperature-buffer-size 256)

(defn reset-temp-atom [val]
  (reset! max-temp val))

(defn- calculate-temperature [dashboard analog-level]
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

(defn temperature-interpreter [dashboard {val :value :as cmd-map} abs-km diesel-buffer temp-buffer settings-id]
  (if (< (count temp-buffer) temperature-buffer-size)
    [abs-km diesel-buffer (conj temp-buffer val) settings-id]
    (let [temp (calculate-temperature dashboard (avg temp-buffer))]
      (when (> temp 110)
        (create-log "INFO" "Engine temperature critical!"))
      [abs-km diesel-buffer [] settings-id])))

(defn create-temperature-data [temp trip-id]
  (make-request {:op_type "car_temp_new"
                 :id (uuid)
                 :trip_id trip-id
                 :val temp}))
