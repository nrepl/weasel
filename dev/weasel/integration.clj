(ns weasel.integration
  "End-to-end check of the native-WebSocket transport.

  Starts a real Weasel server, launches the compiled ClojureScript client
  under Node and drives a single round-trip that exercises both the result
  and the print paths: the evaluated snippet prints a string and returns a
  value, and we assert both travel back over the socket. Run with:

      lein cljsbuild once node
      lein run -m weasel.integration"
  (:require [clojure.edn :as edn]
            [weasel.repl.server :as server]))

(def ^:private client-js "target/node/weasel_node_client.js")

(defn- await!
  "Derefs promise `p`, throwing if it does not resolve within 10 seconds."
  [what p]
  (let [v (deref p 10000 ::timeout)]
    (when (= v ::timeout)
      (throw (ex-info (str "timed out waiting for " what) {})))
    v))

(defn -main [& _]
  (let [ready   (promise)
        printed (promise)
        result  (promise)
        port    9009
        ok?     (atom false)]
    (server/start
      (fn [data]
        (let [msg (edn/read-string data)]
          (case (:op msg)
            :ready  (deliver ready true)
            :print  (deliver printed (:value msg))
            :result (deliver result (:value msg))
            nil)))
      :ip "127.0.0.1" :port port)
    (let [^"[Ljava.lang.String;" cmd (into-array String ["node" client-js (str "ws://127.0.0.1:" port)])
          proc (-> (ProcessBuilder. cmd) (.inheritIO) (.start))]
      (try
        (server/wait-for-client)
        (await! ":ready" ready)
        (server/send!
          (pr-str {:op :eval-js
                   :code "(function () { weasel.repl.repl_print('hi from node'); return 40 + 2; })()"}))
        (let [value (:value (await! ":result" result))
              out   (await! ":print" printed)]
          (println "RESULT:" (pr-str value) " PRINT:" (pr-str out))
          (reset! ok? (and (= value "42")
                           (= out (pr-str "hi from node")))))
        (catch Exception e
          (println "FAIL:" (.getMessage e)))
        (finally
          (.destroy proc)
          (server/stop))))
    (if @ok?
      (println "PASS")
      (do (println "FAIL") (System/exit 1)))))
