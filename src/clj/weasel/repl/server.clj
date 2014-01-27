(ns weasel.repl.server
  (:require [org.httpkit.server :as http :refer [on-close on-receive with-channel]])
  (:import [java.io IOException]))

;;; only support a single client/channel. i'm not sure how we could
;;; support more than one from the same repl session, or if that even
;;; makes sense
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
          (on-receive channel (fn [data] (when-let [f (:response-fn @state)]
                                           (f data)))))))))

(defn send!
  [msg]
  (when-let [channel (:channel @state)]
    (http/send! channel msg)))

(defn ask!
  "Send message to client and block waiting for a response, returning
  that response. If no client is connected when called, throws an exception."
  [msg]
  (let [p (promise)]
    (when (nil? (:channel @state))
      (throw (IOException. "No client connected to WebSocket channel.")))
    (future
      (swap! state assoc :response-fn
        (fn [response]
          (swap! state dissoc :response-fn)
          (deliver p response)))
      (send! msg))
    @p))

(defn start
  [{:keys [port]}]
  (swap! state
    assoc :server (http/run-server #'handler {:port (or port 9001)})))

(defn stop []
  (let [stop-server (:server @state)]
    (when-not (nil? stop-server)
      (stop-server)
      (reset! state {})
      @state)))

(defn restart []
  (stop)
  (start {}))
