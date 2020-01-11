# Weasel

[![Clojars Project](http://clojars.org/weasel/latest-version.svg)](http://clojars.org/weasel)

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
* WebSocket APIs are available in unusual JavaScript environments like
  [JavaScriptCore][goby], [QML][qml], [WinJS][winjs], browser
  extensions, Mac OS X Dashboard widgets, and so on, where use of
  `CrossPageChannel` may be unfeasible due to restrictions on or
  unavailability of `<iframe>` elements.  Weasel allows the
  ClojureScript developer to still enjoy the benefits of REPL driven
  development in these exotic domains.

## Usage

Weasel is intended to be used with nREPL's
[piggieback][] middleware.  Once you've set that up,
add Weasel as a dependency to `project.clj`:

```clojure
[weasel "0.7.1" :exclusions [org.clojure/clojurescript]]
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
                               ; default for all is nil
```

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

Note that unless a client is connected to the WebSocket channel,
evaluation will fail:

```clojure
cljs.user> (+ 5 10)
java.io.IOException: No client connected to Websocket
nil
```

Only a single client can be connected to the REPL at once.  Attempting
to connect to an occupied REPL server will throw an exception in the
client.

## Example

An example project is included in the `weasel-example` subdirectory of
this project.

## Need help?

If you have any feedback or issues to report, feel free to open an
issue on [GitHub](https://github.com/tomjakubowski/weasel).

## A weasel "piggiebacking" on a woodpecker

A little treat for reading the whole README!

![](http://i.imgur.com/XIaZZ2k.jpg)

[goby]: <https://github.com/mfikes/goby>
[qml]: <http://doc.qt.io/qt-5/qml-qt-websockets-websocket.html>
[winjs]: <https://msdn.microsoft.com/en-us/library/windows/apps/hh761442.aspx>
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
