(ns weasel.repl.websocket
  (:refer-clojure :exclude [loaded-libs])
  (:require [cljs.repl]
            [cljs.compiler :as cmp]
            [cljs.env :as env]
            [cljs.analyzer :as ana]
            [weasel.repl.server :as server]
            [clojure.pprint :as pp]))

(def loaded-libs (atom #{}))

(defn websocket-receive
  [channel msg]
  (println "received message" channel msg))

(defn websocket-setup-env
  [this]
  (cmp/with-core-cljs)
  (server/start {:port 9001})
  (println "<< started server >>"))

(defn websocket-tear-down-env
  []
  (server/stop)
  (println "<< stopped server >>"))

(defn websocket-eval
  [this js]
  (try
    (read-string (server/ask! (pr-str {:op :eval-js, :code js})))
    (catch Exception e
      {:status :error,
       :value (str "Error evaluating form: " (.getMessage e))})))

(defn load-javascript
  "Does this ever get called?"
  [repl-env ns url]
  (println "loading javascript" ns url))

(defrecord WebsocketEnv []
  cljs.repl/IJavaScriptEnv
  (-setup [this] (websocket-setup-env this))
  (-evaluate [this _ _ js] (websocket-eval this js))
  (-load [this ns url] (load-javascript this ns url))
  (-tear-down [_] (websocket-tear-down-env)))

(defn repl-env
  "Returns a JS environment to pass to repl or piggieback"
  [& {:as opts}]
  (let [opts (merge (WebsocketEnv.)
               {::env/compiler (env/default-compiler-env)
                :src "src/"}
               opts)]
    opts))

(comment
  (cemerick.piggieback/cljs-repl
    :repl-env (weasel.repl.websocket/repl-env :port 9001)
    :verbose true)
  )

(comment
  (let [user-env '{:ns nil :locals {}}
        cenv (atom {})]
    (env/with-compiler-env cenv
      (cmp/with-core-cljs
        (cmp/emit (ana/analyze user-env '(println "hello world"))))
      ))
  )
