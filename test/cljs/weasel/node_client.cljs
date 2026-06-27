(ns weasel.node-client
  "A tiny Node entry point used by the integration test to prove that the
   REPL client works outside the browser, on a native `WebSocket`.

   Args: <url> [heartbeat-interval-ms] [client-id]

   When a client-id is given it is stashed on the global object so the test can
   evaluate `globalThis.CLIENT_ID` and confirm which client handled the eval."
  (:require [weasel.repl :as repl]))

(defn -main [& args]
  (let [url (or (first args) "ws://127.0.0.1:9001")
        hb  (some-> (second args) (js/parseInt 10))
        id  (nth args 2 nil)]
    (when id
      (set! (.-CLIENT_ID js/globalThis) id))
    (apply repl/connect url
           :verbose false
           (if hb [:heartbeat-interval hb] []))))

(set! *main-cli-fn* -main)
