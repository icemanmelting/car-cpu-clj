(ns car-cpu-clj.core-test
  (:require [clojure.test :refer :all]
            [car-cpu-clj.core :refer :all]
            [car-data-clj.core :as data]
            [car-data-clj.db :as db])
  (:import (pt.iceman.carscreentools Dashboard UDPClient ScreenLoader)
           (javafx.application Application)
           (javafx.stage Stage)))

(def TDashboard
  (proxy [Dashboard] []))

(deftest testing-dashboard
  (testing ""
    (let [settings-id (db/uuid)]
      (data/make-request {:op_type "car_settings_new"
                          :id settings-id
                          :cnst_km 20000
                          :trip_km 1000})
      (Thread/sleep 1000)
      (-startCPU TDashboard settings-id)
      (is (= 20000.0 (.getTotalDistance TDashboard)))
      (is (= 1000.0 (.getDistance TDashboard)))

      (UDPClient/send (byte-array 1 [UDPClient/IGNITION_ON])
                      "localhost" 9887)
      (doseq [x (range 600)]
        (UDPClient/send (byte-array 3 [UDPClient/TEMPERATURE_VALUE (byte -127) (byte 0)])
                        "localhost" 9887)
        (UDPClient/send (byte-array 3 [UDPClient/DIESEL_VALUE (byte -127) (byte 0)])
                        "localhost" 9887)
        (UDPClient/send (byte-array 3 [UDPClient/SPEED_PULSE (byte -127) (byte 0)])
                        "localhost" 9887)
        (UDPClient/send (byte-array 3 [UDPClient/RPM_PULSE (byte -127) (byte 0)])
                        "localhost" 9887))
      (Thread/sleep 1000)
      (is (= 24.192844194003555 (.getDiesel TDashboard)))
      (is (= 749.0322580645161 (.getRpm TDashboard)))
      (is (= 56.42013973703632 (.getTemp TDashboard)))
      (is (= 129.0 (.getSpeed TDashboard))))))

(run-tests)