(ns weasel.repl.server
  (:require [org.httpkit.server :as http :refer [on-close on-receive with-channel]])
  (:import [java.io IOException]))

(defonce state (atom {:server nil
                      :channel nil
                      :response-fn nil}))

(defn handler [request]
  (if-not (:websocket? request)
    {:status 200 :body "Please connect with a websocket!"}
    (with-channel request channel
      (if-not (nil? (:channel @state))
        (do
          (http/send! channel (pr-str {:op :error, :type :occupied}))
          (http/close channel))
        (do
          (swap! state assoc :channel channel)
          (on-close channel (fn [_] (swap! state dissoc :channel)))
          (on-receive channel (:response-fn @state)))))))

(defn send!
  [msg]
  (if-let [channel (:channel @state)]
    (http/send! channel msg)
    (throw (IOException. "No client connected to Websocket"))))

(defn channel []
  (:channel @state))

(defn start
  [f & {:keys [ip port] :as opts}]
  {:pre [(ifn? f)]}
  (swap! state
    assoc :server (http/run-server #'handler opts)
          :response-fn f))

(defn stop []
  (let [stop-server (:server @state)]
    (when-not (nil? stop-server)
      (stop-server)
      (reset! state {})
      @state)))

(defn restart []
  (stop)
  (start {}))
