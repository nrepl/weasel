(ns weasel.repl.server-test
  (:require [clojure.test :refer [deftest is testing]]
            [weasel.repl.server :as server]))

(deftest start-and-stop
  (testing "starting the server populates the shared state"
    (try
      (server/start (fn [_]) :ip "127.0.0.1" :port 0)
      (is (some? (:server @server/state)) "a stop fn is stored")
      (is (instance? clojure.lang.IPending (:channel @server/state))
          "a pending client promise is stored")
      (finally
        (server/stop))))

  (testing "stopping the server clears the shared state"
    (is (nil? (:server @server/state)))
    (is (nil? (:channel @server/state)))
    (is (nil? (:response-fn @server/state)))))

(deftest send-without-server-throws
  (testing "sending with no running server raises an IOException"
    (server/stop)
    (is (thrown? java.io.IOException (server/send! "anything")))))
