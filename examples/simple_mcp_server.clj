(ns simple-mcp-server
  (:require
   [co.gaiwan.mcp :as mcp]
   [co.gaiwan.mcp.state :as state]
   [malli.json-schema :as mjs]))

;; Add a tool
(state/add-tool
 {:name "greet"
  :title "Greeting Tool"
  :description "Sends a personalized greeting"
  :schema (mjs/transform [:map [:name string?]])
  :tool-fn (fn [req {:keys [name]}]
             (def req req)
             {:content [{:type "text" :text (str "Hello, " name "!")}]
              :isError false})})

;; Add a prompt
(state/add-prompt
 {:name "joke-rating"
  :title "Joke Rater"
  :description "Rate how funny a joke is"
  :arguments [{:name "joke" :description "The joke to rate" :required true}]
  :messages-fn (fn [req {:keys [joke]}]
                 [{:role "user"
                   :content {:type "text"
                             :text (str "Rate this joke from 1-5:\n\n" joke)}}])})

(mcp/run-http {:port 3999})
