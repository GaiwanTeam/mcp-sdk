(ns co.gaiwan.mcp.system.http
  "HTTP server component"
  (:require
   [co.gaiwan.mcp.system.router :as router]
   [lambdaisland.log4j2 :as log]
   [reitit.ring :as reitit-ring]
   [ring.adapter.jetty :as jetty]))

(defn start! [{:keys [port]
               :or {port 3000}}]
  (log/info :http/starting {:port port})
  (jetty/run-jetty
   (reitit-ring/ring-handler
    (router/router)
    (reitit-ring/create-default-handler))
   {:port port
    :output-buffer-size 1
    :join? false}))

(defn stop! [jetty]
  (.stop jetty))
