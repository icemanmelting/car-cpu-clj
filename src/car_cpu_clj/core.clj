(ns car-cpu-clj.core
  (:gen-class
    :name pt.iceman.CarCPU
    :state state
    :init init
    :methods [#^{:static true} [startCPU [pt.iceman.carscreentools.Dashboard java.util.UUID] void]
              #^{:static true} [resetDashTripKm [] boolean]])
  (:require [car-data-clj.core :refer [make-request]]
            [clojure.core.async :as a :refer [<! <!! chan go-loop >!! go >!]]
            [car-data-clj.core :as data])
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

;;values for temperature calculation
(def car-thermistor-alpha-value -0.00001423854206)
(def car-thermistor-beta-value 0.0007620444171)
(def car-thermistor-c-value -0.000006511973919)
(def car-pullup-resistor-value 975)
(def voltage-level 12)
(def pin-resolution 1023)
(def step (/ 15 pin-resolution))

(defn- calculate-temperature [analog-level]
  (let [v-lvl (* analog-level step)
        resistance (/ (* v-lvl car-pullup-resistor-value) (- voltage-level v-lvl))]
    (- (/ 1 (+ car-thermistor-alpha-value
               (* car-thermistor-beta-value (Math/log resistance))
               (* car-thermistor-c-value (Math/pow (Math/log resistance) 3))))
       273.15)))

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

(defn calculate-distance [speed]
  (* (* 0.89288 (Math/pow 1.0073 speed) 0.00181)))

(defn- speed-distance-interpreter [dashboard speed trip-km abs-km]
  (if (and (> speed 0) (<= speed 220))
    (let [distance (calculate-distance speed)
          trip (+ trip-km distance)
          abs (+ abs-km distance)]
      (doto dashboard
        (.setDistance trip)
        (.setTotalDistance abs))
      (.setSpeed dashboard val)
      [trip abs])
    [trip-km abs-km]))

(defn avg [ar] (/ (reduce + ar) (count ar)))

(defn fuel-value-interpreter [resistance]
  (+ -50153.53 (/ (- 104.5813 -50153.53) (+ 1 (Math/pow (/ resistance 16570840000) 0.3447283)))))

(defn- interpret-command [dashboard cmd-map trip-km abs-km diesel-buffer temp-buffer]
  (let [cmd (:command cmd-map)
        val (:value cmd-map)]
    (cond
      (= abs-anomaly-off cmd) (do (.setAbs dashboard false) [trip-km abs-km diesel-buffer temp-buffer])
      (= abs-anomaly-on cmd) (do (.setAbs dashboard true) [trip-km abs-km diesel-buffer temp-buffer])
      (= battery-off cmd) (do (.setBattery dashboard false) [trip-km abs-km diesel-buffer temp-buffer])
      (= battery-on cmd) (do (.setBattery dashboard true) [trip-km abs-km diesel-buffer temp-buffer])
      (= brakes-oil-off cmd) (do (.setBrakesOil dashboard false) [trip-km abs-km diesel-buffer temp-buffer])
      (= brakes-oil-on cmd) (do (.setBrakesOil dashboard true) [trip-km abs-km diesel-buffer temp-buffer])
      (= hig-beam-off cmd) (do (.setHighBeams dashboard false) [trip-km abs-km diesel-buffer temp-buffer])
      (= high-beam-on cmd) (do (.setHighBeams dashboard true) [trip-km abs-km diesel-buffer temp-buffer])
      (= oil-pressure-off cmd) (do (.setOilPressure dashboard false) [trip-km abs-km diesel-buffer temp-buffer])
      (= oil-pressure-on cmd) (do (.setOilPressure dashboard true) [trip-km abs-km diesel-buffer temp-buffer])
      (= parking-brake-off cmd) (do (.setParking dashboard false) [trip-km abs-km diesel-buffer temp-buffer])
      (= parking-brake-on cmd) (do (.setParking dashboard true) [trip-km abs-km diesel-buffer temp-buffer])
      (= turning-signs-off cmd) (do (.setTurnSigns dashboard false) [trip-km abs-km diesel-buffer temp-buffer])
      (= turning-signs-on cmd) (do (.setTurnSigns dashboard true) [trip-km abs-km diesel-buffer temp-buffer])
      (= spark-plugs-off cmd) (do (.setSparkPlug dashboard false) [trip-km abs-km diesel-buffer temp-buffer])
      (= spark-plugs-on cmd) (do (.setSparkPlug dashboard true) [trip-km abs-km diesel-buffer temp-buffer])
      (= reset-trip-km cmd) (do (.resetDistance dashboard) [0 abs-km diesel-buffer temp-buffer])
      (= speed-pulse cmd) (let [[trip abs] (speed-distance-interpreter dashboard val trip-km abs-km)]
                            [trip abs diesel-buffer temp-buffer])
      (= rpm-pulse cmd) (do (.setRpm dashboard (/ (* val 900) 155)) [trip-km abs-km diesel-buffer temp-buffer])
      (= diesel-value cmd) (do
                             (if (>= (count diesel-buffer) diesel-buffer-size )
                               (do
                                 (let [fuel-lvl (fuel-value-interpreter (avg diesel-buffer))]
                                   (if (< fuel-lvl 0)
                                     (.setDiesel dashboard 0)
                                     (.setDiesel dashboard fuel-lvl))
                                   [trip-km abs-km [] temp-buffer]))
                               [trip-km abs-km (conj diesel-buffer val) temp-buffer]))
      (= temperature-value cmd) (do
                                  (if (>= (count temp-buffer) temperature-buffer-size )
                                    (do
                                      (.setTemp dashboard (avg temp-buffer))
                                      [trip-km abs-km diesel-buffer []])
                                    [trip-km abs-km diesel-buffer (conj temp-buffer val)])
                                  [trip-km abs-km diesel-buffer temp-buffer]))))

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
                temp-buffer []]
        (let [record (<!! cpu-channel)
              [trip-km abs-km d-buffer t-buffer] (interpret-command dashboard
                                                                    record
                                                                    trip
                                                                    absolute-km
                                                                    diesel-buffer
                                                                    temp-buffer)]
          (recur trip-km abs-km d-buffer t-buffer))))
    (prn "Could not get car settings, is there somethign wrong with the connection?")))
