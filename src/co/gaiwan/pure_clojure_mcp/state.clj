(ns co.gaiwan.pure-clojure-mcp.state
  (:require [malli.json-schema :as malli-json-schema]))

(defonce state (atom {:sessions {}
                      :resources {}
                      :tools {}
                      :resource-remplates {}
                      :prompts {}}))

(defn add-prompt [{:keys [name title description arguments messages-fn]}]
  (swap! state update :prompts assoc name
         {:title title
          :description description
          :arguments arguments
          :messages-fn messages-fn}))

(defn add-resource [{:keys [uri name title description mime-type load-fn]}]
  (swap! state update :resources assoc uri
         {:name name
          :title title
          :description description
          :mimeType mime-type
          :load-fn load-fn}) )

(defn add-tool [{:keys [name title description schema tool-fn]}]
  (swap! state update :tools assoc name
         {:title title
          :description description
          :inputSchema schema
          :tool-fn tool-fn}))



(comment
  (add-prompt
   {:name "joke"
    :title "Rate a joke"
    :description "Given a joke, ask the model to rate it"
    :arguments [{:name "joke"
                 :description "the joke to rate"
                 :required true}]
    :messages-fn (fn [{:keys [joke]}]
                   [{:role "user"
                     :content
                     {:type "text"
                      :text (str "Hey, I heard this great joke. Do you think it's funny? Please rate it on a scale from 1 to 5.\n\n<joke>" joke "</joke>")}}])}
   )

  (add-resource
   {:uri "rfc:6749"
    :name "RFC 6749"
    :title "The OAuth 2.0 Authorization Framework"
    :description "The OAuth 2.0 authorization framework enables a third-party application to obtain limited access to an HTTP service"
    :mime-type "text/plain"
    :load-fn #(slurp "https://www.rfc-editor.org/rfc/rfc6749.txt")})

  (add-tool
   {:name "clojure_find_vars"
    :title "Find matching public vars (functions or globals) in Clojure"
    :description "Given a partial function name, find all defined vars which
  contain that partial name as a substring of their fully qualified name

  Examples:
  * <input>fetch</input>
    <output>[\"#'clojure.tools.gitlibs.impl/git-fetch\"]</output>
  * <input>core/get-re</input>
    <output> [\"#'muuntaja.core/get-response-format-and-charset\", \"#'muuntaja.core/get-request-format-and-charset\"]</output>
"
    :schema (malli.json-schema/transform
             [:map
              [:partial-name {:description "Substring to look for in fully-qualified name"} string?]])
    :tool-fn
    (fn [{:keys [partial-name]}]
      {:content
       [{:type "text"
         :text (str
                (vec
                 (for [ns (all-ns)
                       [_ var] (ns-publics ns)
                       :let [var-name (str var)]
                       :when (clojure.string/includes? var-name partial-name)]
                   var-name)))}]
       :isError false})})
  )
