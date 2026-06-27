(ns weasel.impls.websocket
  "A thin wrapper over the platform's native `WebSocket` object.

  Every modern JavaScript runtime - browsers, Node 21+, Deno, Bun, web and
  service workers, React Native - exposes a global `WebSocket`, so the REPL
  client no longer needs the legacy `goog.net.WebSocket` /
  `clojure.browser.net` machinery to talk to the server.")

(defn available?
  "Returns true when the runtime exposes a native `WebSocket` constructor."
  []
  (exists? js/WebSocket))

(defn open?
  "Returns true when `socket` is connected and ready to send."
  [socket]
  (and (some? socket)
       (== (.-readyState socket) (.-OPEN js/WebSocket))))

(defn send!
  "Sends `message` (a string) over `socket`, but only while the socket is
  open. A native `WebSocket` throws when `send` is called in any other state,
  so anything emitted before the connection opens or after it closes is
  silently dropped."
  [socket message]
  (when (open? socket)
    (.send socket message)))

(defn close!
  "Closes `socket`."
  [socket]
  (.close socket))

(defn connect!
  "Opens a `WebSocket` to `url` and wires the supplied handlers.

  `handlers` is a map of optional callbacks:

    :on-open    (fn [socket] ...)        called once the socket is open
    :on-message (fn [socket string] ...) called with each received text frame
    :on-error   (fn [event] ...)         called on a transport error
    :on-close   (fn [event] ...)         called when the socket closes

  The send-side handlers receive the socket itself so they always reply over
  the connection that fired the event, never a newer one. Returns the freshly
  created `WebSocket`."
  [url {:keys [on-open on-message on-error on-close]}]
  (when-not (available?)
    (throw (js/Error. "This JavaScript runtime does not provide a WebSocket implementation")))
  (let [socket (js/WebSocket. url)]
    (when on-open
      (set! (.-onopen socket) (fn [_] (on-open socket))))
    (when on-message
      (set! (.-onmessage socket) (fn [e] (on-message socket (.-data e)))))
    (when on-error
      (set! (.-onerror socket) (fn [e] (on-error e))))
    (when on-close
      (set! (.-onclose socket) (fn [e] (on-close e))))
    socket))
