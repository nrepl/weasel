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
