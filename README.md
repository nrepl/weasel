# Weasel

[![Clojars Project](http://clojars.org/weasel/latest-version.svg)](http://clojars.org/weasel)
[![CI](https://github.com/nrepl/weasel/actions/workflows/ci.yml/badge.svg)](https://github.com/nrepl/weasel/actions/workflows/ci.yml)

Weasel uses WebSockets to communicate between a ClojureScript REPL,
which is typically hosted on [nREPL][] using [piggieback][], and an
environment which can execute compiled ClojureScript, which can be a
web browser or any JavaScript environment that supports the WebSocket
APIs.

**We're looking for more maintainers for the project. If you're interested in helping out please ping @bbatsov.**

## Why?

* A WebSocket transport is simple and avoids some of the thornier bugs
  caused by the `CrossPageChannel` transport, which is used in the
  standard ClojureScript browser REPL and Austin. (see:
  [cemerick/austin#17][austin-17], [cemerick/austin#47][austin-47],
  [cemerick/austin#49][austin-49])
* WebSocket APIs are available far beyond the browser - Node, Deno, Bun, web
  and service workers, browser extensions, React Native, and more exotic
  embeddings like [QML][qml] - so Weasel can give you a REPL driven workflow in
  environments where the iframe-based standard browser REPL simply can't run.

## Usage

Weasel is intended to be used with nREPL's
[piggieback][] middleware.  Once you've set that up,
add Weasel as a dependency to `project.clj`:

```clojure
[weasel "0.8.0" :exclusions [org.clojure/clojurescript]]
```

Or, if you're using `deps.edn`:

```clojure
{:deps {weasel/weasel {:mvn/version "0.8.0"}}}
```

Start up `lein repl` and piggieback the Weasel REPL environment onto
the nREPL session, optionally specifying a port (defaults to 9001) and
an address to bind to (defaults to "127.0.0.1").

```clojure
user> (require 'weasel.repl.websocket)
nil
user> (cider.piggieback/cljs-repl
        (weasel.repl.websocket/repl-env :ip "0.0.0.0" :port 9001))
<< started Weasel server on ws://127.0.0.1:9001 >>
<< waiting for client to connect ...
```

Weasel will block the REPL, waiting for a client to connect.

In your project's ClojureScript source, require the Weasel client
namespace and connect to the REPL.

```clojure
(ns my.cljs.core
  (:require [weasel.repl :as repl]))

(when-not (repl/alive?)
  (repl/connect "ws://localhost:9001"))
```

You may optionally specify the following:
```clojure
:verbose ; boolean, defaults to true
:print ; :repl to print only to the repl,
       ; :console to print only to the console
       ; #{:repl :console} to print to both
       ; or any variadic function to handle printing differently.
       ; defaults to :repl
:on-open, :on-error, :on-close ; fns for handling websocket lifecycle events.
                               ; default for all is nil. :on-open and :on-close
                               ; fire once per connection (not on every
                               ; reconnect); :on-error receives the native
                               ; WebSocket event.
:reconnect? ; boolean, whether to reconnect automatically when the
            ; connection drops. defaults to true
:reconnect-delay ; initial reconnect backoff in ms, defaults to 1000.
                 ; the delay doubles after each failed attempt
:max-reconnect-delay ; ceiling for the backoff in ms, defaults to 30000
:heartbeat-interval ; ms between keepalive pings used to detect a silently
                    ; dead connection. 0 disables it, which is the default.
                    ; a few missed pongs are tolerated before the link is
                    ; considered dead, and a server that never answers pings
                    ; is left undisturbed
```

The connection survives page reloads and server restarts on its own, retrying
with the backoff until it gets back in. Call `(repl/disconnect)` to close it for
good and stop the reconnection attempts.

Connecting with options:
```clojure
(repl/connect "ws://localhost:9001"
   :verbose true
   :print #{:repl :console}
   :on-error #(print "Error! " %))
```

Load the script in your WebSocket-enabled environment (probably a page
in a web browser) and start evaluating ClojureScript at the REPL:

```clojure
cljs.user> (= (js/Number. 34) (js/Number. 34))
false
cljs.user> (do (js/alert "Hello world!") 42)
42
```

### Non-browser runtimes

Because the client talks over the platform's native `WebSocket`, it isn't tied
to the browser. Any runtime with a global `WebSocket` works, including Node 22+,
Deno, Bun, and web/service workers. Require `weasel.repl` from code compiled for
that target and call `repl/connect` exactly as you would in a browser:

```clojure
(ns my.app
  (:require [weasel.repl :as repl]))

(repl/connect "ws://localhost:9001")
```

The only browser-specific piece is Closure's script-tag code loading used to
pull in namespaces first required at the REPL; it's skipped automatically when
there is no `document`. Evaluating forms, printing, reconnection and the
heartbeat all behave the same everywhere.

Note that unless a client is connected to the WebSocket channel,
evaluation will fail:

```clojure
cljs.user> (+ 5 10)
java.io.IOException: No client connected to Websocket
nil
```

More than one client may be connected at once. Evaluations are sent to the
most recently connected client, so a newly connected client takes over the
REPL; the others stay connected and their printed output still reaches the
REPL. This pairs naturally with auto-reconnect - a client that drops and
comes back simply becomes the active one again.

### Security

The REPL server validates the `Origin` header of every WebSocket handshake.
WebSocket connections aren't subject to the same-origin policy, so without this
any page a developer happens to have open could connect to the server and take
over the REPL. By default only origins on the local machine (`localhost`,
`127.0.0.1`, `[::1]`, on any port, over http or https) are accepted, which covers
the usual "app served from localhost" setup. Non-browser clients (Node, Deno,
Bun) send no `Origin` header and are always allowed.

If you serve your app from somewhere else - a LAN IP for testing on a phone, a
custom dev domain - widen the allowlist with `:allowed-origins`:

```clojure
(weasel.repl.websocket/repl-env
  :ip "0.0.0.0" :port 9001
  :allowed-origins ["http://192.168.1.5:8080"])
```

`:allowed-origins` accepts a single origin string, a collection of exact origin
strings, a one-argument predicate that receives the origin (which may be `nil`),
or `:all` to turn the check off entirely.

Two things worth knowing about the built-in policies. A page opened straight from
disk over `file://` sends `Origin: null` and is rejected - serve it over
`http://localhost` instead, or pass `:all`. And a client that sends no `Origin`
header at all (every non-browser runtime) is always accepted, since browsers are
the only thing the same-origin rule constrains; pass your own predicate if you
need to restrict header-less clients too.

## Editor integration

Weasel is an ordinary piggieback ClojureScript REPL environment, so any nREPL
client can drive it: start an nREPL server with piggieback on the classpath,
connect your editor to it, and evaluate the `repl-env` form to upgrade the
session into a Weasel REPL.

If you use the Clojure CLI, an alias like this gives you that nREPL server:

```clojure
;; deps.edn
{:aliases
 {:cljs-repl
  {:extra-deps {nrepl/nrepl {:mvn/version "1.3.1"}
                cider/piggieback {:mvn/version "0.6.0"}
                weasel/weasel {:mvn/version "0.8.0"}
                org.clojure/clojurescript {:mvn/version "1.12.134"}}
   :main-opts ["-m" "nrepl.cmdline"
               "--middleware" "[cider.piggieback/wrap-cljs-repl]"]}}}
```

Run `clj -M:cljs-repl`, connect your editor to the port it prints, then start the
Weasel REPL from the session:

```clojure
(require 'weasel.repl.websocket)
(cider.piggieback/cljs-repl (weasel.repl.websocket/repl-env :port 9001))
```

**CIDER** (Emacs) ships a built-in Weasel REPL type, so you can skip the manual
form: run `M-x cider-jack-in-cljs` (or `cider-connect-cljs`) and pick `weasel`
when prompted for the ClojureScript REPL type.

**Calva** (VS Code) has no Weasel preset, but the generic path works: use
"Connect to a Running REPL in your Project", point it at the nREPL port from the
alias above, and evaluate the `cljs-repl` form in the REPL window to drop into
the Weasel REPL.

## Example

The `weasel-example` subdirectory has a small, self-contained project with a
step-by-step tutorial that drives a Weasel REPL into both a web page and a Node
process. It uses the Clojure CLI and runs against this repo's source, so it's a
good way to try the current code end to end.

## Need help?

If you have any feedback or issues to report, feel free to open an
issue on [GitHub](https://github.com/nrepl/weasel).

## A weasel "piggiebacking" on a woodpecker

A little treat for reading the whole README!

![](http://i.imgur.com/XIaZZ2k.jpg)

[qml]: <http://doc.qt.io/qt-5/qml-qt-websockets-websocket.html>
[nREPL]: <https://github.com/nrepl/nrepl>
[piggieback]: <https://github.com/nrepl/piggieback>
[austin-17]: <https://github.com/cemerick/austin/issues/17>
[austin-47]: <https://github.com/cemerick/austin/issues/47>
[austin-49]: <https://github.com/cemerick/austin/issues/49>

## License

This is free and unencumbered software released into the public domain.

Anyone is free to copy, modify, publish, use, compile, sell, or
distribute this software, either in source code form or as a compiled
binary, for any purpose, commercial or non-commercial, and by any
means.

In jurisdictions that recognize copyright laws, the author or authors
of this software dedicate any and all copyright interest in the
software to the public domain. We make this dedication for the benefit
of the public at large and to the detriment of our heirs and
successors. We intend this dedication to be an overt act of
relinquishment in perpetuity of all present and future rights to this
software under copyright law.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY
CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

For more information, please refer to http://unlicense.org/.
