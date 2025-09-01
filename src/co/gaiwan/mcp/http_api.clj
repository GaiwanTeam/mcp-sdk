(ns co.gaiwan.mcp.http-api
  (:require
   [charred.api :as charred]
   [co.gaiwan.mcp.json-rpc :as json-rpc]
   [co.gaiwan.mcp.protocol :as mcp]
   [co.gaiwan.mcp.state :as state]
   [lambdaisland.log4j2 :as log])
  (:import
   (java.util.concurrent BlockingQueue)))

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
           [:method {:optional true} string?]
           [:id {:optional true} any?]
           [:response {:optional true} [:map {:closed false}]]
           [:params {:optional true} [:or
                                      [:map {:closed false}]
                                      [:vector any?]]]]}}
  [{:keys [parameters mcp-session-id] :as req}]
  (log/info :POST (-> req :parameters :body))
  (let [{:keys [method params result id] :as rpc-req} (:body parameters)]
    (cond
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

      (and result mcp-session-id id) ;; response
      (do
        (log/debug :response/reply {:id id :result result})
        (when-let [req (get-in @state/state [:requests id])]
          (let [conn-id (random-uuid)
                handle-response (fn []
                                  (swap! state/state update :requests dissoc id)
                                  ((or (:callback req) mcp/handle-response)
                                   (assoc req :state state/state :session-id mcp-session-id :connection-id conn-id :result result)))]
            (if (:sse req)
              {:status 200
               :sse/start-stream
               (fn [sse]
                 ((start-sse-stream mcp-session-id conn-id) sse)
                 (handle-response))}
              (do
                (handle-response)
                {:status 202})))))

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
       (future
         (let [queue (get-in @state/state [:sessions mcp-session-id :connections :default :queue])]
           (try
             (loop [response (.take ^BlockingQueue queue)]
               (log/debug :get/emitting response)
               (emit
                (merge {:event "message"}
                       (update response :data charred/write-json-str)))
               (recur (.take ^BlockingQueue queue)))
             (catch Throwable t
               (log/error :get-stream/broke {} :exception t))
             (finally
               (close))))))}
    {:status 400
     :body {:error {:code json-rpc/invalid-request
                    :message "GET request must accept text/event-stream"}}}))

(defn routes []
  [["/mcp" {:get #'GET :post #'POST}]])
