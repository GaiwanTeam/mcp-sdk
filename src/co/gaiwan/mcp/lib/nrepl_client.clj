(ns co.gaiwan.mcp.lib.nrepl-client
  "nREPL client as a library, providing a callback or promise based API"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [lambdaisland.log4j2 :as log]
   [nrepl.transport :as transport])
  (:import
   (java.net Socket)))

(defn nrepl-port [loc]
  (let [path (cond
               (str/starts-with? loc "file:///")
               (subs loc 7)
               (str/starts-with? loc "file://")
               (subs loc 6)
               :else
               loc)
        f (io/file path ".nrepl-port")]
    (when (.exists f)
      (parse-long (slurp f)))))

(defn connect [{:keys [host port]}]
  (let [sock (Socket. host port)
        in   (io/input-stream (.getInputStream sock))
        out  (io/output-stream (.getOutputStream sock))
        state (atom {})
        trans (transport/bencode in out sock)]
    (.start
     (Thread/ofVirtual)
     (fn []
       (while (not (.isClosed sock))
         (when-let [msg (transport/recv trans)]
           (when-let [cb (get-in @state [:callbacks (:id msg)])]
             (when (some #{"done" "error"} (:status msg))
               (swap! state update :callbacks dissoc (:id msg)))
             (try
               (cb msg)
               (catch Throwable t
                 (log/error :nrepl-callback/failed msg :exception t))))))))
    {:state state
     :socket sock
     :transport trans}))

(defonce msg-id-cnt (atom 0))

(defn send-msg
  ([conn msg]
   (let [result (promise)
         responses (volatile! [])]
     (send-msg conn msg (fn [msg]
                          (vswap! responses conj msg)
                          (when (some #{"done" "error"} (:status msg))
                            (deliver result @responses))))
     result))
  ([conn msg cb]
   (let [id (swap! msg-id-cnt inc)
         sid (:session-id conn)
         msg (cond-> (assoc msg :id id)
               sid (assoc :session sid))]
     (swap! (:state conn) assoc-in [:callbacks id] cb)
     (transport/send (:transport conn) msg)
     conn)))

(defn new-session [conn]
  (let [sess-id (promise)]
    (send-msg conn {:op "clone"} (fn [{:keys [new-session]}]
                                   (deliver sess-id new-session)))
    (assoc conn :session-id @sess-id)))



(comment
  (def t (connect {:host "localhost"
                   :port (nrepl-port "/home/arne/Gaiwan/mcp-sdk")}))
  (def s (new-session t))
  @(send-msg t {:op "eval" :code "(+ 1 1)"})
  (.close (:transport t))
  (.isClosed (:socket t))
  (transport/recv (:transport t))
  (send-msg s {:op "eval" :code "(println \"foo\") 123"}
            (fn [{:keys [out value]}]
              (when out
                (print out)
                (flush))
              (when value
                (println ";;=> " value)))))
