(defproject weasel "0.1.0-SNAPSHOT"
  :description "websocket repl for clojurescript"
  :url "http://github.com/tomjakubowski/weasel"

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2138"]
                 [com.cemerick/piggieback "0.1.2"]
                 [org.clojure/google-closure-library "0.0-20130212-95c19e7f0f5f"]]

  :plugins [[lein-cljsbuild "1.0.1"]]

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :source-paths ["src"]

  :cljsbuild {
    :builds [{:id "hello"
              :source-paths ["src"]
              :compiler {
                :output-to "weasel.js"
                :output-dir "out"
                :optimizations :none
                :source-map true}}]})
