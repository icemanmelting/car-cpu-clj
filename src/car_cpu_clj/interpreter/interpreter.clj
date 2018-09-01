(ns car-cpu-clj.interpreter.interpreter
  (:require [car-data-clj.db.postgresql :refer [uuid]]
            [car-data-clj.core :as data :refer [make-request]]
            [car-data-clj.config :refer [read-from-resource]])
  (:import (pt.iceman.carscreentools Dashboard)))

(def car-id (atom (-> (read-from-resource "configuration.edn") :car :car-id)))

(def car-pullup-resistor-value 975)

(def voltage-level 10.05)

(def pin-resolution 1023)

(def step (/ 15 pin-resolution))

(def trip-id (atom (uuid)))

(defn avg [ar] (/ (reduce + ar) (count ar)))

(defn create-log [type msg]
  (make-request {:op_type "car_log_new"
                 :id (uuid)
                 :trip_id @trip-id
                 :msg msg
                 :log_level type}))

(defn if-car-running->error [dashboard msg]
  (when (> (.getRpm dashboard) 0)
    (create-log "ERROR" msg)))

(defn reset-dashboard [dashboard]
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
