(ns weasel.repl.websocket-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [weasel.repl.server :as server]
            [weasel.repl.websocket :as websocket]))

(deftest ping-is-answered-with-pong
  (testing "a :ping message makes the server send a :pong back"
    (let [sent (atom nil)]
      (with-redefs [server/send! (fn [msg] (reset! sent msg))]
        (#'websocket/process-message ::ignored {:op :ping}))
      (is (= {:op :pong} (edn/read-string @sent))))))
