(defproject weasel "0.1.1-SNAPSHOT"
  :description "websocket REPL environment for ClojureScript"
  :url "http://github.com/tomjakubowski/weasel"
  :license {:name "Unlicense"
            :url "http://unlicense.org/UNLICENSE"
            :distribution :repo}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2138"]
                 [org.clojure/google-closure-library "0.0-20130212-95c19e7f0f5f"]
                 [http-kit "2.1.16"]]

  :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.1.2"]]}}
  :source-paths ["src/clj" "src/cljs"])
