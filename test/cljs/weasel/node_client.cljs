(ns weasel.node-client
  "A tiny Node entry point used by the integration test to prove that the
   REPL client works outside the browser, on a native `WebSocket`.

   Args: <url> [heartbeat-interval-ms]"
  (:require [weasel.repl :as repl]))

(defn -main [& args]
  (let [url (or (first args) "ws://127.0.0.1:9001")
        hb  (some-> (second args) (js/parseInt 10))]
    (apply repl/connect url
           :verbose false
           (if hb [:heartbeat-interval hb] []))))

(set! *main-cli-fn* -main)
