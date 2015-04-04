(ns weasel.repl.server
  (:require [org.httpkit.server :as http :refer [on-close on-receive with-channel]])
  (:import [java.io IOException]))

(defonce state (atom {:server nil
                      :channel nil      ; when the server starts, a
                                        ; promise that derefs to a
                                        ; channel when a client
                                        ; connects
                      :response-fn nil}))

(defn handler [request]
  (if-not (:websocket? request)
    {:status 200 :body "Please connect with a websocket!"}
    (with-channel request channel
      (if (realized? (:channel @state))
        (do
          (http/send! channel (pr-str {:op :error, :type :occupied}))
          (http/close channel))
        (do
          (deliver (:channel @state) channel)
          (on-close channel (fn [_] (swap! state assoc :channel (promise))))
          (on-receive channel (:response-fn @state)))))))

(defn send!
  [msg]
  (if-let [channel (:channel @state)]
    (http/send! (deref channel) msg)
    (throw (IOException. "WebSocket server not started!"))))

(defn channel []
  (:channel @state))

(defn start
  [f & {:keys [ip port] :as opts}]
  {:pre [(ifn? f)]}
  (swap! state
    assoc :server (http/run-server #'handler opts)
          :channel (promise)
          :response-fn f))

(defn stop []
  (let [stop-server (:server @state)]
    (when-not (nil? stop-server)
      (stop-server)
      (reset! state {:server nil
                     :channel nil
                     :response-fn nil})
      @state)))

(defn wait-for-client []
  (deref (:channel @state))
  nil)

(defn restart []
  (stop)
  (start {}))
