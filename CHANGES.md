# Changelog

## 0.8.0 (unreleased)

### Changes

* Bump the minimum dependencies to Clojure 1.12 and ClojureScript 1.12.
* Bump `http-kit` to 2.8.1 and (dev-only) `piggieback` to 0.6.0.
* The REPL client now talks over the platform's native `WebSocket` instead of
  the legacy `goog.net.WebSocket` / `clojure.browser.net` machinery. As a
  result the client runs in any modern JavaScript runtime (browsers, Node 22+,
  Deno, Bun, web/service workers), not just the browser. Two consequences worth
  calling out:
  * The `weasel.impls.websocket` namespace, previously a `goog.net`-based
    protocol implementation, is now a small functional wrapper. Code that
    depended on its old vars (`websocket-connection`, the `IConnection`
    protocol, etc.) needs updating.
  * The objects passed to the `:on-error` and `:on-close` callbacks are now
    native `WebSocket` events rather than `goog.net.WebSocket` events.

### Enhancements

* The client now reconnects automatically when the connection drops (page
  reload, server restart, flaky network) using an exponential backoff. It is
  on by default and tunable via the `:reconnect?`, `:reconnect-delay` and
  `:max-reconnect-delay` options. Call `weasel.repl/disconnect` to close the
  connection and stop reconnecting.
* Added an optional application-level heartbeat (`:ping`/`:pong`) that detects a
  silently dead connection and triggers a reconnect. It is off by default and
  enabled via the `:heartbeat-interval` option; it never disrupts a server that
  doesn't answer pings.
* The server no longer rejects a second client. Several clients may be connected
  at once; evaluations go to the most recently connected one (so a new client
  takes over the REPL) while the others stay connected and their output still
  reaches the REPL. An evaluation whose target client disconnects mid-flight now
  reports an error instead of hanging the REPL.
* Ship a `deps.edn` so the library can be consumed via the Clojure CLI / tools.deps.
* Add a GitHub Actions CI pipeline and a basic test suite, including a Node
  round-trip integration test that exercises the full eval cycle over a real
  WebSocket.

## 0.7.0

### Enhancements

* The REPL's evaluation mechanism has been adjusted so that an eval
  without a client connected simply blocks until a client connects,
  rather than throwing an exception.

## 0.6.0

### Breaking changes

* Requires ClojureScript 0.0-2814 or newer.

## 0.5.0

### Breaking changes

* Requires ClojureScript 0.0-2665 or newer.
* Requires Piggieback 0.1.4 or newer.

## 0.4.0

### Breaking changes

* Requires Clojure 1.6.0 and ClojureScript 0.0-2311 or newer.
* Weasel no longer invokes the compiler in a way that creates files in `out/`.
  Any files the compiler generates for Weasel are now in `target/`.

### Enhancements

* The `repl-env` function now passes options to the compiler.
* Adds a `:print` option to `repl/connect` which controls where prints are sent.

## 0.3.0

### Breaking changes

* The `verbose` option to `weasel.repl/connect` now defaults to `true`.
* When `verbose` is false, the REPL client no longer logs Websocket
  errors to the console.

## 0.2.1

### Bug fixes

* Don't try to call string when no stacktrace available.
* Handle exceptions that are not instances of `js/Error` (e.g. `(throw
  "foo")`)

## 0.2.0

### Changes

* Requires ClojureScript 0.0-2202

### Enhancements

* Client: `weasel.repl/alive?` to determine if REPL is
  connected/connecting or dead. (#5)
* Client: `weasel.repl/connect` now takes optional callbacks
  `:on-open`, `:on-error`, `:on-close`.
