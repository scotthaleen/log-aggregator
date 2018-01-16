(ns scotthaleen.log.aggregator.server.system
  (:require
   [com.stuartsierra.component :as component]
   [scotthaleen.log.aggregator.server.store :refer [construct-store]]
   [scotthaleen.log.aggregator.server.routes :refer [construct-routes]]
   [scotthaleen.log.aggregator.server.pedestal :refer [construct-server]]))

(set! *warn-on-reflection* true)

(def base-config
  "
  :allowed-origins with a function of to always return true. This allows all origins for cors.

  see cors - https://github.com/pedestal/pedestal/blob/master/service/src/io/pedestal/http/cors.clj#L60
  "
  {:env :dev
   :router :linear-search
   :allowed-origins (constantly true)
   :type :jetty
   :port 3000})

(defn new-system [config]
  (let [config (merge base-config config)]
    (-> (component/system-map
         :config config
         :store (construct-store)
         :routes (construct-routes)
         :http   (construct-server config))
        (component/system-using
         {:routes {:store :store}
          :http   {:routes :routes} }))))
