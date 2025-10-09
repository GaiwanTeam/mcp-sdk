# Clojure MCP SDK

A pure Clojure SDK for building Model Context Protocol (MCP) servers. This
library provides everything you need to create MCP servers that work over both
STDIO and HTTP transports.

## Features

- **Full MCP Protocol Support**: Implements the complete MCP specification (protocol version 2025-06-18)
- **Support all MCP types**: tools, prompts, resources, and resource templates
- **Dual Transport Support**: Run servers over STDIO (standalone process) or HTTP (persistent server, easy to hack on)
- **Capability negotiation**: Request roots from the client if they support it, notify client of new tools/resources/prompts

## Quick Start

Add to your `deps.edn`:

```clojure
{:deps {co.gaiwan/mcp-sdk {:mvn/version "0.2.17"}}}
```

Create a simple MCP server:

```clojure
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

#_(mcp/run-stdio! {})
(mcp/run-http! {:port 3999})
```

## Development

This library uses [Launchpad](https://github.com/github/launchpad), use
`bin/launchpad` to start a development process/REPL. See the Launchpad README
for how to customize your `deps.local.edn`.

## License

Copyright © 2025 Arne Brasseur

Licensed under the Apache License, Version 2.0.
