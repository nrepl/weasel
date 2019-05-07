(defproject weasel "0.7.1-SNAPSHOT"
  :description "websocket REPL environment for ClojureScript"
  :url "http://github.com/nrepl/weasel"
  :license {:name "Unlicense"
            :url "http://unlicense.org/UNLICENSE"
            :distribution :repo}

  :dependencies [[org.clojure/clojure "1.7.0-RC1"]
                 [org.clojure/clojurescript "0.0-3308"]
                 [org.clojure/google-closure-library "0.0-20140718-946a7d39" :scope "provided"]
                 [http-kit "2.1.18"]]

  :repositories [["releases" {:url "https://clojars.org/repo"
                              :creds :gpg}]]

  :pom-addition [:developers [:developer
                              [:name "Tom Jakubowski"]
                              [:email "tom@crystae.net"]
                              [:url "https://github.com/tomjakubowski"]]]
  :profiles {:dev {:dependencies [[cider/piggieback "0.4.0"]]}}
  :source-paths ["src/clj" "src/cljs"]
  :scm {:name "git"
        :url "https://github.com/nrepl/weasel"})
