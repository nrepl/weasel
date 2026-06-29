(ns weasel-example.example
  (:require [weasel.repl :as repl]
            [weasel-example.foo :as foo]))

;; Connect back to the Weasel REPL server. Auto-reconnect is on by default, so
;; this survives page reloads and server restarts on its own.
(when-not (repl/alive?)
  (repl/connect "ws://localhost:9001"))

(println "weasel-example loaded; foo/baz =" foo/baz)
