(ns co.gaiwan.pure-clojure-mcp.json-rpc)

(def parse-error -32700)      ;; Parse error	 	Invalid JSON was received by the server.
(def invalid-request -32600)  ;; Invalid Request 	The JSON sent is not a valid Request object.
(def method-not-found -32601) ;; Method not found 	The method does not exist / is not available.
(def invalid-params -32602)   ;; Invalid params 	Invalid method parameter(s).
(def internal-error -32603)   ;; Internal error 	Internal JSON-RPC error.

(defn request [id method params]
  {:jsonrpc "2.0"
   :id id
   :method method
   :params params})

(defn notification
  ([method]
   {:jsonrpc "2.0"
    :method method})
  ([method params]
   {:jsonrpc "2.0"
    :method method
    :params params}))

(defn response [id result]
  {:jsonrpc "2.0"
   :id id
   :result result})

(defn error [id {:keys [message code data]}]
  {:jsonrpc "2.0"
   :id id
   :error (cond-> {:code code}
            message (assoc :message message)
            data (assoc :data data))})
