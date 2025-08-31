(ns co.gaiwan.pure-clojure-mcp.api
  (:require
   [charred.api :as charred]
   [co.gaiwan.pure-clojure-mcp.json-rpc :as json-rpc]
   [co.gaiwan.pure-clojure-mcp.mcp :as mcp]
   [lambdaisland.log4j2 :as log]))

(defonce state (atom {:sessions {}
                      :resources
                      {"https://www.rfc-editor.org/rfc/rfc6749.txt"
                       {:name "RFC 6749"
                        :title "The OAuth 2.0 Authorization Framework"
                        :description "The OAuth 2.0 authorization framework enables a third-party application to obtain limited access to an HTTP service, either on behalf of a resource owner by orchestrating an approval interaction between the resource owner and the HTTP service, or by allowing the third-party application to obtain access on its own behalf.  This specification replaces and obsoletes the OAuth 1.0 protocol described in RFC 5849."
                        :mimeType "text/plain"}}}))

(defn start-sse-stream
  [session-id conn-id]
  (fn [{:keys [emit close]}]
    (log/debug :sse/start conn-id)
    (let [emit (fn [response]
                 (log/debug :sse/emit (update response :data select-keys [:id :method]))
                 (emit
                  (merge {:event "message"} (update response :data charred/write-json-str))))
          close (fn []
                  (log/debug :sse/close conn-id)
                  (swap! state update-in [:sessions session-id :connections] dissoc conn-id)
                  (close))]
      (swap! state assoc-in [:sessions session-id :connections conn-id] {:emit emit :close close})
      )))

(defn POST
  {:parameters
   {:body [:map {:closed false}
           ;; [:jsonrpc [:enum "2.0"]]
           ;; [:method string?]
           ;; [:id {:optional true} any?]
           ;; [:params {:optional true} [:or
           ;;                            [:map {:closed false}]
           ;;                            [:vector any?]]]
           ]}}
  [{:keys [parameters mcp-session-id] :as req}]
  (log/debug :POST (-> req :parameters :body))
  (let [{:keys [method params id] :as rpc-req} (:body parameters)]
    (cond
      (= "notifications/initialized" method)
      {:status 202}

      (and (not mcp-session-id) (not= "initialize" method))
      {:status 400
       :body {:result {:error "Missing Mcp-Session-Id header"}}}

      (and mcp-session-id (= "initialize" method))
      {:status 400
       :body {:result {:error "Re-initializing existing session"}}}

      (and mcp-session-id (not (get-in @state [:sessions mcp-session-id])))
      {:status 404
       :body {:result {:error (str "No session with Mcp-Session-Id "
                                   mcp-session-id " found")}}}

      (not id) ;; notification
      (do
        (mcp/handle-notification (assoc rpc-req :state state :session-id mcp-session-id))
        {:status 202})

      id ;; request
      (let [session-id (or mcp-session-id (str (random-uuid)))
            conn-id (random-uuid)]
        (if (:sse req)
          {:status 200
           :mcp-session-id session-id
           :sse/start-stream
           (fn [sse]
             ((start-sse-stream session-id conn-id) sse)
             (mcp/handle-request (assoc rpc-req :state state :session-id session-id :connection-id conn-id)))}
          (do
            (mcp/handle-request (assoc rpc-req :state state :session-id session-id))
            {:status 200
             :mcp-session-id session-id}))))))

(defn GET [{:keys [mcp-session-id] :as req}]
  (log/info :GET (:headers req))
  (if (:sse req)
    {:status 200
     :sse/start-stream (start-sse-stream :mcp-session-id :default)}
    {:status 400
     :body {:error {:code json-rpc/invalid-request
                    :message "GET request must accept text/event-stream"}}}))

(defn routes []
  [["/mcp" {:get #'GET :post #'POST}]])
