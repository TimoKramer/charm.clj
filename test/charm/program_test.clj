(ns charm.program-test
  (:require [clojure.test :refer [deftest is testing]]
            [charm.program :as p]
            [charm.message :as msg]
            [charm.render.core :as render]
            [charm.style.color :as color]))

;; Note: Full program tests require terminal interaction.
;; These tests focus on command and message helper functions.

(deftest cmd-test
  (testing "creates command from function"
    (let [c (p/cmd (fn [] (msg/quit)))]
      (is (= :cmd (:type c)))
      (is (fn? (:fn c))))))

(deftest batch-test
  (testing "combines multiple commands"
    (let [c1 (p/cmd (fn [] :a))
          c2 (p/cmd (fn [] :b))
          batch (p/batch c1 c2)]
      (is (= :batch (:type batch)))
      (is (= 2 (count (:cmds batch))))))

  (testing "filters nil commands"
    (let [c1 (p/cmd (fn [] :a))
          batch (p/batch c1 nil nil)]
      (is (= 1 (count (:cmds batch)))))))

(deftest sequence-cmds-test
  (testing "creates sequence of commands"
    (let [c1 (p/cmd (fn [] :a))
          c2 (p/cmd (fn [] :b))
          seq (p/sequence-cmds c1 c2)]
      (is (= :sequence (:type seq)))
      (is (= 2 (count (:cmds seq)))))))

(deftest quit-cmd-test
  (testing "quit-cmd is a command"
    (is (= :cmd (:type p/quit-cmd)))
    (is (fn? (:fn p/quit-cmd))))

  (testing "quit-cmd produces quit message"
    (let [result ((:fn p/quit-cmd))]
      (is (msg/quit? result)))))

(deftest window-size-msg-test
  (testing "creates window size message"
    (let [m (p/window-size-msg 80 24)]
      (is (= :window-size (:type m)))
      (is (= 80 (:width m)))
      (is (= 24 (:height m))))))

(deftest resolve-color-profile-test
  (let [resolve-profile #'p/resolve-color-profile]
    (testing "a pinned profile is used as given"
      (doseq [profile color/color-profiles]
        (is (= profile (resolve-profile profile)))))

    (testing "nil detects from the environment"
      (is (contains? color/color-profiles (resolve-profile nil))))

    (testing "an unknown profile throws, naming the value"
      (let [e (is (thrown? clojure.lang.ExceptionInfo (resolve-profile :256color)))]
        (is (= :256color (:color-profile (ex-data e))))))))

(deftest handle-msg-test
  (let [handle-msg! #'p/handle-msg!
        renders (atom 0)
        run! (fn [update state m & opts]
               (let [state-atom (atom state)]
                 (apply handle-msg! ::renderer update identity state-atom nil m opts)
                 @state-atom))]
    (with-redefs [render/render! (fn [_ _] (swap! renders inc))]
      (testing "renders when update returns a new state"
        (reset! renders 0)
        (is (= {:n 1} (run! (fn [st _] [(update st :n inc) nil]) {:n 0} (msg/key-press "j"))))
        (is (= 1 @renders)))

      (testing "skips the render when update returns the state unchanged"
        (reset! renders 0)
        (is (= {:n 0} (run! (fn [st _] [st nil]) {:n 0} (msg/key-press "x"))))
        (is (zero? @renders)))

      (testing "an equal but not identical state still renders"
        (reset! renders 0)
        (run! (fn [st _] [(into {} st) nil]) {:n 0} (msg/key-press "x"))
        (is (= 1 @renders)))

      (testing "force-render? renders even when the state is unchanged"
        (reset! renders 0)
        (run! (fn [st _] [st nil]) {:n 0} (p/window-size-msg 80 24) :force-render? true)
        (is (= 1 @renders))))))
