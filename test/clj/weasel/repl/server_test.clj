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

(deftest origin-allowlist
  (testing "the default :local policy accepts local origins and missing origins"
    (let [allow? (#'server/->origin-allowed-fn :local)]
      (is (allow? "http://localhost:8080"))
      (is (allow? "http://127.0.0.1:3000"))
      (is (allow? "https://localhost"))
      (is (allow? "http://[::1]:9000"))
      (is (allow? nil) "non-browser clients send no Origin header")
      (is (not (allow? "http://evil.example.com")))
      (is (not (allow? "http://localhost.evil.com")) "no suffix-match bypass")
      (is (not (allow? "http://notlocalhost")) "no prefix-match bypass")))
  (testing "nil is treated the same as :local"
    (is ((#'server/->origin-allowed-fn nil) "http://localhost")))
  (testing ":all disables the check"
    (is ((#'server/->origin-allowed-fn :all) "http://evil.example.com")))
  (testing "an explicit allowlist accepts only its members (plus missing origins)"
    (let [allow? (#'server/->origin-allowed-fn ["https://app.example.com"])]
      (is (allow? "https://app.example.com"))
      (is (allow? nil))
      (is (not (allow? "https://evil.example.com")))))
  (testing "a bare origin string is treated as a one-element allowlist"
    (let [allow? (#'server/->origin-allowed-fn "https://app.example.com")]
      (is (allow? "https://app.example.com"))
      (is (allow? nil))
      (is (not (allow? "https://evil.example.com")))))
  (testing "a custom predicate gets full control, including over a missing origin"
    (let [allow? (#'server/->origin-allowed-fn (fn [o] (= o "app://prod")))]
      (is (allow? "app://prod"))
      (is (not (allow? nil)))))
  (testing "an unusable value is rejected loudly"
    (is (thrown? IllegalArgumentException (#'server/->origin-allowed-fn 42)))))

(deftest handler-rejects-disallowed-origin
  (testing "a cross-origin websocket handshake is refused with 403"
    (server/start (fn [& _]) :ip "127.0.0.1" :port 0)
    (try
      (is (= 403 (:status (server/handler {:websocket? true
                                           :headers {"origin" "http://evil.example.com"}}))))
      (finally (server/stop)))))

(deftest start-validates-allowed-origins-before-binding
  (testing "a bad :allowed-origins throws without leaving a server running"
    (is (thrown? IllegalArgumentException
          (server/start (fn [& _]) :ip "127.0.0.1" :port 0 :allowed-origins 42)))
    (is (nil? (:server @server/state)) "no server was bound")))

(deftest stale-disconnect-after-stop-leaves-server-stopped
  (testing "a late client close after stop does not re-arm the readiness promise"
    (server/start (fn [& _]) :ip "127.0.0.1" :port 0)
    (#'server/add-client! :client-a)
    (server/stop)
    (#'server/remove-client! :client-a) ; the channel's on-close firing late
    (is (nil? (:ready @server/state)) "the server stays stopped")
    (is (thrown? java.io.IOException (server/active-channel)))))
