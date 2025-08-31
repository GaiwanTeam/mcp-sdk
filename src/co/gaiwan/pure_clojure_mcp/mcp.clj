(ns co.gaiwan.pure-clojure-mcp.mcp
  (:require
   [co.gaiwan.pure-clojure-mcp.json-rpc :as jsonrpc]
   [lambdaisland.log4j2 :as log]))

(def protocol-version "2025-06-18")

(defn empty-response [request]
  (let [{:keys [id state session-id connection-id]} request]
    (if-let [{:keys [emit close]} (get-in @state [:sessions session-id :connections connection-id])]
      (close)
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
  (reply req
         (jsonrpc/response
          id
          {:prompts
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
