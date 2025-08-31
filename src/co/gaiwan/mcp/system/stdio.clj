(ns co.gaiwan.mcp.system.stdio
  (:require
   [charred.api :as charred]
   [co.gaiwan.mcp.protocol :as mcp]
   [co.gaiwan.mcp.state :as state]
   [lambdaisland.log4j2 :as log]))

(defn component [_]
  (swap! state/state assoc-in [:sessions :stdio :connections :default]
         {:emit #(do
                   (log/debug :emit %)
                   (println
                    (charred/write-json-str (:data %) :close-writer? false))
                   )
          :close (fn [])})
  (log/info :stdio/starting {})
  (future
    (with-open [json-fn (charred/read-json-supplier *in* {:key-fn keyword :close-reader? false :async? false :bufsize 8192})]
      (try
        (let [read-next #(.get json-fn)]
          (loop [rpc-req (read-next)]
            (log/debug :jsonrpc rpc-req)
            (try
              (if (:id rpc-req)
                (mcp/handle-request (assoc rpc-req :state state/state :session-id :stdio :connection-id :default))
                (mcp/handle-notification (assoc rpc-req :state state/state :session-id :default)))
              (catch Throwable e
                (log/error :handler/failed {} :exception e)))
            (recur (read-next))))
        (catch Throwable e
          (log/error :loop/broke {} :exception e))))))
