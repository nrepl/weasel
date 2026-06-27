(ns weasel.node-client
  "A tiny Node entry point used by the integration test to prove that the
   REPL client works outside the browser, on a native `WebSocket`."
  (:require [weasel.repl :as repl]))

(defn -main [& args]
  (let [url (or (first args) "ws://127.0.0.1:9001")]
    (repl/connect url :verbose false)))

(set! *main-cli-fn* -main)
