(ns weasel.repl.websocket
  (:refer-clojure :exclude [loaded-libs])
  (:require [cljs.repl]
            [cljs.closure :as cljsc]
            [cljs.compiler :as cmp]
            [cljs.env :as env]
            [weasel.repl.server :as server]))

(def ^:private repl-out
  "stores the value of *out* when the server is started"
  (atom nil))

(def ^:private pending-eval
  "the outstanding evaluation as {:channel ch :promise p}, or nil. Keeping the
   target channel and its result promise in one atom means they are always read
   and written together, so only the client an eval was sent to can satisfy it."
  (atom nil))

(declare
  websocket-setup-env
  websocket-eval
  load-javascript
  websocket-tear-down-env
  on-client-disconnect
  transitive-deps)

(defrecord WebsocketEnv []
  cljs.repl/IJavaScriptEnv
  (-setup [this opts] (websocket-setup-env this opts))
  (-evaluate [_ _ _ js] (websocket-eval js))
  (-load [this ns url] (load-javascript this ns url))
  (-tear-down [_] (websocket-tear-down-env)))

(defn repl-env
  "Returns a JS environment to pass to repl or piggieback"
  [& {:as opts}]
  (merge (WebsocketEnv.)
    {:ip "127.0.0.1"
     :port 9001}
    opts))

(def ^:private disconnect-result
  {:status :exception
   :value "Weasel client disconnected before returning a result"
   :stacktrace "No stacktrace available."})

(defn- deliver-if-active!
  "Delivers `value` to the outstanding evaluation, but only if it was sent to
   `channel` - so a stale or foreign message can't satisfy the wrong eval."
  [channel value]
  (when-let [{:keys [promise] ch :channel} @pending-eval]
    (when (= channel ch)
      (deliver promise value))))

(defmulti ^:private process-message (fn [_ msg] (:op msg)))

(defmethod process-message
  :result
  [channel message]
  (deliver-if-active! channel (:value message)))

(defmethod process-message
  :print
  [_ message]
  (let [string (:value message)]
    (binding [*out* (or @repl-out *out*)]
      (print (read-string string)))))

(defmethod process-message
  :ready
  [_ _])

(defmethod process-message
  :ping
  [channel _]
  ;; pong back to the client that pinged, not the active one
  (server/send-to! channel (pr-str {:op :pong})))

(defmethod process-message
  :default
  [_ _])

(defn- on-client-disconnect
  "Unblocks an outstanding evaluation when the client it was sent to drops, so
   the REPL reports an error instead of hanging forever."
  [channel]
  (deliver-if-active! channel disconnect-result))

(defn- websocket-setup-env
  [this opts]
  (reset! repl-out *out*)
  (server/start
    (fn [channel data] (process-message channel (read-string data)))
    :ip (:ip this)
    :port (:port this)
    :allowed-origins (:allowed-origins this))
  (server/on-disconnect! on-client-disconnect)
  (let [{:keys [ip pre-connect]} this]
    (let [port (-> @server/state :server meta :local-port)]
      (println (str "<< started Weasel server on ws://" ip ":" port " >>")))
    (print "<< waiting for client to connect ... ")
    (flush)
    (when pre-connect (pre-connect))
    (server/wait-for-client)
    (println " connected! >>")))

(defn- websocket-tear-down-env
  []
  (reset! repl-out nil)
  (server/stop)
  (println "<< stopped server >>"))

(defn- websocket-eval
  [js]
  (let [channel (server/active-channel)
        p (promise)]
    (reset! pending-eval {:channel channel :promise p})
    ;; if the channel is already closed the message is silently dropped, so
    ;; surface that immediately rather than waiting for a result that won't come
    (when (false? (server/send-to! channel (pr-str {:op :eval-js, :code js})))
      (deliver p disconnect-result))
    (let [ret @p]
      (reset! pending-eval nil)
      ret)))

(defn- load-javascript
  [_ provides _]
  (websocket-eval
    (str "goog.require('" (cmp/munge (first provides)) "')")))
