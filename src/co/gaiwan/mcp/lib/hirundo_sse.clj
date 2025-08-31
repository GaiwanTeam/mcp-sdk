(ns co.gaiwan.mcp.lib.hirundo-sse
  (:require
   [clojure.string :as str]
   [s-exp.hirundo.http.request :as request]
   [s-exp.hirundo.http.response :as response]
   [s-exp.hirundo.options :as hirundo-options]
   [ring.util.response :as res]
   [lambdaisland.log4j2 :as log])
  (:import
   (io.helidon.http Status)
   (io.helidon.http.sse SseEvent)
   (io.helidon.webserver WebServerConfig$Builder)
   (io.helidon.webserver.http Handler HttpRouting ServerResponse)
   (java.io OutputStreamWriter)
   java.io.OutputStream))

(set! *warn-on-reflection* true)

(defn set-status!
  [^ServerResponse server-response status]
  (when status
    (.status server-response (Status/create status))))

(defn map->SseEvent [{:keys [event data id comment]}]
  (.build
   (cond-> (SseEvent/builder)
     event   (.name (str event))
     data    (.data (.getBytes (str data)))
     id      (.id (str id))
     comment (.comment (str comment)))))

#_(defn upgrade-request [server-response res]
    (let [sink (.sink server-response SseSink/TYPE)
          {:keys [status headers]} res]
      (response/set-headers! server-response (:headers res))
      (set-status! server-response status)
      ((:sse/start-stream res)
       {:emit  #(.emit sink (map->SseEvent %))
        :close #(.close sink)})))

(defn upgrade-request [^ServerResponse server-response res]
  (let [{:keys [status headers]} res
        headers (cond-> headers
                  (not (res/find-header res "content-type"))
                  (assoc "content-type" "text/event-stream"))]
    (response/set-headers! server-response headers)
    (set-status! server-response status)
    (let [stream (.outputStream server-response)
          writer (OutputStreamWriter. stream)]
      ((:sse/start-stream res)
       {:emit
        (fn [{:keys [event data id comment]}]
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
        :close (fn []
                 (.close writer)
                 (.close stream))}))))

(defn helidon-handler [handler]
  (reify Handler
    (handle [_ server-request server-response]
      (let [req  (request/ring-request server-request server-response)
            sse? (str/includes? (get-in req [:headers "accept"]) "text/event-stream")
            res  (handler (assoc req :sse sse?))]
        (if (and sse? (contains? res :sse/start-stream))
          (upgrade-request server-response res)
          (response/set-response! server-response res))))))

(defn set-ring-handler! ^WebServerConfig$Builder
  [^WebServerConfig$Builder builder handler _options]
  (doto builder
    (.addRouting
     (doto (HttpRouting/builder)
       (.any
        ^io.helidon.webserver.http.Handler/1
        (into-array
         Handler
         [(helidon-handler handler)]))))))

(defmethod hirundo-options/set-server-option! :http-handler/sse
  [^WebServerConfig$Builder builder _ handler options]
  (set-ring-handler! builder handler options))
