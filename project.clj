(require '[clojure.string :as s])


(def VERSION "0.1.0-SNAPSHOT")

(defproject log-aggregator VERSION
  :description ""
  :url ""
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.474"]
                 [clj-http "3.7.0"]
                 [cheshire "5.8.0"]

                 [org.clojure/tools.logging "0.4.0"]

                 [com.stuartsierra/component "0.3.2"]
                 [org.clojure/tools.cli "0.3.5"]

                 ;; server
                 [io.pedestal/pedestal.service "0.5.1"]
                 [io.pedestal/pedestal.jetty "0.5.1"]

                 ;;Logging
                 [ch.qos.logback/logback-classic "1.1.11" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.22"]
                 [org.slf4j/jcl-over-slf4j "1.7.22"]
                 [org.slf4j/log4j-over-slf4j "1.7.22"]]
  :resource-paths ["resources"]
  :target-path "target/%s"
  :profiles {
             :dev [:project/dev]
             :uberjar {:aot :all
                       :uberjar-name "log-aggregator-standalone.jar"}
             :lint [:project/lint :aot/test]
             :forwarder {:main scotthaleen.log.aggregator.log-forwarder}
             :server {:main scotthaleen.log.aggregator.app}
             :project/lint {:plugins [[jonase/eastwood "0.2.5"]]}
             :project/dev {
                           :dependencies [[reloaded.repl "0.2.3"]
                                          [org.clojure/tools.namespace "0.2.11"]

                                          [org.clojure/tools.nrepl "0.2.12"]
                                          [eftest "0.3.1"]
                                          [org.clojure/test.check "0.10.0-alpha2"]]
                           :source-paths ["dev"]
                           :jvm-opts ["-Dclojure.spec.check-asserts=true"]
                           :repl-options {:init-ns user}}}
  :aliases {"forwarder" ["with-profile" "forwarder" "run"]
            "server"  ["with-profile" "server" "run"]})
