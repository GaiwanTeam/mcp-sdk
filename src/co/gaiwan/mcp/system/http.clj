(ns co.gaiwan.mcp.system.http
  "HTTP server component"
  (:require
   [lambdaisland.log4j2 :as log]
   [reitit.ring :as reitit-ring]
   [s-exp.hirundo :as hirundo]))

(defn start [config]
  (log/info :http/starting {:port (:port config)})
  {:server
   (hirundo/start!
    {:port (:port config)
     :http-handler/sse
     (reitit-ring/ring-handler
      (:router config)
      (reitit-ring/create-default-handler))})})

(def component
  {:start start
   :stop (fn [o] (prn o) (hirundo/stop! (:server o)))})

(comment
  (user/restart! :system/http))
