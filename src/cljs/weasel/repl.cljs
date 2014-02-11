(ns weasel.repl
  (:require [clojure.browser.event :as event :refer [event-types]]
            [clojure.browser.net :as net]
            [cljs.reader :as reader :refer [read-string]]
            [weasel.impls.websocket :as ws]))

(def ws-connection (atom nil))

(defmulti process-message :op)

(defmethod process-message
  :error
  [message]
  (throw (js/Error. (str "Websocket REPL error " (:type message)))))

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
                               ("No stacktrace available."))}))}))

(defn repl-print
  [x]
  (if-let [conn @ws-connection]
    (net/transmit @ws-connection {:op :print :value (pr-str x)})))

(defn connect
  [repl-server-url]
  (let [repl-connection (ws/websocket-connection)]
    (swap! ws-connection (constantly repl-connection))

    (event/listen repl-connection :opened
      (fn [evt]
        (net/transmit repl-connection (pr-str {:op :ready}))
        (.info js/console "Opened WS connection!")))

    (event/listen repl-connection :message
      (fn [evt]
        (let [{:keys [op] :as message} (read-string (.-message evt))
              response (-> message process-message pr-str)]
          (net/transmit repl-connection response))))

    (event/listen repl-connection :error
      (fn [evt] (.error js/console "WebSocket error" evt)))

    (net/connect repl-connection repl-server-url)))
