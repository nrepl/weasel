(defproject weasel "0.7.1"
  :description "websocket REPL environment for ClojureScript"
  :url "http://github.com/nrepl/weasel"
  :license {:name "Unlicense"
            :url "http://unlicense.org/UNLICENSE"
            :distribution :repo}
  :scm {:name "git"
        :url "https://github.com/nrepl/weasel"}

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.520"]
                 [http-kit "2.3.0"]]

  :deploy-repositories [["clojars" {:url "https://clojars.org/repo"
                                    :username :env/clojars_username
                                    :password :env/clojars_password
                                    :sign-releases false}]]

  :pom-addition [:developers [:developer
                              [:name "Tom Jakubowski"]
                              [:email "tom@crystae.net"]
                              [:url "https://github.com/tomjakubowski"]]]
  :profiles {:dev {:dependencies [[cider/piggieback "0.4.2"]]}}
  :source-paths ["src/clj" "src/cljs"])
