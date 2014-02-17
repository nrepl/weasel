(defproject weasel "0.1.0-SNAPSHOT"
  :description "websocket repl for clojurescript"
  :url "http://github.com/tomjakubowski/weasel"
  :license {:name "Unlicense"
            :url "http://unlicense.org/UNLICENSE"
            :distribution :repo}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2138"]
                 [org.clojure/google-closure-library "0.0-20130212-95c19e7f0f5f"]
                 [http-kit "2.1.16"]]

  :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.1.2"]]}}

  :plugins [[lein-cljsbuild "1.0.1"]]
  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
  :resource-paths ["src/cljs"]
  :source-paths ["src/clj"]

  :cljsbuild {
    :builds [{:id "example"
              :source-paths ["src/cljs/weasel"]
              :compiler {
                :output-to "weasel.js"
                :output-dir "out"
                :optimizations :none
                :source-map true}}]})
