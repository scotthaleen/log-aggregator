(ns scotthaleen.log.aggregator.server.routes
  (:require
   [clojure.string :as sz]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]
   [io.pedestal.http.body-params :as body-params]
   [io.pedestal.http.content-negotiation :refer [negotiate-content]]
   [io.pedestal.http.ring-middlewares :as ring-mw]
   [io.pedestal.http :as bootstrap]
   [io.pedestal.interceptor :refer [interceptor]]
   [io.pedestal.interceptor.chain :refer [terminate]]
   [io.pedestal.http.route :as route]
   [ring.util.response :refer [response not-found content-type]]
   [scotthaleen.log.aggregator.server.store :as store]
   ))



(defn- context-terminate
  " Helper function to terminate a request "
  [ctx status reason]
  (-> ctx
      terminate
      (assoc :response
             {:status status
              :body reason})))


(def attach-guid
  " Example Interceptor Adds a GUID to Request Context"
  (interceptor
   {:name ::attach-guid
    :enter (fn [context]
             (let [guid (java.util.UUID/randomUUID)]
               (println "Request GUID: " guid)
               (assoc context ::guid guid)))}))

(def ruok
  " Simple Interceptor to check if service is up"
  (interceptor {:name ::ruok
                :enter (fn [context]
                         (assoc context :response
                                {:status 200
                                 :body "imok"
                                 :headers {"Content-Type" "text/plain"
                                           "X-GUID" (str (::guid context))}}))}))

(def get-log-store
  " Get a log from the Store"
  (interceptor
   {:name ::get-from-store
    :enter (fn [context]
             (assoc context :response
                    {:status 200
                     :body (sz/join \newline (vals (into (sorted-map) (-> context ::dbval))))
                     :headers {"Content-Type" "text/plain"
                               "X-GUID" (str (::guid context))}
                     }))}))

(def accept-batch
  " Add a batch of logs to the Store "
  (interceptor
   {:name ::accept-batch
    :enter (fn [context]
             (let [store (-> context :request :store)
                   body (-> context :request :json-params)]
               (log/info body)
               (if-not body
                 (-> context
                     terminate
                     (assoc :response (not-found (str " json object not posted " body))))
                 (doseq [{:keys [host file line-num content]} (:batch body)]
                   (log/info host file line-num content)
                   (store/put> store host file line-num content)
                   (assoc context :response
                          (response "ok"))
                   ))))}))

(def lookup-log
  " Pulls Log from Store and adds it to context.  Terminates if not found and throws 404"
  (interceptor
   {:name ::lookup-log
    :enter (fn [context]
             (let [host (-> context :request :query-params :host)
                   file (-> context :request :query-params :file)
                   v (store/<get (-> context :request :store) host file)]
               (if-not v
                 (-> context
                     terminate
                     (assoc :response (not-found (str "key not found " host " " file))))
                 (assoc context ::dbval v))))}))


(def dump-store
  " debugging route "
  (interceptor {:name ::dump-store
                :enter (fn [context]
                         (assoc context :response
                                {:status 200
                                 :body @(-> context :request :store :data)
                                 :headers {"Content-Type" "application/json"
                                           "X-GUID" (str (::guid context))}}))}))

(defn add-store
  " Adds a store to context"
  [store]
  (interceptor
   {:name  ::add-store
    :enter (fn [context]
             (assoc-in context [:request :store] store))}))

(defn build-routes
  [store]
  (route/expand-routes
   [[
     ["/" ^:interceptors [attach-guid
                          (body-params/body-params)]
      ["/ruok" {:get ruok}]
      ["/store" ^:interceptors [(add-store store)]
       ["/debug" {:get dump-store}]
       ["/batch" ^:interceptors [(negotiate-content ["application/json"])] {:post accept-batch}]
       ["/log" ^:interceptors [lookup-log] {:get get-log-store}]]
      ]]]))


(defrecord Routes [routes store]
  component/Lifecycle
  (start [component]
    (assoc component :routes (build-routes store)))
  (stop [component]
    (dissoc component :routes)
    component))


(defn construct-routes []
  (map->Routes {}))
