(ns co.gaiwan.mcp.lib.ring-sse
  "Generic SSE middleware that hands control to the handler"
  (:require
   [clojure.string :as str]
   [ring.util.io :as ring-io]
   [ring.util.response :as res])
  (:import
   (java.io OutputStream OutputStreamWriter Writer)))

(set! *warn-on-reflection* true)

(defn write-sse-message [^Writer writer {:keys [event data id comment]}]
  (when comment
    (doseq [c (str/split comment #"\R")]
      (.write writer (str ": " c "\n"))))
  (when event
    (.write writer (str "event: " event "\n")))
  (when id
    (.write writer (str "id: " id "\n")))
  (when data
    (doseq [d (str/split data #"\R")]
      (.write writer (str "data: " d "\n"))))
  (.write writer "\n"))

(defn accepts-sse? [{:keys [headers]}]
  (str/includes? (get headers "accept") "text/event-stream"))

(defn upgrade-response [res]
  (let [res (cond-> res
              (not (res/find-header res "content-type"))
              (assoc-in [:headers "content-type"] "text/event-stream"))]
    (assoc res
           :body
           (ring-io/piped-input-stream
            (fn [^OutputStream out]
              (let [wait (promise)
                    writer (OutputStreamWriter. out "UTF-8")
                    emit (fn emit [m]
                           (write-sse-message writer m)
                           (.flush writer))
                    close (fn close [m]
                            (deliver wait :ok)
                            (.close writer))]
                ((:sse/handler res) emit close)
                @wait))))))

(defn wrap-sse
  "Generic SSE middleware. When a client makes a request with `text/event-stream`,
  the ring request map passed to the handler will contain `:sse true`, to signal
  that the handler could start an event stream.

  If the handler wants to do so, it returns a `:sse/handler (fn [emit close])`,
  instead of a `:body`. The signature of `emit` is `[{:keys [event data id
  comment]}]`. The connection will be kept open until `close` is called.

  Tested with Jetty, should work with other adapters assuming they correctly
  handle `ring.util.io/piped-input-stream`. Make sure the adapter honors
  `.flush` on the outputstream, for jetty, start it with `:output-buffer-size
  1`."
  [handler]
  (fn [req]
    (let [sse? (accepts-sse? req)
          res  (handler (cond-> req sse? (assoc :sse sse?)))]
      (if (and sse? (contains? res :sse/handler))
        (upgrade-response res)
        res))))
