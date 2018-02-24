(ns car-cpu-clj.fuel-reader
  (:require [car-cpu-clj.interpreter.interpreter :refer [step car-pullup-resistor-value voltage-level create-log avg]])
  (:import (pt.iceman.carscreentools Dashboard)))

(def diesel-buffer-size 512)

(defn calculate-fuel-level [dashboard analog-level]
  (let [v-lvl (* (/ analog-level 0.68) step);;todo - dirty hack -> to be fixed later
        resistance (/ (* v-lvl car-pullup-resistor-value) (- voltage-level v-lvl))
        fuel-lvl (+ -50153.53 (/ (- 104.5813 -50153.53) (+ 1 (Math/pow (/ resistance 16570840000) 0.3447283))))]
    (if (< fuel-lvl 0)
      (.setDiesel dashboard 0)
      (.setDiesel dashboard fuel-lvl))
    (when (< fuel-lvl 6)
      (create-log "INFO" "Reserve fuel reached. Please refill tank!"))
    fuel-lvl))

(defn- fuel-interpreter [dashboard {val :value :as cmd-map} abs-km diesel-buffer temp-buffer settings-id]
  (if (< (count diesel-buffer) diesel-buffer-size)
    [abs-km (conj diesel-buffer val) temp-buffer settings-id]
    (do
      (calculate-fuel-level dashboard (avg diesel-buffer))
      [abs-km [] temp-buffer settings-id])))
