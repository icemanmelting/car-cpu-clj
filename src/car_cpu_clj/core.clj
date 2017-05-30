(ns car-cpu-clj.core
  (import (java.net DatagramSocket
                    DatagramPacket
                    InetSocketAddress)))

(defn make-socket
  ([] (new DatagramSocket))
  ([port] (new DatagramSocket port)))

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
  (let [buffer (byte-array 512)
        packet (DatagramPacket. buffer 512)]
    (.receive socket packet)
    (String. (.getData packet)
             0 (.getLength packet))))

(defn receive-loop
  "Given a function and DatagramSocket, will (in another thread) wait
  for the socket to receive a message, and whenever it does, will call
  the provided function on the incoming message."
  [socket f]
  (future (loop []
            (f (receive-packet socket))
            (recur))))

(defn print-this-shit [msg] (println "message: " msg))


(defn -main []
  (receive-loop (make-socket 9999) print-this-shit))