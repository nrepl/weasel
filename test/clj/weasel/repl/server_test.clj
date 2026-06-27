(ns weasel.repl.server-test
  (:require [clojure.test :refer [deftest is testing]]
            [weasel.repl.server :as server]))

(deftest start-and-stop
  (testing "starting the server populates the shared state"
    (try
      (server/start (fn [& _]) :ip "127.0.0.1" :port 0)
      (is (some? (:server @server/state)) "a stop fn is stored")
      (is (instance? clojure.lang.IPending (:ready @server/state))
          "a pending client promise is stored")
      (finally
        (server/stop))))

  (testing "stopping the server clears the shared state"
    (is (nil? (:server @server/state)))
    (is (nil? (:ready @server/state)))
    (is (nil? (:response-fn @server/state)))
    (is (empty? (:clients @server/state)))))

(deftest send-without-server-throws
  (testing "sending with no running server raises an IOException"
    (server/stop)
    (is (thrown? java.io.IOException (server/send! "anything")))))

(deftest most-recent-client-wins
  (testing "the active channel is the most recently connected client"
    (server/start (fn [& _]) :ip "127.0.0.1" :port 0)
    (try
      (#'server/add-client! :client-a)
      (is (= :client-a (server/active-channel)))
      (#'server/add-client! :client-b)
      (is (= :client-b (server/active-channel)) "newest client takes over")
      (#'server/remove-client! :client-b)
      (is (= :client-a (server/active-channel)) "falls back to the remaining client")
      (finally
        (server/stop)))))

(deftest stop-wakes-blocked-waiter
  (testing "stopping the server unblocks a thread waiting for a client"
    (server/start (fn [& _]) :ip "127.0.0.1" :port 0)
    (let [waiter (future (server/wait-for-client) :woke)]
      (Thread/sleep 100)
      (is (not (realized? waiter)) "blocks while no client is connected")
      (server/stop)
      (is (= :woke (deref waiter 1000 ::timeout)) "stop wakes the waiter"))))

(deftest stale-disconnect-after-stop-leaves-server-stopped
  (testing "a late client close after stop does not re-arm the readiness promise"
    (server/start (fn [& _]) :ip "127.0.0.1" :port 0)
    (#'server/add-client! :client-a)
    (server/stop)
    (#'server/remove-client! :client-a) ; the channel's on-close firing late
    (is (nil? (:ready @server/state)) "the server stays stopped")
    (is (thrown? java.io.IOException (server/active-channel)))))
