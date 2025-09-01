(ns co.gaiwan.mcp.http-api
  (:require
   [charred.api :as charred]
   [co.gaiwan.mcp.json-rpc :as json-rpc]
   [co.gaiwan.mcp.protocol :as mcp]
   [co.gaiwan.mcp.state :as state]
   [lambdaisland.log4j2 :as log]))

(defn- start-sse-stream [session-id conn-id]
  (fn [emit close]
    (log/debug :sse/start session-id)
    (let [emit (fn [response]
                 (log/debug :sse/emit (update response :data select-keys [:id :method #_:result]))
                 (emit
                  (merge {:event "message"} (update response :data charred/write-json-str))))
          close (fn []
                  (log/debug :sse/close conn-id)
                  (swap! state/state update-in [:sessions session-id :connections] dissoc conn-id)
                  (close))]
      (swap! state/state assoc-in [:sessions session-id :connections conn-id] {:emit emit :close close}))))

(defn POST
  {:parameters
   {:body [:map {:closed false}
           [:jsonrpc [:enum "2.0"]]
           [:method string?]
           [:id {:optional true} any?]
           [:params {:optional true} [:or
                                      [:map {:closed false}]
                                      [:vector any?]]]]}}
  [{:keys [parameters mcp-session-id] :as req}]
  (log/info :POST (-> req :parameters :body))
  (let [{:keys [method params result id] :as rpc-req} (:body parameters)]
    (cond
      (= "notifications/initialized" method)
      {:status 202}

      (and (not mcp-session-id) (not= "initialize" method))
      {:status 400
       :body {:result {:error "Missing Mcp-Session-Id header"}}}

      (and mcp-session-id (= "initialize" method))
      {:status 400
       :body {:result {:error "Re-initializing existing session"}}}

      (and mcp-session-id (not (get-in @state/state [:sessions mcp-session-id])))
      {:status 404
       :body {:result {:error (str "No session with Mcp-Session-Id "
                                   mcp-session-id " found")}}}

      (not id) ;; notification
      (do
        (mcp/handle-notification (assoc rpc-req :state state/state :session-id mcp-session-id))
        {:status 202})

      (and result id) ;; response
      (do
        (log/debug :response/reply {:id id :result result})
        (when-let [callback (get-in @state/state [:response-handlers id])]
          (let [session-id (or mcp-session-id (str (random-uuid)))
                conn-id (random-uuid)]
            (if (:sse req)
              {:status 200
               :mcp-session-id session-id
               :sse/start-stream
               (fn [sse]
                 ((start-sse-stream session-id conn-id) sse)
                 (callback result))}
              (do
                (callback result)
                {:status 200
                 :mcp-session-id session-id})))))

      (and method id) ;; request
      (let [session-id (or mcp-session-id (str (random-uuid)))
            conn-id (random-uuid)]
        (if (:sse req)
          {:status 200
           :mcp-session-id session-id
           :sse/handler
           (fn [emit close]
             ((start-sse-stream session-id conn-id) emit close)
             (mcp/handle-request (assoc rpc-req :state state/state :session-id session-id :connection-id conn-id)))}
          (do
            (mcp/handle-request (assoc rpc-req :state state/state :session-id session-id))
            {:status 200
             :mcp-session-id session-id}))))))

(defn GET [{:keys [mcp-session-id] :as req}]
  (log/info :GET (:headers req) :sse? (:sse req))
  (if (:sse req)
    {:status 200
     :sse/handler
     (fn [emit close]
       ((start-sse-stream mcp-session-id :default) emit (fn [_])))}
    {:status 400
     :body {:error {:code json-rpc/invalid-request
                    :message "GET request must accept text/event-stream"}}}))

(defn routes []
  [["/mcp" {:get #'GET :post #'POST}]])
