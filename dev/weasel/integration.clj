(ns weasel.integration
  "End-to-end checks of the native-WebSocket transport against a real server
  driving the compiled Node client. Exercises, in one run:

    * a round-trip that both prints and returns a value
    * the heartbeat (the client keeps pinging, the server pongs, and the
      connection is not torn down)
    * auto-reconnect after the server is bounced

  Run with:

      lein cljsbuild once node
      lein run -m weasel.integration"
  (:require [clojure.edn :as edn]
            [weasel.repl.server :as server]))

(def ^:private client-js "target/node/weasel_node_client.js")
(def ^:private port 9009)
(def ^:private heartbeat-ms 300)

;; the current round's signal promises, swapped out between rounds
(def ^:private signals (atom nil))
(def ^:private ping-count (atom 0))
(def ^:private ready-count (atom 0))

(defn- fresh-round! []
  (reset! signals {:ready (promise) :printed (promise) :result (promise)}))

(defn- handle [data]
  (let [msg (edn/read-string data)
        {:keys [ready printed result]} @signals]
    (case (:op msg)
      :ready  (do (swap! ready-count inc) (deliver ready true))
      :print  (deliver printed (:value msg))
      :result (deliver result (:value msg))
      :ping   (do (swap! ping-count inc)
                  (server/send! (pr-str {:op :pong})))
      nil)))

(defn- await! [what p]
  (let [v (deref p 10000 ::timeout)]
    (when (= v ::timeout)
      (throw (ex-info (str "timed out waiting for " what) {})))
    v))

(defn- wait-for-client! [what]
  ;; like server/wait-for-client, but bounded so a broken reconnect fails the
  ;; run instead of hanging forever
  (when (= ::timeout (deref (server/channel) 10000 ::timeout))
    (throw (ex-info (str "timed out waiting for " what) {}))))

(defn- eval! [code]
  (server/send! (pr-str {:op :eval-js :code code})))

(defn -main [& _]
  (let [ok? (atom false)]
    (fresh-round!)
    (server/start handle :ip "127.0.0.1" :port port)
    (let [^"[Ljava.lang.String;" cmd (into-array String
                                       ["node" client-js (str "ws://127.0.0.1:" port)
                                        (str heartbeat-ms)])
          proc (-> (ProcessBuilder. cmd) (.inheritIO) (.start))]
      (try
        ;; round 1 - result and print travel back over the socket
        (wait-for-client! "client to connect")
        (await! ":ready" (:ready @signals))
        (eval! "(function () { weasel.repl.repl_print('hi from node'); return 40 + 2; })()")
        (let [value (:value (await! ":result" (:result @signals)))
              out   (await! ":print" (:printed @signals))]
          (assert (= value "42") (str "eval result was " (pr-str value)))
          (assert (= out (pr-str "hi from node")) (str "print was " (pr-str out))))

        ;; heartbeat - over several intervals the client keeps pinging (proving
        ;; it processes pongs and the cycle continues) and the healthy socket is
        ;; NOT torn down (no spurious reconnect, so still exactly one :ready)
        (Thread/sleep (long (* 5 heartbeat-ms)))
        (assert (>= @ping-count 2)
                (str "expected repeated heartbeat pings, saw " @ping-count))
        (assert (= 1 @ready-count)
                (str "heartbeat tore down a healthy socket; :ready count = " @ready-count))

        ;; round 2 - bounce the server, the client reconnects on its own
        (fresh-round!)
        (server/stop)
        (Thread/sleep 200)
        (server/start handle :ip "127.0.0.1" :port port)
        (wait-for-client! "client to reconnect")
        (await! ":ready (after reconnect)" (:ready @signals))
        (eval! "7 * 6")
        (assert (= "42" (:value (await! ":result (after reconnect)" (:result @signals))))
                "reconnected eval returned the wrong value")

        (reset! ok? true)
        (println (str "PASS - eval, print, heartbeat (" @ping-count
                      " pings) and reconnect all verified"))
        (catch Throwable e
          (println "FAIL:" (.getMessage e)))
        (finally
          (.destroy proc)
          (server/stop))))
    (when-not @ok?
      (System/exit 1))))
