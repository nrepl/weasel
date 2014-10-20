# Weasel example

## Build

Run:

`lein cljsbuild once`

To fetch the dependencies and build `weasel_example.js`.

## REPL

Start a `lein repl` and enter this:

```clojure
(require 'weasel.repl.websocket)
(cemerick.piggieback/cljs-repl :repl-env (weasel.repl.websocket/repl-env))
```

Once that executes, open `index.html` in your browser and *voila*, your REPL is connected
to the browser.
