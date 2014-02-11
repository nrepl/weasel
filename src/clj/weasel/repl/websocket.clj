(ns weasel.repl.websocket
  (:refer-clojure :exclude [loaded-libs])
  (:require [cljs.repl]
            [cljs.closure :as cljsc]
            [cljs.compiler :as cmp]
            [cljs.env :as env]
            [weasel.repl.server :as server]))

(declare send-for-eval!)

(def loaded-libs (atom #{}))

(def ^:private repl-out
  "stores the value of *out* when the server is started"
  (atom nil))

(def ^:private client-response
  "stores a promise fulfilled by a client's eval response"
  (atom nil))

(defmulti process-message (fn [_ msg] (:op msg)))

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
      (print (read-string string))
      (flush))))

;;; websocket receiver doesn't run in same thread as the REPL, so
;;; env/*compiler* isn't bound on the receiver.
(defmethod process-message
  :ready
  [renv _]
  (binding [*out* (or @repl-out *out*)
            env/*compiler* (::env/compiler renv)]
    (send-for-eval! (cljsc/compile-form-seq
                      '[(ns cljs.user)
                        (set-print-fn! weasel.repl/repl-print)]))))

(defn websocket-setup-env
  [this]
  (reset! repl-out *out*)
  (require 'cljs.repl.reflect)
  (cljs.repl/analyze-source (:src this))
  (cmp/with-core-cljs)
  (server/start
    (fn [data] (process-message this (read-string data)))
    :port (:port this))
  (println "<< started server >>"))

(defn websocket-tear-down-env
  []
  (reset! repl-out nil)
  (server/stop)
  (println "<< stopped server >>"))

(defn websocket-eval
  [js]
  (reset! client-response (promise))
  (send-for-eval! js)
  (let [ret @@client-response]
    (reset! client-response nil)
    ret))

(defn load-javascript
  "TODO: determine when/how this is called"
  [repl-env ns url]
  (println "loading javascript" ns url))

(defrecord WebsocketEnv []
  cljs.repl/IJavaScriptEnv
  (-setup [this] (websocket-setup-env this))
  (-evaluate [_ _ _ js] (websocket-eval js))
  (-load [this ns url] (load-javascript this ns url))
  (-tear-down [_] (websocket-tear-down-env)))

(defn repl-env
  "Returns a JS environment to pass to repl or piggieback"
  [& {:as opts}]
  (let [opts (merge (WebsocketEnv.)
               {::env/compiler (env/default-compiler-env)
                :port 9001
                :src "src/"}
               opts)]
    opts))

(defn- send-for-eval! [js]
  (server/send! (pr-str {:op :eval-js, :code js})))

(defn- start-wrepl
  ([] (start-wrepl 9001))
  ([port]
     (cemerick.piggieback/cljs-repl
       :repl-env (repl-env :port port)
       :verbose false)))

(comment
  (let [user-env '{:ns nil :locals {}}
        cenv (atom {})]
    (env/with-compiler-env cenv
      (cmp/with-core-cljs
        #_(cmp/emit (ana/analyze user-env '(println "hello world")))
        (cljsc/compile-form-seq '[(ns cljs.user)]))
      ))
  )
