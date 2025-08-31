(ns user)

(defn go []
  (try
    ((requiring-resolve 'co.gaiwan.pure-clojure-mcp.config/start!))
    (catch Exception e
      (println e)))
  ((requiring-resolve 'co.gaiwan.pure-clojure-mcp.config/print-table)))

(defn refresh []
  ((requiring-resolve 'clojure.tools.namespace.repl/set-refresh-dirs)
   (clojure.java.io/file "src")
   (clojure.java.io/file "test"))
  ((requiring-resolve 'co.gaiwan.pure-clojure-mcp.config/refresh)))

(defn error []
  (or ((requiring-resolve 'co.gaiwan.pure-clojure-mcp.config/error))
      *e))

(defn component [id]
  ((requiring-resolve 'co.gaiwan.pure-clojure-mcp.config/component) id))

(defn restart! [& ks]
  ((requiring-resolve 'co.gaiwan.pure-clojure-mcp.config/restart!) ks))
