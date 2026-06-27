(ns weasel.repl.websocket-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.edn :as edn]
            [weasel.repl.server :as server]
            [weasel.repl.websocket :as websocket]))

(defn- reset-eval-state! []
  (reset! @#'websocket/pending-eval nil))

(use-fixtures :each (fn [t] (reset-eval-state!) (t) (reset-eval-state!)))

(deftest ping-is-answered-with-pong
  (testing "a :ping is answered with a :pong sent back to the pinging client"
    (let [sent (atom nil)]
      (with-redefs [server/send-to! (fn [channel msg] (reset! sent {:channel channel :msg msg}))]
        (#'websocket/process-message :the-channel {:op :ping}))
      (is (= :the-channel (:channel @sent)) "pong goes to the client that pinged")
      (is (= {:op :pong} (edn/read-string (:msg @sent)))))))

(deftest result-correlation
  (testing "only the channel an eval was sent to may answer it"
    (let [response (promise)]
      (reset! @#'websocket/pending-eval {:channel :ch-a :promise response})
      (#'websocket/process-message :ch-b {:op :result :value "wrong"})
      (is (not (realized? response)) "a foreign client's result is ignored")
      (#'websocket/process-message :ch-a {:op :result :value "right"})
      (is (= "right" (deref response 100 ::timeout)) "the eval's client answers it"))))

(deftest disconnect-unblocks-pending-eval
  (testing "the eval's client disconnecting unblocks the pending eval"
    (let [response (promise)]
      (reset! @#'websocket/pending-eval {:channel :ch-a :promise response})
      (#'websocket/on-client-disconnect :ch-b)
      (is (not (realized? response)) "an unrelated disconnect leaves the eval pending")
      (#'websocket/on-client-disconnect :ch-a)
      (is (= :exception (:status (deref response 100 ::timeout)))
          "the eval reports an exception instead of hanging"))))

(deftest eval-to-closed-channel-errors
  (testing "evaluating against an already-closed client returns an error, not a hang"
    (with-redefs [server/active-channel (fn [] :closed-channel)
                  server/send-to! (fn [_ _] false)]
      (is (= :exception (:status (#'websocket/websocket-eval "1 + 1")))))))
