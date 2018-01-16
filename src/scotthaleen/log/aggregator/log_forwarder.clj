(ns scotthaleen.log.aggregator.log-forwarder
  (:require
   [clojure.core.async :as async :refer [chan go-loop <! >! >!! <!! close!]]
   [clj-http.client :as client]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log])
  (:import [java.io RandomAccessFile]
           [java.util UUID]
           [java.net InetAddress]))

(set! *warn-on-reflection* true)


(def BACKOFF-TIMEOUT 5000) ; milliseconds

(def BATCH-SIZE 2)

;; batches messages to BATCH-SIZE
(defonce content-chan (atom (chan 1 (comp (partition-all BATCH-SIZE)))))

(defn uuid [] (str (java.util.UUID/randomUUID)))
(def HOST (.getCanonicalHostName (InetAddress/getLocalHost)))

(defn- canonical-path
  [file-path]
  (.getCanonicalPath (io/file file-path)))

(defn- create-payload
  "helper to create the payload to send to the server"
  [file line-num content]
  {:uuid (uuid)
   :host HOST
   :file file
   :line-num line-num
   :content content})

(defn post-data!
  "send data to server, retry N times before failing"
  [url data & {:keys [retry-count] :or {retry-count 5}}]
  (try
    (log/debug "POSTING: " data)
    (client/post
     url
     {:body (json/generate-string data)
      :content-type :json
      :socket-timeout 1000
      :conn-timeout 1000
      :accept :json
      :retry-handler (fn [ex try-count http-context]
                       (log/debug "try again " try-count)
                       (Thread/sleep (* try-count BACKOFF-TIMEOUT))
                       (if (> try-count retry-count) false true))})
    true
    (catch Exception e
      (log/error e "failed to send data")
      false
      )))

(defn tail-file!
  "Start a tail on file

  reads a file line by line emitting

  {:file-path \"path to file being read\"
   :line-num \"line number of content\"
   :line \"content of line\"}

  on to an output channel, continues to read file for changes
  "
  [file-path out-ch]
  (let [raf #^RandomAccessFile (RandomAccessFile. file-path "r")]
    (go-loop [line-num 0]
      (log/info "reading line " line-num)
      (recur
       (if-let [line (.readLine raf)]
         (do
           (>! out-ch {:file-path file-path :line-num line-num :content line})
           (inc line-num))
         (do (Thread/sleep 1000)
             line-num))))))


(defn line-process!
  "Start a go block to process an input channel (the content of a file)

  recieves batch of {:file-path _ :line-num _ :content _} from input channel
  fills a batch of batch-size, when full sends to aggregator server-url
  "
  [in-ch server-url]
  (go-loop []
    (let [msg-batch (<! in-ch)]
      (log/debug "recieved: " msg-batch)
      (if (post-data!
           server-url
           {:batch
            (map
             #(apply
               create-payload
               ((juxt :file-path :line-num :content) %)) msg-batch)
            :size (count msg-batch)})
        (do
          (log/debug "failed to send data sleeping")
          ;; in case of failure back off
          (Thread/sleep BACKOFF-TIMEOUT))))
    (recur)))


(defn -main
  "Main entry point for forwarder"
  [& args]
  (if (not= 2 (count args))
    (do
      (println "Missing argument file-path")
      (println "Usage: java -cp log-aggregator.jar scotthaleen.log.aggregator.log-forwarder  <aggregator server url> <file-path>")
      (System/exit 1)))

  (let [file-path (second args)
        server-url (first args)
        file-process (atom nil)
        line-process (atom nil)
        batch-size 2]

    (log/info "started")
    ;; start go blocks
    (reset! line-process (line-process! @content-chan server-url))
    (reset! file-process (tail-file! (canonical-path file-path) @content-chan))
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread.
                       #(do
                          (log/info "shutdown")
                          (close! @file-process)
                          (close! @line-process)
                          (close! @content-chan))))
    (while true
      (<!! @file-process))))
