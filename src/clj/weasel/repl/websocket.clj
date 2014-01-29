(ns weasel.repl.websocket
  (:require [cljs.repl]
            [weasel.repl.server :as server]))

(def current-repl-env (atom nil))

(defn websocket-receive
  [channel msg]
  (println "received message" channel msg))

(defn websocket-setup-env
  [_]
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
  [this ns url])

(defn repl-env
  "Returns a JS environment to pass to repl or piggieback"
  [& {:as opts}]
  (reify
    cljs.repl/IJavaScriptEnv
    (-setup [this] (websocket-setup-env this))
    (-evaluate [_ _ _ js] (websocket-eval js))
    (-load [this ns url] (load-javascript this ns url))
    (-tear-down [_] (websocket-tear-down-env))))

(comment
  (cemerick.piggieback/cljs-repl
    :repl-env (weasel.repl.websocket/repl-env :port 9001))
  )
