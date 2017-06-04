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

;;IMPLEMENT THIS FOR TEMPERATURE CREATION
;protected static final float CAR_TERMISTOR_ALPHA_VALUE = -0.00001423854206f;
;protected static final float CAR_TERMISTOR_BETA_VALUE = 0.0007620444171f;
;protected static final float CAR_TERMISTOR_C_VALUE = -0.000006511973919f;
;public static final byte TEMPERATURE_VALUE = (byte) 0b1100_0000;
;public static final int TEMPERATURE_BUFFER_SIZE = 256;
;static final int PULL_UP_RESISTOR_VALUE = 975;
;static final double VOLTAGE_LEVEL = 12;
;static final int PIN_RESOLUTION = 1023;
;static final double STEP = (double) 15 / (double) PIN_RESOLUTION;
;
;double voltageLevel = analogLevel * STEP;
;double resistance = (voltageLevel * PULL_UP_RESISTOR_VALUE) / (VOLTAGE_LEVEL - voltageLevel);
;double temperature = 1 / (CAR_TERMISTOR_ALPHA_VALUE + CAR_TERMISTOR_BETA_VALUE * (Math.log(resistance)) + CAR_TERMISTOR_C_VALUE * Math.log(resistance) * Math.log(resistance) * Math.log(resistance)) - 273.15;


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
  (let [distance (calculate-distance speed)
        trip (+ trip-km distance)
        abs (+ abs-km distance)]
    (doto dashboard
      (.setDistance trip)
      (.setTotalDistance abs))
    [trip abs (conj speed)]))

(defn avg [ar] (/ (reduce + ar) (count ar)))

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
      (= spark-plugs-on cmd) (do (.setSparkPlug dashboard false) [trip-km abs-km diesel-buffer temp-buffer])
      (= reset-trip-km cmd) (do (.resetDistance dashboard) [0 abs-km diesel-buffer temp-buffer])
      (= speed-pulse cmd) (let [[trip abs] (speed-distance-interpreter dashboard val trip-km abs-km)]
                            (.setSpeed dashboard val)
                            [trip abs diesel-buffer temp-buffer])
      (= rpm-pulse cmd) (do (.setRpm dashboard (/ (* val 900) 155)) [trip-km abs-km diesel-buffer temp-buffer])
      (= diesel-value cmd) (do (if (= diesel-buffer-size (count diesel-buffer))
                                 (do
                                   (.setDiesel dashboard (avg diesel-buffer))
                                   [trip-km abs-km diesel-buffer [] temp-buffer])
                                 [trip-km abs-km (conj diesel-buffer val) diesel-buffer temp-buffer]))
      (= temperature-value cmd) (do [trip-km abs-km diesel-buffer diesel-buffer temp-buffer])))) ;;<--- implement this

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
      (receive-loop (make-socket 9999) dashboard)
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
          (prn)
          (recur trip-km abs-km d-buffer t-buffer))))
    (prn "Could not get car settings, is there somethign wrong with the connection?")))
