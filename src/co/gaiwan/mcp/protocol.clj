(ns co.gaiwan.mcp.protocol
  (:require
   [co.gaiwan.mcp.json-rpc :as jsonrpc]
   [lambdaisland.log4j2 :as log])
  (:import
   java.util.concurrent.LinkedBlockingQueue))

(defmulti handle-request (fn [request] (:method request)))
(defmulti handle-response (fn [request] (:method request)))
(defmulti handle-notification (fn [request] (:method request)))

(defn default-conn [state session-id]
  (get-in state [:sessions session-id :connections :default]))

(defn find-conn [state session-id connection-id]
  (or (when (and connection-id (not= :default connection-id))
        (get-in state [:sessions session-id :connections connection-id]))
      (default-conn state session-id)))

(defn empty-response [request]
  (let [{:keys [id state session-id connection-id]} request
        state @state]
    (if-let [{:keys [emit close]} (find-conn state session-id connection-id)]
      (close)
      (log/warn :empty-res/failed {:session-id session-id :connection-id connection-id}))))

(defonce req-id-counter (atom 0))

(defn request [{:keys [state session-id connection-id method params callback] :as req}]
  (let [id (swap! req-id-counter inc)]
    (log/debug :mcp/request {:method method :session-id session-id :id id :connection-id connection-id})
    (if-let [{:keys [emit]} (find-conn @state session-id connection-id)]
      (do
        (swap! state assoc-in [:requests id] req)
        (emit {:data (jsonrpc/request id method params)}))
      (log/warn :request/failed {:method method :session-id session-id :id id}
                :message "no default conn found"))))

(defn notify [{:keys [state session-id method params]}]
  (when-let [{:keys [emit]} (default-conn @state session-id)]
    (emit {:data (if params
                   (jsonrpc/notification method params)
                   (jsonrpc/notification method))})))

(defn reply [request response]
  (let [{:keys [id state session-id connection-id]} request]
    (if-let [{:keys [emit close]} (find-conn @state session-id connection-id)]
      (do
        (emit {:data response})
        (close))
      (log/warn :jsonrpc/reply-failed {:request request :response response} :message "Missing connection"))))

(defn swap-sess! [req f & args]
  (if-let [sess (:session-id req)]
    (apply swap! (:state req) update-in [:sessions sess] f args)
    (log/warn :session-update/failed {:req req} :message "Missing session-id")))

(defmethod handle-request "initialize" [{:keys [id state session-id params] :as req}]
  (let [{:keys [procolversion capabilities clientInfo]} params
        queue (LinkedBlockingQueue. 1024)]
    (swap-sess! req
                (fn [sess]
                  (-> sess
                      (update :connections
                              assoc :default
                              {:emit #(.put queue %)
                               :close (fn [])
                               :queue queue})
                      (assoc :procolversion procolversion
                             :capabilities capabilities
                             :clientInfo clientInfo)))
                )
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

(defmethod handle-notification "notifications/initialized" [{:keys [state session-id]}]
  (let [capabilities (get-in @state [:sessions session-id :capabilities])]
    (log/debug :notifications/initialized {:session-id session-id
                                           :capabilities capabilities})
    (when (contains? capabilities :roots)
      (request {:state state
                :session-id session-id
                :method "roots/list"})))
  ;; "params"
  ;; {"requestId" "123",
  ;;  "reason" "User requested cancellation"}
  )

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
  (log/debug :resources/list req)
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

(defmethod handle-response "roots/list" [{:keys [state session-id result] :as req}]
  (swap! state assoc-in [:sessions session-id :roots] (:roots result))
  (empty-response req))
