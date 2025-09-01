(ns co.gaiwan.mcp.system.http
  "HTTP server component"
  (:require
   [lambdaisland.log4j2 :as log]
   [reitit.ring :as reitit-ring]
   [ring.adapter.jetty :as jetty]
   ))

(defn start [config]
  (log/info :http/starting {:port (:port config)})
  {:server
   (jetty/run-jetty
    (reitit-ring/ring-handler
     (:router config)
     (reitit-ring/create-default-handler))
    {:port (:port config)
     :output-buffer-size 1
     :join? false})})

(def component
  {:start start
   :stop (fn [o] (.stop (:server o)))})

(comment
  (user/restart! :system/http))
