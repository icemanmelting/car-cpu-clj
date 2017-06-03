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
(def abs-anomaly-on -113) ;143
(def battery-off 32)
(def battery-off 47)
(def brakes-oil-off 64)
(def brakes-oil-on 79)
(def hig-beam-off 144)
(def high-beam-on 159)
(def oild-pressure-off 16)
(def oild-pressure-on 31)
(def parking-brake-off 48)
(def parking-brake-on 63)
(def reset-trip-km 255)
(def rpm-pulse 180)
(def spark-plugs-off 112)
(def spark-plugs-on 127)
(def speed-pulse 176)
(def temperature-value 192)
(def turning-signs-off 80)
(def turning-signs-on 95)

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
        length (min (alength payload) 512)
        address (InetSocketAddress. host port)
        packet (DatagramPacket. payload length address)]
    (.send socket packet)))

(defn receive-packet
  "Block until a UDP message is received on the given DatagramSocket, and
  return the payload message as a string."
  [^DatagramSocket socket]
  (let [buffer (byte-array 10)
        packet (DatagramPacket. buffer 10)]
    (.receive socket packet)
    (.getData packet)))

(defn bytes-to-int
  ([bytes]
   (bit-or (bit-and (first bytes)
                    0xFF)
           (bit-and (bit-shift-left (second bytes) 8)
                    0xFF00))))

(defn- process-command [cmd-ar]
  (let [ar (filter #(not (= % 0)) cmd-ar)
        ar-size (count ar)]
    (if (= 1 ar-size)
      {:command (first ar)}
      {:command (first ar)
       :value (bytes-to-int (rest ar))})))

(defn- interpret-command [dashboard cmd-map trip-km abs-km speed-buffer temp-buffer]
  (let [cmd (:command cmd-map)]
    (println cmd)
    (.setAbs dashboard true)
    ;(case cmd
    ;  abs-anomaly-off (.setAbs dashboard false)
    ;  abs-anomaly-on (.setAbs dashboard true))
    ))

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
  ;(if-let [car-settings (data/read-settings id)]
  ;  (let [trip-km (:trip_kilometers car-settings)
  ;        cnst-km (:constant_kilometers car-settings)]
  (receive-loop (make-socket 9999) dashboard)
  (go-loop [trip 0
            absolute-km 0
            ;trip trip-km
            ;absolute-km cnst-km
            speed-buffer []
            temp-buffer []]
    (let [[] (interpret-command dashboard
                                (<!! cpu-channel)
                                trip
                                absolute-km
                                speed-buffer
                                temp-buffer)]
      (recur trip absolute-km speed-buffer temp-buffer))))
;))
;make the interpret command return a vector
; of updated values to use in recur


