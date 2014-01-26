(ns weasel.repl.server
  (:require [org.httpkit.server :as http :refer [on-close on-receive with-channel]]))

;;; only support a single client/channel. i'm not sure how we could
;;; support more than one from the same repl session, or if that even
;;; makes sense
(defonce state (atom {:server nil
                      :channel nil}))

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
          (on-receive channel (fn [data] (println "received" data))))))))

(defn send!
  [msg]
  (when-let [channel (:channel @state)]
    (http/send! channel msg)))

(defn start
  [{:keys [port]}]
  (swap! state
    assoc :server (http/run-server #'handler {:port (or port 9001)})))

(defn stop []
  (let [stop-server (:server @state)]
    (when-not (nil? stop-server)
      (stop-server)
      (reset! state {:server nil :channel nil})
      @state)))

(defn restart []
  (stop)
  (start {}))
