(ns weasel.example
  (:require [weasel.repl :as ws-repl]
            [clojure.browser.repl :as cljs-repl]))

(enable-console-print!)

(cljs-repl/connect "http://localhost:9000/repl")
(ws-repl/connect "ws://localhost:9001/")
