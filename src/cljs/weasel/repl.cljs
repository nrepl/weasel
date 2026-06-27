(ns weasel.repl
  (:require [clojure.browser.repl :as brepl]
            [cljs.reader :as reader :refer [read-string]]
            [weasel.impls.websocket :as ws]))

(def ^:private ws-connection (atom nil))

(defn alive?
  "Returns truthy value if the REPL is attempting to connect or is
   connected, or falsy value otherwise."
  []
  (some? @ws-connection))

(defn- browser?
  "Returns true when running in a browser-like environment that can load
   additional code via Closure's script-tag mechanism."
  []
  (exists? js/document))

(defmulti process-message :op)

(defmethod process-message
  :error
  [message]
  (.error js/console (str "Websocket REPL error " (:type message))))

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
  (when-let [conn @ws-connection]
    (ws/send! conn (pr-str {:op :print :value (apply pr-str args)}))))

(defn console-print [& args]
  (.apply (.-log js/console) js/console (into-array args)))

(def print-fns
  {:repl repl-print
   :console console-print
   #{:repl :console} (fn [& args]
                       (apply console-print args)
                       (apply repl-print args))})

(defn connect
  [repl-server-url & {:keys [verbose on-open on-error on-close print]
                      :or {verbose true, print :repl}}]
  (let [repl-connection
        (ws/connect! repl-server-url
          {:on-open
           (fn [socket]
             (set-print-fn! (if (fn? print) print (get print-fns print)))
             (ws/send! socket (pr-str {:op :ready}))
             (when verbose (.info js/console "Opened Websocket REPL connection"))
             (when (fn? on-open) (on-open)))

           :on-message
           (fn [socket data]
             (let [message (read-string data)
                   response (-> message process-message pr-str)]
               (ws/send! socket response)))

           :on-close
           (fn [_]
             (reset! ws-connection nil)
             (when verbose (.info js/console "Closed Websocket REPL connection"))
             (when (fn? on-close) (on-close)))

           :on-error
           (fn [evt]
             (when verbose (.error js/console "WebSocket error" evt))
             (when (fn? on-error) (on-error evt)))})]

    (reset! ws-connection repl-connection)

    ;; reusable bootstrap - only meaningful in a browser, where new code is
    ;; loaded by appending script tags to the document
    (when (browser?) (brepl/bootstrap))

    repl-connection))
