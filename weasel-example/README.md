# Weasel example

## Build

Run:

`lein cljsbuild once`

To fetch the dependencies and build `weasel_example.js`.

## REPL

Start a `lein repl` and enter this:

```clojure
(require 'weasel.repl.websocket)
(cider.piggieback/cljs-repl
    (weasel.repl.websocket/repl-env :ip "0.0.0.0" :port 9001))
```

Once that executes, open `index.html` in your browser and *voila*, your REPL is connected
to the browser.
