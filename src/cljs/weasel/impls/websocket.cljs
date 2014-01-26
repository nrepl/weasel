(ns weasel.impls.websocket
  (:require [clojure.browser.net :as net :refer [IConnection connect transmit]]
            [clojure.browser.event :as event :refer [event-types]]
            [goog.net.WebSocket :as gwebsocket]))

(defprotocol IWebSocket
  (open? [this]))

(defn websocket-connection
  ([]
     (websocket-connection nil nil))
  ([auto-reconnect?]
     (websocket-connection auto-reconnect? nil))
  ([auto-reconnect? next-reconnect-fn]
     (goog.net.WebSocket. auto-reconnect? next-reconnect-fn)))

(extend-type goog.net.WebSocket
  IWebSocket
  (open? [this]
    (.isOpen this ()))

  net/IConnection
  (connect
    ([this url]
       (connect this url nil))
    ([this url protocol]
       (.open this url protocol)))

  (transmit [this message]
    (.send this message))

  (close [this]
    (.close this ()))

  event/EventType
  (event-types [this]
    (into {}
      (map
        (fn [[k v]]
          [(keyword (. k (toLowerCase)))
           v])
        (merge
          (js->clj goog.net.WebSocket/EventType))))))
