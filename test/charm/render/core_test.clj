(ns charm.render.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [charm.render.core :as r]
            [charm.terminal :as term]))

;; Note: Most renderer tests require a real terminal.
;; These tests focus on the logic that can be tested without terminal I/O.

(deftest create-renderer-test
  (testing "creates renderer with defaults"
    (let [terminal (term/create-terminal)
          renderer (r/create-renderer terminal)]
      (try
        (is (some? @renderer))
        (is (= 60 (:fps @renderer)))
        (is (false? (:alt-screen @renderer)))
        (is (false? (:in-alt-screen @renderer)))
        (is (true? (:hide-cursor @renderer)))
        (is (nat-int? (:width @renderer)))
        (is (nat-int? (:height @renderer)))
        (finally
          (term/close terminal)))))

  (testing "creates renderer with options"
    (let [terminal (term/create-terminal)
          renderer (r/create-renderer terminal :fps 30 :alt-screen true)]
      (try
        (is (= 30 (:fps @renderer)))
        (is (true? (:alt-screen @renderer)))
        (is (false? (:in-alt-screen @renderer)))
        (finally
          (term/close terminal))))))

(deftest update-size-test
  (testing "updates renderer size"
    (let [terminal (term/create-terminal)
          renderer (r/create-renderer terminal)]
      (try
        (r/update-size! renderer 100 50)
        (is (= [100 50] (r/get-size renderer)))
        (finally
          (term/close terminal))))))

(deftest get-size-test
  (testing "returns current size"
    (let [terminal (term/create-terminal)
          renderer (r/create-renderer terminal)]
      (try
        (let [[w h] (r/get-size renderer)]
          (is (nat-int? w))
          (is (nat-int? h)))
        (finally
          (term/close terminal))))))

(deftest alt-screen-lifecycle-test
  (testing "start!/stop! use configured alt-screen and track active state separately"
    (let [calls (atom [])
          renderer (atom {:terminal :fake
                          :alt-screen true
                          :in-alt-screen false
                          :hide-cursor false
                          :running false})]
      (with-redefs [term/enter-alt-screen (fn [_] (swap! calls conj :enter))
                    term/clear-screen (fn [_] (swap! calls conj :clear))
                    term/cursor-home (fn [_] (swap! calls conj :home))
                    term/exit-alt-screen (fn [_] (swap! calls conj :exit))
                    r/disable-mouse! (fn [_] (swap! calls conj :disable-mouse))
                    r/disable-focus-reporting! (fn [_] (swap! calls conj :disable-focus))
                    r/show-cursor! (fn [_] (swap! calls conj :show-cursor))]
        ;; Stop before start should not send exit alt-screen.
        (r/stop! renderer)
        (is (= [:disable-mouse :disable-focus] @calls))
        (reset! calls [])

        (r/start! renderer)
        (is (= [:enter :clear :home] @calls))
        (is (true? (:in-alt-screen @renderer)))

        ;; Enter should be idempotent once active.
        (r/start! renderer)
        (is (= [:enter :clear :home] @calls))

        (r/stop! renderer)
        (is (= [:enter :clear :home :exit :disable-mouse :disable-focus] @calls))
        (is (false? (:in-alt-screen @renderer)))))))
