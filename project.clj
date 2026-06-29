(defproject weasel "0.8.1-SNAPSHOT"
  :description "websocket REPL environment for ClojureScript"
  :url "http://github.com/nrepl/weasel"
  :license {:name "Unlicense"
            :url "http://unlicense.org/UNLICENSE"
            :distribution :repo}
  :scm {:name "git"
        :url "https://github.com/nrepl/weasel"}

  :dependencies [[org.clojure/clojure "1.12.5"]
                 [org.clojure/clojurescript "1.12.134"]
                 [http-kit "2.8.1"]]

  :deploy-repositories [["clojars" {:url "https://clojars.org/repo"
                                    :username :env/clojars_username
                                    :password :env/clojars_password
                                    :sign-releases false}]]

  :pom-addition [:developers [:developer
                              [:name "Tom Jakubowski"]
                              [:email "tom@crystae.net"]
                              [:url "https://github.com/tomjakubowski"]]]
  :source-paths ["src/clj" "src/cljs"]
  :test-paths ["test/clj"]
  :profiles {:dev {:dependencies [[cider/piggieback "0.6.0"]]
                   :plugins [[lein-cljsbuild "1.1.8"]]
                   :source-paths ["dev"]
                   :cljsbuild {:builds [{:id "smoke"
                                         :source-paths ["src/cljs"]
                                         :compiler {:output-to "target/weasel-smoke.js"
                                                    :optimizations :advanced}}
                                        {:id "node"
                                         :source-paths ["src/cljs" "test/cljs"]
                                         :compiler {:output-to "target/node/weasel_node_client.js"
                                                    :output-dir "target/node"
                                                    :target :nodejs
                                                    :main weasel.node-client
                                                    :optimizations :none}}]}}})
