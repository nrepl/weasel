(ns weasel.repl
  (:require [clojure.browser.event :as event :refer [event-types]]
            [clojure.browser.net :as net]
            [cljs.reader :as reader :refer [read-string]]
            [weasel.impls.websocket :as ws]))

(def ^:private ws-connection (atom nil))

(defn alive? []
  "Returns truthy value if the REPL is attempting to connect or is
   connected, or falsy value otherwise."
  (not (nil? @ws-connection)))

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
  (if-let [conn @ws-connection]
    (net/transmit @ws-connection {:op :print :value (apply pr-str args)})))

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
  (let [repl-connection (ws/websocket-connection)]
    (swap! ws-connection (constantly repl-connection))
    (event/listen repl-connection :opened
      (fn [evt]
        (set-print-fn! (if (fn? print) print (get print-fns print)))
        (net/transmit repl-connection (pr-str {:op :ready}))
        (when verbose (.info js/console "Opened Websocket REPL connection"))
        (when (fn? on-open) (on-open))))

    (event/listen repl-connection :message
      (fn [evt]
        (let [{:keys [op] :as message} (read-string (.-message evt))
              response (-> message process-message pr-str)]
          (net/transmit repl-connection response))))

    (event/listen repl-connection :closed
      (fn [evt]
        (reset! ws-connection nil)
        (when verbose (.info js/console "Closed Websocket REPL connection"))
        (when (fn? on-close) (on-close))))

    (event/listen repl-connection :error
      (fn [evt]
        (when verbose (.error js/console "WebSocket error" evt))
        (when (fn? on-error) (on-error evt))))

    ;; Monkey-patch goog.require if running under optimizations :none - David
    ;; This is copied from Clojurescript (cljs.browser.repl/connect).
    (when-not js/COMPILED
      (set! *loaded-libs*
        (let [gntp (.. js/goog -dependencies_ -nameToPath)]
          (into #{}
            (filter
              (fn [name]
                (aget (.. js/goog -dependencies_ -visited) (aget gntp name)))
              (js-keys gntp)))))
      (set! (.-isProvided_ js/goog) (fn [_] false))
      (set! (.-require js/goog)
        (fn [name reload]
          (when (or (not (contains? *loaded-libs* name)) reload)
            (set! *loaded-libs* (conj (or *loaded-libs* #{}) name))
            (.appendChild js/document.body
              (let [script (.createElement js/document "script")]
                (set! (.-type script) "text/javascript")
                (set! (.-src script)
                  (str js/goog.basePath
                    (aget (.. js/goog -dependencies_ -nameToPath) name)))
                script))))))

    (net/connect repl-connection repl-server-url)))
