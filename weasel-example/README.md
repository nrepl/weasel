# Weasel example

A small project that shows a Weasel REPL driving two targets: a web page and a
Node process. It uses the Clojure CLI (`deps.edn`) and depends on the Weasel
checkout next to it (`:local/root ".."`), so it always runs against the source
in this repo. In your own project you'd depend on a released version instead:

```clojure
{:deps {weasel/weasel {:mvn/version "0.8.0"}}}
```

You'll need the [Clojure CLI](https://clojure.org/guides/install_clojure), and
Node for the non-browser part.

## Browser

Compile the example:

```sh
clojure -M:build
```

Start a Weasel REPL. The quickest way is a plain terminal REPL, no editor
needed:

```sh
./scripts/start-repl
```

It prints `<< waiting for client to connect ... ` and blocks until the page
connects. In another terminal, serve the project over HTTP and open it:

```sh
python3 -m http.server 8000
# now visit http://localhost:8000
```

Serve it over `http://localhost`, not `file://`: a `file://` page has an opaque
origin that the REPL server rejects, and Closure's dev-mode script loading needs
a real HTTP origin anyway.

Once the page loads it connects back to the REPL, and the terminal drops you at a
`cljs.user=>` prompt running in the browser:

```clojure
cljs.user=> (js/document.getElementById "afield")
cljs.user=> (set! (.-value (js/document.getElementById "afield")) "hi from the REPL")
cljs.user=> weasel-example.foo/baz
456
```

Reload the page and the REPL reconnects on its own - auto-reconnect is on by
default, so you can keep your REPL session across reloads and server restarts.

### From an editor instead

If you'd rather drive it from CIDER, Calva, or another nREPL client, start an
nREPL server with piggieback:

```sh
clojure -M:repl
```

Connect your editor to the port it prints, then start the Weasel REPL from the
session:

```clojure
(require 'weasel.repl.websocket)
(cider.piggieback/cljs-repl (weasel.repl.websocket/repl-env :port 9001))
```

(CIDER users can skip the form: `cider-jack-in-cljs` ships a built-in `weasel`
REPL type.)

## Node

The same client runs outside the browser. Compile the Node entry point:

```sh
clojure -M:node-build
```

Start a Weasel REPL as above (`./scripts/start-repl`), then run the compiled
client:

```sh
node out-node/main.js
```

It connects back over Node's native `WebSocket` - no browser, no extra
dependencies - and the REPL is now evaluating inside the Node process:

```clojure
cljs.user=> (.-version js/process)
cljs.user=> (+ 1 2)
3
```

That's the whole point of the native-WebSocket client: a REPL-driven workflow in
any modern JavaScript runtime, the browser being just one of them.
