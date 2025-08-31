(ns co.gaiwan.pure-clojure-mcp.mcp
  (:require
   [co.gaiwan.pure-clojure-mcp.json-rpc :as jsonrpc]
   [lambdaisland.log4j2 :as log]))

(def protocol-version "2025-06-18")

(defn empty-response [request]
  (let [{:keys [id state session-id connection-id]} request]
    (if-let [{:keys [emit close]} (get-in @state [:sessions session-id :connections connection-id])]
      (do
        (log/info :CLOSING (str close))
        (close))
      (log/warn :empty-res/failed {:session-id session-id :connection-id connection-id}))))

(defn reply [request response]
  (let [{:keys [id state session-id connection-id]} request]
    (if-let [{:keys [emit close]} (get-in @state [:sessions session-id :connections connection-id])]
      (do
        (emit {:data response :id id})
        (close))
      (log/warn :jsonrpc/reply-failed {:request request :response response} :message "Missing connection"))))

(defn swap-sess! [req f & args]
  (if-let [sess (:session-id req)]
    (apply swap! (:state req) update-in [:sessions sess] f args)
    (log/warn :session-update/failed {:req req} :message "Missing session-id")))

(defmulti handle-request (fn [request] (:method request)))
(defmulti handle-notification (fn [request] (:method request)))

(defmethod handle-request "initialize" [{:keys [id state session-id params] :as req}]
  (let [{:keys [procolversion capabilities clientInfo]} params]
    (swap-sess! req assoc
                :procolversion procolversion
                :capabilities  capabilities
                :clientInfo clientInfo)
    (reply
     req
     (jsonrpc/response
      id
      {:protocolVersion protocol-version
       :capabilities
       {:logging {}
        :prompts {:listChanged true}
        :resources {:subscribe true
                    :listChanged true}
        :tools
        {:listChanged true}}
       :serverInfo
       {:name "Pure"
        :title "Pure Clojure MPC server"
        :version "1.0.0"}
       :instructions "Be nice"}))))

(defmethod handle-request "logging/setLevel" [{:keys [id state session-id params] :as req}]
  (reply req
         (jsonrpc/response
          id
          {}))
  (swap-sess! req assoc :logging params))

(defmethod handle-notification "notifications/cancelled" [req]
  ;; "params"
  ;; {"requestId" "123",
  ;;  "reason" "User requested cancellation"}
  )

(defmethod handle-request "tools/list" [{:keys [id state session-id params] :as req}]
  (reply req (jsonrpc/response id {:tools []})))

(defmethod handle-request "prompts/list" [{:keys [id state session-id params] :as req}]
  (reply req (jsonrpc/response id {:prompts []})))

(defmethod handle-request "resources/list" [{:keys [id state session-id params] :as req}]
  (let [resources  (into []
                         (map #(assoc (val %) :uri (key %)))
                         (get @state :resources))]
    (log/info :resources/listing resources)
    (reply req
           (jsonrpc/response
            id
            {:resources resources}))))

(defmethod handle-request "resources/templates/list" [{:keys [id state session-id params] :as req}]
  (reply req (jsonrpc/response id {:resourceTemplates []})))

(defmethod handle-request "resources/read" [{:keys [id state session-id params] :as req}]
  (let [uri (:uri params)]
    (when-let [res (get-in @state [:resources uri])]
      (reply req
             (jsonrpc/response
              id
              {:contents [(assoc res :uri uri :text (slurp uri))]})))))
