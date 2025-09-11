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
{:deps {co.gaiwan/mcp-sdk {:git/url "https://github.com/gaiwan/mcp-sdk"
                          :sha "..."}}}
```

Create a simple MCP server:

```clojure
(ns my-mcp-server
  (:require 
    [co.gaiwan.mcp.state :as mcp]
    [malli.json-schema :as mjs]))

;; Add a tool
(mcp/add-tool
  {:name "greet"
   :title "Greeting Tool"
   :description "Sends a personalized greeting"
   :schema (mjs/transform [:map [:name string?]])
   :tool-fn (fn [{:keys [name]}]
              {:content [{:type "text" :text (str "Hello, " name "!")}]
               :isError false})})

;; Add a prompt
(mcp/add-prompt
  {:name "joke-rating"
   :title "Joke Rater"
   :description "Rate how funny a joke is"
   :arguments [{:name "joke" :description "The joke to rate" :required true}]
   :messages-fn (fn [{:keys [joke]}]
                  [{:role "user"
                    :content {:type "text"
                             :text (str "Rate this joke from 1-5:\n\n" joke)}}])})
```

Run the server:

```bash
# STDIO mode (for CLI tools)
clj -M -m co.gaiwan.mcp stdio

# HTTP mode (for web applications)
clj -M -m co.gaiwan.mcp http --port 3000
```

## Core Concepts

### State Management

The `co.gaiwan.mcp.state` namespace provides atomic state management for:
- **Tools**: Callable functions with input schemas
- **Prompts**: Template messages for LLM interactions
- **Resources**: External content accessible via URIs
- **Sessions**: Client connection state

### Protocol Handlers

The `co.gaiwan.mcp.protocol` namespace implements MCP request/response handling:
- `initialize` - Session setup and capability negotiation
- `tools/list`, `tools/call` - Tool discovery and execution
- `prompts/list`, `prompts/get` - Prompt management
- `resources/list`, `resources/read` - Resource access

### Transport Layers

- **STDIO**: `co.gaiwan.mcp.system.stdio` - JSON-RPC over standard input/output
- **HTTP**: `co.gaiwan.mcp.system.http` - RESTful API with SSE support
- **Router**: `co.gaiwan.mcp.system.router` - Request routing and middleware

## Configuration

Server configuration uses Lambdaisland/config with multiple sources:

```clojure
;; config.edn
{:system/http {:port 3000}
 :dev/route-var-handlers true}
```

Environment variables:
```bash
CLOJURE_MCP_SYSTEM_HTTP_PORT=3000
```

## Development

Start a development server:

```clojure
;; In REPL
(user/go)  ; Start HTTP server
(user/refresh)  ; Reload changed namespaces
```

## License

Copyright © 2025 Arne Brasseur

Licensed under the Apache License, Version 2.0.
