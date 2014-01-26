(ns weasel.repl
  (:require [clojure.browser.event :as event :refer [event-types]]
            [clojure.browser.net :as net]
            [cljs.reader :as reader :refer [read-string]]
            [weasel.impls.websocket :as ws]))

(def ws-connection (atom nil))

;;; TODO: needs to connect to something that implements cljs.repl/IJavaScriptEnv
;;; see repl/browser.clj
;;; repl/server.clj
;;; repl.clj

(defmulti process-message :op)

(defmethod process-message
  :eval-js
  [message]
  (let [code (:code message)
        result {:status :success :value (js* "eval(~{code})")}]
    (pr-str result)))

(defn connect
  [repl-server-url]
  (let [repl-connection (ws/websocket-connection)]
    (swap! ws-connection (constantly repl-connection))
    (event/listen repl-connection :opened
      (fn [evt]
        (.log js/console "Opened WS connection!")))
    (event/listen repl-connection :message
      (fn [evt]
        (let [{:keys [op] :as message} (read-string (.-message evt))
              response (process-message message)]
          (println "got a message" op "from" message "... dispatching ...")
          (println "sending to server:" response)
          (net/transmit repl-connection response))))
    (event/listen repl-connection :error
      (fn [evt] (.error js/console "WebSocket error" evt)))
    (net/connect repl-connection repl-server-url)))
