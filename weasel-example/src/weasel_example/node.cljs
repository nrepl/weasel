(ns weasel-example.node
  "A non-browser entry point. Compiled for Node, this connects back to the same
   Weasel REPL server over the platform's native WebSocket - no browser involved.
   The open connection keeps the Node process alive, so once you run it you can
   evaluate ClojureScript in it from the REPL."
  (:require [weasel.repl :as repl]
            [weasel-example.foo :as foo]))

(repl/connect "ws://localhost:9001" :verbose false)

(println "weasel-example Node client connected; foo/foo =" foo/foo)
