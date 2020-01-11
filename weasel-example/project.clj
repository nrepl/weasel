(defproject weasel-example "NOT_RELEASED"
  :description "example project for the weasel ClojureScript REPL env"
  :url "https://github.com/nrepl/weasel"
  :license {:name "Unlicense"
            :url "http://unlicense.org/UNLICENSE"
            :distribution :repo}

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.520"]
                 [weasel "0.7.1"]]

  :repl-options {:welcome (println "Run (start-weasel) to start a Weasel REPL.")
                 :init (do
                         (require 'weasel.repl.websocket)
                         (defn start-weasel [& opts]
                           (cider.piggieback/cljs-repl
                             (apply weasel.repl.websocket/repl-env opts))))}
  :source-paths ["src"]
  :profiles {:dev {:dependencies [[cider/piggieback "0.4.2"]]
                   :plugins [[lein-cljsbuild "1.1.7"]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
                   :cljsbuild {:builds [{:id "weasel-example"
                                         :source-paths ["src"]
                                         :compiler {:output-to "weasel_example.js"
                                                    :output-dir "out"
                                                    :optimizations :none
                                                    :source-map true}}]}}})
