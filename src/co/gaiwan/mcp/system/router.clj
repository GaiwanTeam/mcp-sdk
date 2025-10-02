(ns co.gaiwan.mcp.system.router
  "HTTP router and middleware setup"
  (:require
   [co.gaiwan.mcp.http-api :as api]
   [co.gaiwan.mcp.lib.ring-sse :as ring-sse]
   [lambdaisland.log4j2 :as log]
   [muuntaja.core :as muuntaja]
   [muuntaja.format.charred :as muuntaja-charred]
   [reitit.coercion.malli]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as ring-coercion]
   [reitit.ring.middleware.muuntaja :as reitit-muuntaja]
   [reitit.ring.middleware.parameters :as reitit-params]))

(def malli-coercion-options
  {:error-keys #{:type :coercion :in :schema :value :errors :humanized :transformed}})

(defn muuntaja-instance
  "Muuntaja instance used for request and response coercion"
  []
  (muuntaja/create
   (-> muuntaja/default-options
       (assoc-in [:formats "application/json"] muuntaja-charred/format)
       (assoc-in [:formats "application/json; charset=utf-8"] muuntaja-charred/format))))

(defn- update-handler [{:keys [handler] :as verb-data}]
  (if (var? handler)
    (merge
     (cond-> verb-data
       false #_(not (config/get :dev/route-var-handlers))
       (update :handler deref))
     (meta handler))
    verb-data))

(defn- compile-var-meta
  "Take route-data, find handlers defined as vars, and expand them with metadata"
  [route-data]
  (reduce (fn [d m]
            (if (contains? d m)
              (do
                (update d m update-handler))
              d))
          route-data
          [:get :post :put :delete]))

(defn wrap-mcp-headers [handler]
  (fn [req]
    (let [id (get-in req [:headers "mcp-session-id"])
          version (get-in req [:headers "mcp-protocol-version"])
          res (handler (cond-> req
                         id (assoc :mcp-session-id id)
                         version (assoc :mcp-protocol-version version)))
          id  (or id (get res :mcp-session-id))]
      (if id
        (assoc-in res [:headers "Mcp-Session-Id"] id)
        res))))

(defn reitit-compile-fn
  "Wrap reitit's default compile-fn so we can hook into this to transform routes"
  [[path data] opts]
  (ring/compile-result [path (compile-var-meta data)] opts))

(defn wrap-log [handler]
  (fn [req]
    (let [start (System/currentTimeMillis)]
      (log/debug :request/starting (select-keys req
                                                [:request-method :uri :query-string
                                                 :content-type :content-length
                                                 :headers]))
      (let [res (handler req)]
        (log/debug :request/finished {:status (:status res)
                                      :content-type (get-in res [:headers "content-type"])
                                      :content-length (get-in res [:headers "content-length"])
                                      :time-ms (- (System/currentTimeMillis) start)
                                      ;; :body (:body res)
                                      :headers (:headers res)})
        res))))

(defn router []
  (let [routes (into ["" {}
                      ["/ping" {:get (constantly {:status 200 :body "pong"})}]]
                     (api/routes))]
    (ring/router
     routes
     {:compile reitit-compile-fn
      :data
      {:coercion   (reitit.coercion.malli/create malli-coercion-options)
       :muuntaja   (muuntaja-instance)
       :middleware [reitit-params/parameters-middleware
                    reitit-muuntaja/format-negotiate-middleware
                    reitit-muuntaja/format-response-middleware
                    reitit-muuntaja/format-request-middleware
                    ring-coercion/coerce-exceptions-middleware
                    ring-coercion/coerce-response-middleware
                    ring-coercion/coerce-request-middleware
                    wrap-log
                    wrap-mcp-headers
                    ring-sse/wrap-sse]}})))
