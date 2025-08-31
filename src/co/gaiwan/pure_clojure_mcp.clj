(ns co.gaiwan.pure-clojure-mcp
  ""
  (:gen-class)
  (:require
   [clojure.pprint :as pprint]
   [lambdaisland.cli :as cli]
   [co.gaiwan.pure-clojure-mcp.config :as config]))

(defn run-cmd [_]
  (config/start!))

(defn show-config-cmd [_]
  (config/load!)
  (pprint/print-table
   (for [[k {:keys [val source]}] (config/entries)]
     {"key" k "value" val "source" source})))

(def commands
  ["run" #'run-cmd
   "show-config" #'show-config-cmd])

(def flags
  ["--port=<port>"
   {:key :http/port
    :doc "Set the HTTP port"}])

(defn -main [& args]
  (cli/dispatch
   {:name "co.gaiwan.pure-clojure-mcp"
    :doc ""
    :commands commands
    :flags flags
    :middleware [(fn [cmd]
                   (fn [opts]
                     (reset! config/cli-opts opts)
                     (config/reload!)
                     (cmd opts)))]}
   args))
