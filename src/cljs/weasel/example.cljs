(ns weasel.example
  (:require [weasel.repl :as ws-repl]
            #_[clojure.browser.repl :as cljs-repl]))

(enable-console-print!)

#_(cljs-repl/connect "http://localhost:9000/repl")
(ws-repl/connect "ws://localhost:9001/" :verbose true)
