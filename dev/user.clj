(ns user)

(defn go []
  (try
    ((requiring-resolve 'co.gaiwan.mcp.config/start!) [:system/http
                                                       :system/watch-state])
    (catch Exception e
      (println e)))
  ((requiring-resolve 'co.gaiwan.mcp.config/print-table)))

(defn refresh []
  ((requiring-resolve 'clojure.tools.namespace.repl/set-refresh-dirs)
   (clojure.java.io/file "src")
   (clojure.java.io/file "test"))
  ((requiring-resolve 'co.gaiwan.mcp.config/refresh)))

(defn error []
  (or ((requiring-resolve 'co.gaiwan.mcp.config/error))
      *e))

(defn component [id]
  ((requiring-resolve 'co.gaiwan.mcp.config/component) id))

(defn restart! [& ks]
  ((requiring-resolve 'co.gaiwan.mcp.config/restart!) ks))
