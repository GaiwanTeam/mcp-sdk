(ns co.gaiwan.mcp.protocol
  (:require
   [co.gaiwan.mcp.json-rpc :as jsonrpc]
   [lambdaisland.log4j2 :as log]))

(defn empty-response [request]
  (let [{:keys [id state session-id connection-id]} request]
    (if-let [{:keys [emit close]} (get-in @state [:sessions session-id :connections connection-id])]
      (close)
      (log/warn :empty-res/failed {:session-id session-id :connection-id connection-id}))))

(defonce req-id-counter (atom 0))

;; TODO: test this, and handle response
(defn request [{:keys [state session-id method params callback]}]
  (let [id (swap! req-id-counter inc)]
    (swap! state assoc-in [:response-handlers id] callback)
    (when-let [{:keys [emit]} (get-in @state [:sessions session-id :connections :default])]
      (emit {:data (jsonrpc/request id method params)}))))

(defn notify [{:keys [state session-id method params]}]
  (when-let [{:keys [emit]} (get-in @state [:sessions session-id :connections :default])]
    (emit {:data (if params
                   (jsonrpc/notification method params)
                   (jsonrpc/notification method))})))

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
                :capabilities capabilities
                :clientInfo clientInfo)
    (let [{:keys [protocol-version capabilities server-info instructions]} @state]
      (reply
       req
       (jsonrpc/response
        id
        {:protocolVersion protocol-version
         :capabilities    capabilities
         :serverInfo      server-info
         :instructions    instructions})))))

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
  (reply req
         (jsonrpc/response
          id
          {:tools
           (into []
                 (map #(assoc (dissoc (val %) :tool-fn) :name (key %)))
                 (get @state :tools))})))

(defmethod handle-request "tools/call" [{:keys [id state session-id params] :as req}]
  (let [{:keys [name arguments]} params
        {:keys [tool-fn]} (get-in @state [:tools name])]
    (if tool-fn
      (reply req (jsonrpc/response id (tool-fn arguments)))
      (reply req (jsonrpc/error id {:code jsonrpc/invalid-params :message "Tool not found"}))))  )

(defmethod handle-request "prompts/list" [{:keys [id state session-id params] :as req}]
  (reply
   req
   (jsonrpc/response
    id
    {:prompts
     (into []
           (map #(assoc (dissoc (val %) :messages-fn) :name (key %)))
           (get @state :prompts))})))

(defmethod handle-request "prompts/get" [{:keys [id state session-id params] :as req}]
  (let [{:keys [name arguments]} params
        {:keys [description messages-fn]} (get-in @state [:prompts name])]
    (if messages-fn
      (reply req (jsonrpc/response id {:description description
                                       :messages (messages-fn arguments)}))
      (reply req (jsonrpc/error id {:code jsonrpc/invalid-params :message "Prompt not found"})))))

(defmethod handle-request "resources/list" [{:keys [id state session-id params] :as req}]
  (let [resources (into []
                        (map #(assoc (dissoc (val %) :load-fn) :uri (key %)))
                        (get @state :resources))]
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
              {:contents [(assoc res :uri uri :text ((:load-fn res)))]}))
      (reply req (jsonrpc/error id {:code jsonrpc/invalid-params :message "Resource not found"})))))
