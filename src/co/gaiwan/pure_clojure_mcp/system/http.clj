(ns co.gaiwan.pure-clojure-mcp.system.http
  "HTTP server component"
  (:require
   [lambdaisland.log4j2 :as log]
   [reitit.ring :as reitit-ring]
   [s-exp.hirundo :as hirundo]))

(defn start [config]
  (log/info :http/starting {:port (:port config)})
  {:server
   (hirundo/start!
    {:http-handler/sse (reitit-ring/ring-handler (:router config)
                                                 (reitit-ring/create-default-handler))
     :port         (:port config)})})

(def component
  {:start start
   :stop (fn [o] (prn o) (hirundo/stop! (:server o)))})

(comment
  (user/restart! :system/http))
