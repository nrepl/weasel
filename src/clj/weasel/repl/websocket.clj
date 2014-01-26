(ns weasel.repl.websocket
  (:require [cljs.repl]
            [weasel.repl.server :as server]))

(def current-repl-env (atom nil))

(declare
  websocket-eval
  websocket-setup-env
  load-javascript
  websocket-tear-down-env)

(defrecord WebSocketEnv []
  cljs.repl/IJavaScriptEnv
  (-setup [this] (websocket-setup-env this))
  (-evaluate [_ _ _ js] (websocket-eval js))
  (-load [this ns url] (load-javascript this ns url))
  (-tear-down [_] (websocket-tear-down-env)))

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
  (read-string (server/ask! (pr-str {:op :eval-js, :code js}))))

(defn repl-env
  "Returns a JS environment to pass to repl or piggieback"
  [& {:as opts}]
  (WebSocketEnv.))

(comment
  (cemerick.piggieback/cljs-repl
    :repl-env (weasel.repl.websocket/repl-env :port 9001))
  )
