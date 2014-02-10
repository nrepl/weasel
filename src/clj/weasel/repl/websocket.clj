(ns weasel.repl.websocket
  (:refer-clojure :exclude [loaded-libs])
  (:require [cljs.repl]
            [cljs.repl.browser]
            [cljs.compiler :as cmp]
            [cljs.env :as env]
            [weasel.repl.server :as server]))

(def loaded-libs (atom #{}))

(defn websocket-receive
  [channel msg]
  (println "received message" channel msg))

(defn websocket-setup-env
  [this]
  (require 'cljs.repl.reflect)
  (cljs.repl/analyze-source (:src this))
  (cmp/with-core-cljs)
  (server/start {:port 9001})
  (println "<< started server >>"))

(defn websocket-tear-down-env
  []
  (server/stop)
  (println "<< stopped server >>"))

(defn websocket-eval
  [js]
  (try
    (read-string (server/ask! (pr-str {:op :eval-js, :code js})))
    (catch Exception e
      {:status :error,
       :value (str "Error evaluating form: " (.getMessage e))})))

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
                :src "src/"}
               opts)]
    opts))

(defn- start-brepl
  ([] (start-brepl 9000))
  ([port]
     (cemerick.piggieback/cljs-repl
       :repl-env (cljs.repl.browser/repl-env :port port)
       )))

(defn- start-wrepl
  ([] (start-wrepl 9001))
  ([port]
     (cemerick.piggieback/cljs-repl
       :repl-env (repl-env :port port)
       :verbose true)))

(comment
  (let [user-env '{:ns nil :locals {}}
        cenv (atom {})]
    (env/with-compiler-env cenv
      (cmp/with-core-cljs
        (cmp/emit (ana/analyze user-env '(println "hello world"))))
      ))
  )
