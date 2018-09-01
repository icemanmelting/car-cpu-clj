(ns car-cpu-clj.core-test
  (:require [clojure.test :refer :all]
            [car-cpu-clj.core :refer :all]
            [car-data-clj.core :as data]
            [car-data-clj.db.postgresql :refer [uuid db]]
            [car-data-clj.config :refer [read-from-resource]]
            [hugsql.core :refer :all])
  (:import (pt.iceman.carscreentools Dashboard UDPClient ScreenLoader)
           (javafx.application Application)
           (javafx.stage Stage)))

(def ^:private owner "foo@bar.com")

(def ^:private car-id (-> (read-from-resource "configuration.edn") :car :car-id))

(def-db-fns "cars.sql")

(defn- setup-session []
  (db-run db (str "INSERT INTO users (login, password, salt) VALUES"
                  "('" owner "', '3bb95188c01763e81875ce9644f496a4e3f98d9eb181c5f128ba32a01b62b6de', 'bar');"))
  (db-run db (str "INSERT INTO sessions (id, login, seen) VALUES"
                  "('00000000-0000-0000-0000-000000000000', '" owner "', now());")))

(defn- clear-users [] (db-run db "TRUNCATE users CASCADE"))

(defn- clear-car-trips [] (db-run db "TRUNCATE car_trips CASCADE"))

(defn clear-ks []
  (clear-users)
  (clear-car-trips))

(defn clear-ks-fixture [f]
  (clear-ks)
  (setup-session)
  (f))

(use-fixtures :each clear-ks-fixture)

(def TDashboard
  (proxy [Dashboard] []))

(deftest testing-dashboard
  (testing ""
      (data/make-request {:op_type "car_new"
                          :id car-id
                          :cnst_km 20000
                          :trip_km 1000})
      (Thread/sleep 1000)
      (-startCPU TDashboard)
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
      (is (= 7.076796886707598 (.getDiesel TDashboard)))
      (is (= 749.0322580645161 (.getRpm TDashboard)))
      (is (= 51.59437018482447 (.getTemp TDashboard)))
      (is (= 129.0 (.getSpeed TDashboard)))))
