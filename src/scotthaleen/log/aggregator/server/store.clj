(ns scotthaleen.log.aggregator.server.store
  (:require [com.stuartsierra.component :as component]
            [clojure.string :as s]))


(defprotocol IStore
  (<get [this host file] "return the data stored for a host and file")
  (put> [this host file line-num content] "add a line entry for a file from a host"))


(defn- add-content
  "
  Update map with nested key value, overwrites duplicate entries

  {host:file {1 \"content\"}
  "
  [m host file line-num content]
  (let [composite-key (str host ":" file)]
    (update-in
     m
     [composite-key]
     (fnil
      (fn [data-map]
        (assoc data-map line-num content))
      {line-num content}))))


(defrecord Store []
  component/Lifecycle
  (start [component]
    (assoc component :data (atom {})))
  (stop [component]
    (dissoc component :data))
  IStore
  (<get [this host file] (get @(:data this) (str host ":" file)))
  (put> [this host file line-num content] (swap!
                                           (:data this)
                                           add-content
                                           host file line-num content)))

(defn construct-store []
  (map->Store {}))
