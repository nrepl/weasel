(ns weasel-example.foo
  [:require [weasel.repl :as repl]])

;;; an auxillary namespace that exposes some symbols, for testing
;;; cross-NS analysis at the REPL.

(def foo 1234)
(def baz 456)

(if (repl/alive?)
  (println "Loaded foo"))
