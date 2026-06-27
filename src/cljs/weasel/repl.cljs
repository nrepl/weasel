(ns weasel.repl
  (:require [clojure.browser.repl :as brepl]
            [cljs.reader :as reader :refer [read-string]]
            [weasel.impls.websocket :as ws]))

;; Holds the state of the (single) REPL connection, or nil when disconnected.
;;
;;   {:id              generation token; sockets from a superseded connect are
;;                     ignored by comparing against it
;;    :url             the server url, reused when reconnecting
;;    :options         the normalized connect options
;;    :socket          the current WebSocket, or nil between attempts
;;    :fatal?          true once the server rejected us (e.g. :occupied)
;;    :opened?         true once the user :on-open callback has fired
;;    :backoff         the current reconnect delay, in ms
;;    :reconnect-timer the pending reconnect timeout id, or nil
;;    :heartbeat-timer the heartbeat interval id, or nil
;;    :stable-timer    a one-shot timer that, if the socket survives, clears the
;;                     backoff, or nil
;;    :awaiting-pongs  pings sent since the last pong (0 while healthy)
;;    :pong-seen?      true once the current socket has answered a ping}
(def ^:private connection (atom nil))

;; Monotonic source of generation tokens, one per connect call.
(def ^:private generation (atom 0))

;; How many pings a proven peer may leave unanswered before we call it dead and
;; tear the socket down. Tolerating more than one ride out network jitter, GC
;; pauses and a momentarily busy server.
(def ^:private max-missed-pongs 2)

;; How many unanswered pings to send a peer that has never ponged before giving
;; up on the heartbeat (without disturbing the connection itself). A merely slow
;; first pong, arriving within this window, keeps the heartbeat alive.
(def ^:private max-unanswered-pings 3)

(declare open! schedule-reconnect!)

(defn alive?
  "Returns truthy value if the REPL is attempting to connect or is
   connected, or falsy value otherwise."
  []
  (some? @connection))

(defn- current?
  "Is `id` the generation of the live connection?"
  [id]
  (= id (:id @connection)))

(defn- options []
  (:options @connection))

(defn- browser?
  "Returns true when running in a browser-like environment that can load
   additional code via Closure's script-tag mechanism."
  []
  (exists? js/document))

(defmulti process-message :op)

(defmethod process-message
  :error
  [message]
  (.error js/console (str "Websocket REPL error " (:type message)))
  nil)

(defmethod process-message
  :pong
  [_]
  ;; clear the outstanding pings without resurrecting a disconnected atom
  (swap! connection (fn [c] (when c (assoc c :awaiting-pongs 0 :pong-seen? true))))
  nil)

(defmethod process-message
  :eval-js
  [message]
  (let [code (:code message)]
    {:op :result
     :value (try
              {:status :success, :value (str (js* "eval(~{code})"))}
              (catch js/Error e
                {:status :exception
                 :value (pr-str e)
                 :stacktrace (if (.hasOwnProperty e "stack")
                               (.-stack e)
                               "No stacktrace available.")})
              (catch :default e
                {:status :exception
                 :value (pr-str e)
                 :stacktrace "No stacktrace available."}))}))

(defn repl-print
  [& args]
  (when-let [socket (:socket @connection)]
    (ws/send! socket (pr-str {:op :print :value (apply pr-str args)}))))

(defn console-print [& args]
  (.apply (.-log js/console) js/console (into-array args)))

(def print-fns
  {:repl repl-print
   :console console-print
   #{:repl :console} (fn [& args]
                       (apply console-print args)
                       (apply repl-print args))})

(defn- clear-socket-timers!
  "Cancels the per-socket heartbeat and stability timers."
  []
  (when-let [{:keys [heartbeat-timer stable-timer]} @connection]
    (when heartbeat-timer (js/clearInterval heartbeat-timer))
    (when stable-timer (js/clearTimeout stable-timer))
    (swap! connection assoc :heartbeat-timer nil :stable-timer nil)))

(defn- start-heartbeat!
  "Pings the server every `:heartbeat-interval` ms. A proven peer (one that has
   answered a ping on this socket) that then misses `max-missed-pongs` pings is
   treated as dead and the socket is torn down, triggering a reconnect. A peer
   that never answers is simply left alone after `max-unanswered-pings`, so the
   heartbeat never disrupts a server that doesn't speak it."
  [id socket]
  (let [interval (:heartbeat-interval (options))]
    (when (and interval (pos? interval))
      (let [timer (js/setInterval
                   (fn []
                     (when (current? id)
                       (let [{:keys [awaiting-pongs pong-seen?]} @connection]
                         (cond
                           (and pong-seen? (>= awaiting-pongs max-missed-pongs))
                           (ws/close! socket)

                           (and (not pong-seen?) (>= awaiting-pongs max-unanswered-pings))
                           (clear-socket-timers!)

                           :else
                           (do
                             (swap! connection update :awaiting-pongs inc)
                             (ws/send! socket (pr-str {:op :ping})))))))
                   interval)]
        (swap! connection assoc :heartbeat-timer timer)))))

(defn- mark-stable!
  "Once a socket has stayed open for the base reconnect delay we trust it and
   reset the backoff, so only a genuinely flapping server keeps backing off."
  [id socket]
  (let [delay (:reconnect-delay (options))
        timer (js/setTimeout
               (fn []
                 (when (and (current? id) (identical? socket (:socket @connection)))
                   (swap! connection assoc :backoff delay)))
               delay)]
    (swap! connection assoc :stable-timer timer)))

(defn- finish!
  "Permanently tears the connection down and fires the user :on-close once."
  []
  (let [on-close (:on-close (options))]
    (reset! connection nil)
    (when (fn? on-close) (on-close))))

(defn- make-handlers [id]
  {:on-open
   (fn [socket]
     (when (current? id)
       (let [{:keys [verbose print on-open]} (options)]
         (swap! connection assoc
                :socket socket
                :awaiting-pongs 0
                :pong-seen? false
                :reconnect-timer nil)
         (set-print-fn! (if (fn? print) print (get print-fns print)))
         (ws/send! socket (pr-str {:op :ready}))
         (start-heartbeat! id socket)
         (mark-stable! id socket)
         (when verbose (.info js/console "Opened Websocket REPL connection"))
         ;; the user :on-open callback is one-shot, not re-run on every reconnect
         (when-not (:opened? @connection)
           (swap! connection assoc :opened? true)
           (when (fn? on-open) (on-open))))))

   :on-message
   (fn [socket data]
     (when (current? id)
       (let [message (read-string data)]
         (when (and (= :error (:op message)) (= :occupied (:type message)))
           (swap! connection assoc :fatal? true))
         (when-let [response (process-message message)]
           (ws/send! socket (pr-str response))))))

   :on-close
   (fn [_]
     (when (current? id)
       (clear-socket-timers!)
       (swap! connection assoc :socket nil)
       (when (:verbose (options)) (.info js/console "Closed Websocket REPL connection"))
       (if (and (:reconnect? (options)) (not (:fatal? @connection)))
         (schedule-reconnect! id)
         (finish!))))

   :on-error
   (fn [evt]
     (when (current? id)
       (let [{:keys [verbose on-error]} (options)]
         (when verbose (.error js/console "WebSocket error" evt))
         (when (fn? on-error) (on-error evt)))))})

(defn- open!
  "Opens a fresh socket to the configured url for generation `id`. On failure
   (e.g. a runtime with no WebSocket) the connection is torn down so `alive?`
   reflects reality; the caller decides whether to surface the error."
  [id]
  (when (current? id)
    (try
      (let [socket (ws/connect! (:url @connection) (make-handlers id))]
        (swap! connection assoc :socket socket :reconnect-timer nil)
        socket)
      (catch :default e
        (reset! connection nil)
        (throw e)))))

(defn- schedule-reconnect!
  "Schedules a reconnect after the current backoff, then doubles it up to the
   configured ceiling."
  [id]
  (let [{:keys [max-reconnect-delay verbose]} (options)
        delay (:backoff @connection)]
    (when verbose
      (.info js/console (str "Reconnecting Weasel REPL in " delay "ms")))
    ;; swallow a synchronous open! failure here so a transient error doesn't
    ;; kill the reconnect loop with an uncaught exception in the timer
    (let [timer (js/setTimeout (fn [] (try (open! id) (catch :default _ nil))) delay)]
      (swap! connection assoc
             :reconnect-timer timer
             :backoff (min max-reconnect-delay (* 2 delay))))))

(defn disconnect
  "Closes the REPL connection and cancels any pending reconnect attempts."
  []
  (when-let [{:keys [socket reconnect-timer]} @connection]
    (clear-socket-timers!)
    (when reconnect-timer (js/clearTimeout reconnect-timer))
    ;; tear the state down synchronously so alive? is immediately false; the
    ;; socket's eventual onclose no-ops because its generation is gone
    (finish!)
    (when socket (ws/close! socket))))

(defn connect
  [repl-server-url & {:keys [verbose on-open on-error on-close print
                             reconnect? reconnect-delay max-reconnect-delay
                             heartbeat-interval]
                      :or {verbose true
                           print :repl
                           reconnect? true
                           reconnect-delay 1000
                           max-reconnect-delay 30000
                           heartbeat-interval 0}}]
  ;; replace any existing connection cleanly rather than leaking its timers
  (when @connection (disconnect))
  (let [id (swap! generation inc)
        options {:verbose verbose
                 :on-open on-open
                 :on-error on-error
                 :on-close on-close
                 :print print
                 :reconnect? reconnect?
                 :reconnect-delay reconnect-delay
                 :max-reconnect-delay max-reconnect-delay
                 :heartbeat-interval heartbeat-interval}]
    (reset! connection {:id id
                        :url repl-server-url
                        :options options
                        :socket nil
                        :fatal? false
                        :opened? false
                        :backoff reconnect-delay
                        :reconnect-timer nil
                        :heartbeat-timer nil
                        :stable-timer nil
                        :awaiting-pongs 0
                        :pong-seen? false})

    ;; reusable bootstrap - only meaningful in a browser, where new code is
    ;; loaded by appending script tags to the document
    (when (browser?) (brepl/bootstrap))

    (open! id)))
