goog.addDependency("base.js", ['goog'], []);
goog.addDependency("../cljs/core.js", ['cljs.core'], ['goog.string', 'goog.array', 'goog.object', 'goog.string.StringBuffer']);
goog.addDependency("../clojure/browser/event.js", ['clojure.browser.event'], ['cljs.core', 'goog.events.EventType', 'goog.events.EventTarget', 'goog.events']);
goog.addDependency("../clojure/browser/net.js", ['clojure.browser.net'], ['goog.net.xpc.CrossPageChannel', 'clojure.browser.event', 'goog.net.xpc.CfgFields', 'cljs.core', 'goog.net.EventType', 'goog.json', 'goog.net.XhrIo']);
goog.addDependency("../weasel/impls/websocket.js", ['weasel.impls.websocket'], ['clojure.browser.event', 'clojure.browser.net', 'cljs.core', 'goog.net.WebSocket']);
goog.addDependency("../cljs/reader.js", ['cljs.reader'], ['cljs.core', 'goog.string']);
goog.addDependency("../weasel/repl.js", ['weasel.repl'], ['clojure.browser.event', 'clojure.browser.net', 'cljs.core', 'weasel.impls.websocket', 'cljs.reader']);
goog.addDependency("../clojure/browser/repl.js", ['clojure.browser.repl'], ['clojure.browser.event', 'clojure.browser.net', 'cljs.core']);
goog.addDependency("../weasel/example.js", ['weasel.example'], ['cljs.core', 'clojure.browser.repl', 'weasel.repl']);