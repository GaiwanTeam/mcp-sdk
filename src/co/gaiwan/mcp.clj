(ns co.gaiwan.mcp
  ""
  (:gen-class)
  (:require
   [clojure.pprint :as pprint]
   [lambdaisland.cli :as cli]
   [co.gaiwan.mcp.config :as config]
   [lambdaisland.config :as config]
   [lambdaisland.config.cli :as config-cli]
   [lambdaisland.makina.app :as app]))

(defn start-app! [opts ks]
  (let [prefix (:config-prefix opts "mcp-clj-sdk")
        config (config-cli/add-provider
                (config/create {:prefix prefix})
                (atom cli-opts))
        system (app/create
                {:prefix       prefix
                 :ns-prefix    "co.gaiwan.mcp"
                 :data-readers {'config (partial config/get config)}})]
    (app/start! system ks)))

(defn run-http
  {:doc "Run streaming HTTP MCP server"
   :flags ["--port=<port>" {:key :http/port
                            :doc "Set the HTTP port"}]}
  [opts]
  (doseq [n (:require opts)]
    (require (symbol n)))
  (start-app! [:system/http :system/watch-state]))

(defn run-stdio
  "Run STDIO based MCP server"
  [opts]
  (doseq [n (:require opts)]
    (require (symbol n)))
  (config/start! [:system/stdio :system/watch-state]))

(def commands
  ["http" #'run-http
   "stdio" #'run-stdio])

(def flags
  ["-r,--require <ns>" {:doc "Namespace with initialization code to load"
                        :coll? true}])

(defn -main [& args]
  (cli/dispatch*
   {:name "co.gaiwan.mcp"
    :doc ""
    :commands commands
    :flags flags
    :middleware [(fn [cmd]
                   (fn [opts]
                     (reset! config/cli-opts opts)
                     (config/reload!)
                     (cmd opts)))]}
   args))
