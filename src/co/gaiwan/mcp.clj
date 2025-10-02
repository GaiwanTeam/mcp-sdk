(ns co.gaiwan.mcp
  "mcp-sdk main entry points"
  (:require
   [co.gaiwan.mcp.system.http :as http]
   [co.gaiwan.mcp.system.stdio :as stdio]
   [co.gaiwan.mcp.system.watch-state :as watch]))

(defn run-http!
  "Run HTTP/SSE based MCP server"
  [opts]
  (http/start! opts)
  (watch/start! opts))

(defn run-stdio!
  "Run STDIO based MCP server"
  [opts]
  (http/start! opts)
  (watch/start! opts))
