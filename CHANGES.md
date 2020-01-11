# Changelog

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
