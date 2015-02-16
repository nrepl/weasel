(ns weasel-example.foo
  [:require [weasel.repl :as repl]])

(def foo 1234)

(if (repl/alive?)
  (println "Loaded foo"))
