(ns weasel.repl.server
  (:require [org.httpkit.server :as http :refer [on-close on-receive with-channel]])
  (:import [java.io IOException]))

(defonce state (atom {:server nil
                      :clients []        ; connected channels, oldest first
                      :ready nil         ; a promise, realized while at least one
                                         ; client is connected and replaced with a
                                         ; fresh one once the last client leaves;
                                         ; nil while the server is stopped
                      :response-fn nil   ; (fn [channel data])
                      :on-disconnect nil})) ; (fn [channel])

;; Guards the :clients/:ready invariant (":ready is a realized promise iff
;; :clients is non-empty, and nil iff the server is stopped") so add/remove,
;; start and stop stay consistent.
(def ^:private lock (Object.))

(defn- add-client! [channel]
  (locking lock
    ;; ignore a connection that races server shutdown (:ready is nil when stopped)
    (when (:ready @state)
      (swap! state update :clients conj channel)
      ;; idempotent: only the first client of an empty server realizes the promise
      (deliver (:ready @state) true))))

(defn- remove-client! [channel]
  (let [removed?
        (locking lock
          (when (some #(identical? % channel) (:clients @state))
            (swap! state
              (fn [s]
                (let [clients (vec (remove #(identical? % channel) (:clients s)))]
                  (assoc s :clients clients
                           ;; re-arm a fresh promise only while the server runs and
                           ;; the last client just left, so the next eval blocks
                           ;; until a client reconnects
                           :ready (if (seq clients) (:ready s) (promise))))))
            true))]
    (when removed?
      (when-let [f (:on-disconnect @state)]
        (f channel)))))

(defn handler [request]
  (if-not (:websocket? request)
    {:status 200 :body "Please connect with a websocket!"}
    (with-channel request channel
      ;; multiple clients may connect; the most recent one wins evaluations,
      ;; while the others stay connected so their prints still reach the REPL
      (on-receive channel (fn [data]
                            (when-let [f (:response-fn @state)]
                              (f channel data))))
      (add-client! channel)
      ;; register the close handler only after adding the client: http-kit
      ;; invokes it synchronously if the socket is already closed, so this
      ;; removes a socket that dropped mid-handshake instead of leaving it as a
      ;; zombie in :clients
      (on-close channel (fn [_] (remove-client! channel))))))

(defn active-channel
  "Blocks until at least one client is connected, then returns the channel that
   should receive evaluations: the most recently connected one. Throws once the
   server is stopped."
  []
  (loop []
    (if-let [ready (:ready @state)]
      (do
        (deref ready)
        (or (peek (:clients @state)) (recur)))
      (throw (IOException. "WebSocket server not started!")))))

(defn send-to!
  "Sends `msg` to a specific `channel`. Returns false when the channel is
   already closed."
  [channel msg]
  (http/send! channel msg))

(defn send!
  "Sends `msg` to the active (most recently connected) client, blocking until a
   client is connected."
  [msg]
  (send-to! (active-channel) msg))

(defn ready
  "Returns the promise that is realized while at least one client is connected."
  []
  (:ready @state))

(defn on-disconnect!
  "Registers a one-arg function invoked with a channel whenever a client
   disconnects."
  [f]
  (swap! state assoc :on-disconnect f))

(defn start
  [f & {:keys [ip port] :as opts}]
  {:pre [(ifn? f)]}
  (locking lock
    (swap! state assoc
      :server (http/run-server #'handler opts)
      :clients []
      :ready (promise)
      :response-fn f
      :on-disconnect nil)))

(defn stop []
  (locking lock
    (let [stop-server (:server @state)
          ready (:ready @state)]
      (when-not (nil? stop-server)
        (stop-server)
        ;; wake anything blocked in active-channel/wait-for-client so it can
        ;; observe the stopped server and bail out cleanly
        (when ready (deliver ready true))
        (reset! state {:server nil
                       :clients []
                       :ready nil
                       :response-fn nil
                       :on-disconnect nil})
        @state))))

(defn wait-for-client []
  (when-let [ready (:ready @state)]
    (deref ready))
  nil)

(defn restart []
  (stop)
  (start {}))
