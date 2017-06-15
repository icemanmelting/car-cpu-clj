(ns car-cpu-clj.core
  (:gen-class
    :name pt.iceman.CarCPU
    :state state
    :init init
    :methods [#^{:static true} [startCPU [pt.iceman.carscreentools.Dashboard java.util.UUID] void]
              #^{:static true} [resetDashTripKm [] boolean]])
  (:require [car-data-clj.core :refer [make-request]]
            [clojure.core.async :as a :refer [<! <!! chan go-loop >!! go >!]]
            [car-data-clj.core :as data]
            [car-data-clj.db :as db]
            [car-cpu-clj.temperature-reader :as temp]
            [car-cpu-clj.speed-rpm-reader :as speed]
            [overtone.at-at :refer [at every mk-pool now stop-and-reset-pool!]])
  (import (java.net DatagramSocket
                    DatagramPacket
                    InetSocketAddress)
          (pt.iceman.carscreentools Dashboard)
          (java.util UUID)))

(def abs-anomaly-off 128)
(def abs-anomaly-on 143)
(def battery-off 32)
(def battery-on 47)
(def brakes-oil-off 64)
(def brakes-oil-on 79)
(def hig-beam-off 144)
(def high-beam-on 159)
(def oil-pressure-off 16)
(def oil-pressure-on 31)
(def parking-brake-off 48)
(def parking-brake-on 63)
(def turning-signs-off 80)
(def turning-signs-on 95)
(def spark-plugs-off 112)
(def spark-plugs-on 127)
(def reset-trip-km 255)
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

(defn make-socket
  ([] (new DatagramSocket))
  ([port] (new DatagramSocket port)))

(def cpu-channel (chan))

(defn -resetDashTripKm
  "Method to be used from the Java GUI to reset dashboard trip km"
  []
  (>!! cpu-channel {:command reset-trip-km}))

(defn send-packet
  "Send a short textual message over a DatagramSocket to the specified
  host and port. If the string is over 512 bytes long, it will be
  truncated."
  [^DatagramSocket socket msg host port]
  (let [payload (.getBytes msg)
        length (min (alength payload) 3)
        address (InetSocketAddress. host port)
        packet (DatagramPacket. payload length address)]
    (.send socket packet)))

(defn receive-packet
  "Block until a UDP message is received on the given DatagramSocket, and
  return the payload message as a string."
  [^DatagramSocket socket]
  (let [buffer (byte-array 3)
        packet (DatagramPacket. buffer 3)]
    (.receive socket packet)
    (.getData packet)))

(defn bytes-to-int
  ([bytes]
   (bit-or (bit-and (first bytes)
                    0xFF)
           (bit-and (bit-shift-left (second bytes) 8)
                    0xFF00))))

(defn byte-to-int [byte]
  (bit-and byte 0xFF))

(defn- process-command [cmd-ar]
  (let [ar-size (count cmd-ar)]
    (if (= 1 ar-size)
      {:command (byte-to-int (first cmd-ar))}
      {:command (byte-to-int (first cmd-ar))
       :value (bytes-to-int (rest cmd-ar))})))

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
    (.setAbs false)
    (.setSparkPlug false)
    (.setParking false)
    (.setTurnSigns false)
    (.setOilPressure false)
    (.setBrakesOil false)
    (.setBattery false)))

(defn fuel-value-interpreter [dashboard resistance]
  (let [fuel-lvl (+ -50153.53 (/ (- 104.5813 -50153.53) (+ 1 (Math/pow (/ resistance 16570840000) 0.3447283))))]
    (if (< fuel-lvl 0)
      (.setDiesel dashboard 0)
      (.setDiesel dashboard fuel-lvl))
    (when (< fuel-lvl 6)
      (create-log "INFO" "Reserve fuel reached. Please refill tank!"))
    fuel-lvl))

(defn- interpret-command [dashboard cmd-map trip-km abs-km diesel-buffer temp-buffer settings-id]
  (let [cmd (:command cmd-map)
        val (:value cmd-map)]
    (cond
      (= ignition-on cmd) (do (try
                                (start-new-trip dashboard settings-id)
                                (create-log "INFO" "Ignition turned on")
                                (.exec (Runtime/getRuntime) "/etc/init.d/turnonscreen.sh")
                                (catch Exception e
                                  (create-log "ERROR" "Could not read script to turn on screen")))
                              [trip-km abs-km diesel-buffer temp-buffer settings-id])
      (= turn-off cmd) (do (try
                             (.exec (Runtime/getRuntime) "/etc/init.d/turnOff.sh")
                             (catch Exception e
                               (create-log "ERROR" "Could not read script to turn cpu off")))
                           [trip-km abs-km diesel-buffer temp-buffer settings-id])
      @ignition (cond
                  (= abs-anomaly-off cmd) (do (.setAbs dashboard false) [trip-km abs-km diesel-buffer temp-buffer settings-id])
                  (= abs-anomaly-on cmd) (do (.setAbs dashboard true)
                                             (if-car-running->error dashboard "ABS sensor error. Maybe one of the speed sensors s broken?")
                                             [trip-km abs-km diesel-buffer temp-buffer settings-id])
                  (= battery-off cmd) (do (.setBattery dashboard false) [trip-km abs-km diesel-buffer temp-buffer settings-id])
                  (= battery-on cmd) (do (.setBattery dashboard true)
                                         (if-car-running->error dashboard "Battery not charging. Something wrong with the alternator.")
                                         [trip-km abs-km diesel-buffer temp-buffer settings-id])
                  (= brakes-oil-off cmd) (do (.setBrakesOil dashboard false) [trip-km abs-km diesel-buffer temp-buffer settings-id])
                  (= brakes-oil-on cmd) (do (.setBrakesOil dashboard true)
                                            (if-car-running->error dashboard "Brakes Oil pressure is too low!")
                                            [trip-km abs-km diesel-buffer temp-buffer settings-id])
                  (= hig-beam-off cmd) (do
                                         (create-log "INFO" "High beams off")
                                         (.setHighBeams dashboard false)
                                         [trip-km abs-km diesel-buffer temp-buffer settings-id])
                  (= high-beam-on cmd) (do
                                         (create-log "INFO" "High beams on")
                                         (.setHighBeams dashboard true)
                                         [trip-km abs-km diesel-buffer temp-buffer settings-id])
                  (= oil-pressure-off cmd) (do (.setOilPressure dashboard false) [trip-km abs-km diesel-buffer temp-buffer settings-id])
                  (= oil-pressure-on cmd) (do (.setOilPressure dashboard true)
                                              (if-car-running->error dashboard "Engine's oil pressure is low.")
                                              [trip-km abs-km diesel-buffer temp-buffer settings-id])
                  (= parking-brake-off cmd) (do
                                              (create-log "INFO" "Car park brake disengaged")
                                              (.setParking dashboard false)
                                              [trip-km abs-km diesel-buffer temp-buffer settings-id])
                  (= parking-brake-on cmd) (do
                                             (create-log "INFO" "Car park brake used")
                                             (.setParking dashboard true)
                                             [trip-km abs-km diesel-buffer temp-buffer settings-id])
                  (= turning-signs-off cmd) (do (.setTurnSigns dashboard false) [trip-km abs-km diesel-buffer temp-buffer settings-id])
                  (= turning-signs-on cmd) (do (.setTurnSigns dashboard true) [trip-km abs-km diesel-buffer temp-buffer settings-id])
                  (= spark-plugs-off cmd) (do (.setSparkPlug dashboard false) [trip-km abs-km diesel-buffer temp-buffer settings-id])
                  (= spark-plugs-on cmd) (do (.setSparkPlug dashboard true) [trip-km abs-km diesel-buffer temp-buffer settings-id])
                  (= reset-trip-km cmd) (do
                                          (create-log "INFO" "Trip km reseted")
                                          (.resetDistance dashboard)
                                          [0 abs-km diesel-buffer temp-buffer settings-id])
                  (= speed-pulse cmd) (let [[trip abs] (speed/speed-distance-interpreter dashboard val trip-km abs-km)]
                                        [trip abs diesel-buffer temp-buffer settings-id])
                  (= rpm-pulse cmd) (do (speed/set-rpm dashboard val) [trip-km abs-km diesel-buffer temp-buffer settings-id])
                  (= diesel-value cmd) (if (< (count diesel-buffer) diesel-buffer-size)
                                         [trip-km abs-km (conj diesel-buffer val) temp-buffer settings-id]
                                         (do (fuel-value-interpreter dashboard (avg diesel-buffer))
                                             [trip-km abs-km [] temp-buffer settings-id]))
                  (= temperature-value cmd) (if (< (count temp-buffer) temperature-buffer-size)
                                              [trip-km abs-km diesel-buffer (conj temp-buffer val) settings-id]
                                              (let [temp (temp/calculate-temperature dashboard (avg temp-buffer))]
                                                (when (> temp 110)
                                                  (create-log "INFO" "Engine temperature critical!"))
                                                [trip-km abs-km diesel-buffer [] settings-id]))

                  (= ignition-off cmd) (do (reset! ignition false)
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
                                                          :trip_km trip-km})
                                           [trip-km abs-km diesel-buffer temp-buffer settings-id])
                  :else [trip-km abs-km diesel-buffer temp-buffer settings-id])
      :else [trip-km abs-km diesel-buffer temp-buffer settings-id])))

(defn receive-loop
  "Given a function and DatagramSocket, will (in another thread) wait
  for the socket to receive a message, and whenever it does, will call
  the provided function on the incoming message."
  [socket dashboard]
  (go-loop []
    (>! cpu-channel (process-command (byte-array (receive-packet socket))))
    (recur)))

(defn -startCPU
  "Method to be called from the JavaGUI to start listening for
  incoming communication from the car's MCU"
  [dashboard id]
  (if-let [car-settings (data/read-settings id)]
    (let [trip-km (:trip_kilometers car-settings)
          cnst-km (:constant_kilometers car-settings)]
      (doto dashboard (.setDistance trip-km)
                      (.setTotalDistance cnst-km))
      (receive-loop (make-socket 9887) dashboard)
      (go-loop [trip trip-km
                absolute-km cnst-km
                diesel-buffer []
                temp-buffer []
                settings-id id]
        (let [record (<!! cpu-channel)
              [trip-km abs-km d-buffer t-buffer settings-id] (interpret-command dashboard
                                                                                record
                                                                                trip
                                                                                absolute-km
                                                                                diesel-buffer
                                                                                temp-buffer
                                                                                settings-id)]
          (recur trip-km abs-km d-buffer t-buffer settings-id))))
    (prn "Could not get car settings, is there somethign wrong with the connection?")))
