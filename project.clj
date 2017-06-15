(defproject car-cpu-clj "1.0.5"
  :description "Clojure library to handle communication between the Car CPU and the dashboard"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.3.442"]
                 [car-data-clj "1.0.1"]
                 [overtone/at-at "1.2.0"]
                 [pt.iceman/carscreentools "0.0.1"]
                 [car-ai-clj "1.0.0"]]
  :aot [car-cpu-clj.core]
  :java-source-paths ["src/screentools"])

