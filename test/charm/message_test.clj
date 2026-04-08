(ns charm.message-test
  (:require [clojure.test :refer [deftest is testing]]
            [charm.message :as msg]))

(deftest key-press-test
  (testing "key-press creates correct message"
    (let [m (msg/key-press "a")]
      (is (= :key-press (:type m)))
      (is (= "a" (:key m)))
      (is (false? (:alt m)))
      (is (false? (:ctrl m)))
      (is (false? (:shift m)))))

  (testing "key-press with modifiers"
    (let [m (msg/key-press "c" :ctrl true)]
      (is (= "c" (:key m)))
      (is (true? (:ctrl m)))
      (is (false? (:alt m))))))

(deftest window-size-test
  (testing "window-size creates correct message"
    (let [m (msg/window-size 80 24)]
      (is (= :window-size (:type m)))
      (is (= 80 (:width m)))
      (is (= 24 (:height m))))))

(deftest quit-test
  (testing "quit creates correct message"
    (let [m (msg/quit)]
      (is (= :quit (:type m))))))

(deftest predicates-test
  (testing "key-press?"
    (is (msg/key-press? (msg/key-press "a")))
    (is (not (msg/key-press? (msg/quit)))))

  (testing "window-size?"
    (is (msg/window-size? (msg/window-size 80 24)))
    (is (not (msg/window-size? (msg/quit)))))

  (testing "quit?"
    (is (msg/quit? (msg/quit)))
    (is (not (msg/quit? (msg/key-press "q"))))))

(deftest key-match-test
  (testing "key-match? with string"
    (is (msg/key-match? (msg/key-press "q") "q"))
    (is (not (msg/key-match? (msg/key-press "a") "q"))))

  (testing "key-match? with keyword"
    (is (msg/key-match? (msg/key-press "q") :q))
    (is (not (msg/key-match? (msg/key-press "a") :q))))

  (testing "key-match? with non-key-press"
    (is (not (msg/key-match? (msg/quit) "q")))))

(deftest modifier-predicates-test
  (testing "ctrl?"
    (is (msg/ctrl? (msg/key-press "c" :ctrl true)))
    (is (not (msg/ctrl? (msg/key-press "c")))))

  (testing "alt?"
    (is (msg/alt? (msg/key-press "a" :alt true)))
    (is (not (msg/alt? (msg/key-press "a")))))

  (testing "shift?"
    (is (msg/shift? (msg/key-press "A" :shift true)))
    (is (not (msg/shift? (msg/key-press "a"))))))

(deftest mouse-test
  (testing "mouse creates correct message"
    (let [m (msg/mouse :press :left 10 20)]
      (is (= :mouse (:type m)))
      (is (= :press (:action m)))
      (is (= :left (:button m)))
      (is (= 10 (:x m)))
      (is (= 20 (:y m)))))

  (testing "mouse?"
    (is (msg/mouse? (msg/mouse :press :left 0 0)))
    (is (not (msg/mouse? (msg/quit))))))
