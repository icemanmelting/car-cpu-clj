(ns car-cpu-clj.core
  (:gen-class
    :name pt.iceman.CarCPU
    :state state
    :init init
    :methods [#^{:static true} [startCPU [pt.iceman.carscreentools.Dashboard java.util.UUID] void]
              #^{:static true} [resetDashTripKm [] boolean]])
  (:require [clojure.core.async :as a :refer [<! <!! chan go-loop >!! go >!]]
            [car-cpu-clj.com-protocol-interpreter :refer [reset-trip-km ignition-state]]
            [car-cpu-clj.speed-rpm-reader :as speed]
            [car-data-clj.core :as data])
  (import (java.net DatagramSocket
                    DatagramPacket
                    InetSocketAddress)
          (pt.iceman.carscreentools Dashboard)
          (java.util UUID)))

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

(defn- interpret-command [dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id]
  (ignition-state dashboard cmd-map abs-km diesel-buffer temp-buffer settings-id))

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
      (reset! speed/trip-length trip-km)
      (receive-loop (make-socket 9887) dashboard)
      (go-loop [absolute-km cnst-km
                diesel-buffer []
                temp-buffer []
                settings-id id]
        (let [record (<!! cpu-channel)
              [abs-km d-buffer t-buffer settings-id] (interpret-command dashboard record absolute-km diesel-buffer temp-buffer settings-id)]
          (recur abs-km d-buffer t-buffer settings-id))))
    (prn "Could not get car settings, is there something wrong with the connection?")))
