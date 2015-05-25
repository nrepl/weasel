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

(def ^:private client-response
  "stores a promise fulfilled by a client's eval response"
  (atom nil))

(declare
  send-for-eval!
  websocket-setup-env
  websocket-eval
  load-javascript
  websocket-tear-down-env
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

(defmulti ^:private process-message (fn [_ msg] (:op msg)))

(defmethod process-message
  :result
  [_ message]
  (let [result (:value message)]
    (when-not (nil? @client-response)
      (deliver @client-response result))))

(defmethod process-message
  :print
  [_ message]
  (let [string (:value message)]
    (binding [*out* (or @repl-out *out*)]
      (print (read-string string)))))

(defmethod process-message
  :ready
  [_ _])

(defn- websocket-setup-env
  [this opts]
  (reset! repl-out *out*)
  (server/start
    (fn [data] (process-message this (read-string data)))
    :ip (:ip this)
    :port (:port this))
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
  (reset! client-response (promise))
  (send-for-eval! js)
  (let [ret @@client-response]
    (reset! client-response nil)
    ret))

(defn- load-javascript
  [_ provides _]
  (websocket-eval
    (str "goog.require('" (cmp/munge (first provides)) "')")))

(defn- send-for-eval! [js]
  (server/send! (pr-str {:op :eval-js, :code js})))
