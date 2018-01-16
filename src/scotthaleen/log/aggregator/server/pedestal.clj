(ns scotthaleen.log.aggregator.server.pedestal
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]
            [io.pedestal.http :as pedestal]))

(set! *warn-on-reflection* true)

(defn pedestal-config
  "
  adds io.pedestal.http prefix to all :http config options
  (pedestal-config {:port 80})
  ;;=> {:io.pedestal.http/port 80}
  "
  [config]
  (into {}
        (for [[k v] config]
          [(keyword "io.pedestal.http" (name k)) v])))

(defrecord PedestalServer [routes]
  component/Lifecycle
  (start [component]
    (log/info "Starting Server")
    (if (:server component)
      component
      (let [options (assoc (dissoc component :routes) :join? false)
            pedestal-cfg (->
                          options
                          (assoc :routes (:routes routes))
                          pedestal-config)
            server (-> pedestal-cfg
                       pedestal/create-server
                       pedestal/start)]
        (assoc component :server server))))
  (stop [component]
    (if-let [server (:server component)]
      (do
        (println "Stoping Server")
        (pedestal/stop server)
        (dissoc component :server))
      component)))

(defn construct-server
  "  Constructs a Pedestal Server with Map of configuration options
     http://pedestal.github.io/pedestal/io.pedestal.http.html#var-default-interceptors
  "
  [options]
  (map->PedestalServer options))
