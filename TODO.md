# TODO

before a 0.1.0 release:

* make the default namespace (`cljs.user`) work without needing to do a
  `(ns cljs.user)` at the REPL.
* add printing support, so `(println "hello world")` prints the
  needful to the REPL instead of the browser log. (maybe make this
  opt-outable since I noticed this was a pain sometimes with the
  regular browser REPL)
* figure out what load-javascript is supposed to do, and do that
* add some tests (hahaha ... :weary: ...)

notes:

for the first two items here, when the client connects and is ready we
just need to send:

```clojure
(cljs.compiler/-compile
  '[(ns cljs.user)
    (set! *print-fn* clojure.browser.repl/repl-print)] {})
```

which will require a refactoring of weasel.repl.server. anyway I think
ask!  is really awkwardly implemented as-is. A better course of action
is probably just to pass in a function (a multi-method dispatching on
`:op` perhaps) to `server/start` that handles any traffic.

also to make the `*print-fn*` stuff work we will probably need to not
use such fragile call/response semantics (since the first message back
from the client after requesting an eval may not be the return
value).
