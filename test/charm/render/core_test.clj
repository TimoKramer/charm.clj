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
