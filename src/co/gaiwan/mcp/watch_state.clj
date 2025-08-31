(ns co.gaiwan.mcp.watch-state
  (:require
   [co.gaiwan.mcp.protocol :as mcp]
   [co.gaiwan.mcp.protocol :as state]))

(defn watch [k r o n]
  (when (not= (:prompts o) (:prompts n))
    (doseq [sid (keys (:sessions n))]
      (mcp/notify {:state state/state
                   :session-id sid
                   :method "notifications/prompts/list_changed"})))
  (when (not= (:resources o) (:resources n))
    (doseq [sid (keys (:sessions n))]
      (mcp/notify {:state state/state
                   :session-id sid
                   :method "notifications/resources/list_changed"})))
  (when (not= (:tools o) (:tools n))
    (doseq [sid (keys (:sessions n))]
      (mcp/notify {:state state/state
                   :session-id sid
                   :method "notifications/tools/list_changed"}))))

(defn init! []
  (add-watch state/state ::notify-changes watch))
