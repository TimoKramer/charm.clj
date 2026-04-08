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
    (is (not (msg/mouse? (msg/quit)))))

  (testing "click?"
    (is (msg/click? (msg/mouse :press :left 10 20)))
    (is (not (msg/click? (msg/mouse :release :none 10 20))))
    (is (not (msg/click? (msg/quit)))))

  (testing "release?"
    (is (msg/release? (msg/mouse :release :none 10 20)))
    (is (not (msg/release? (msg/mouse :press :left 10 20)))))

  (testing "motion?"
    (is (msg/motion? (msg/mouse :motion :left 10 20)))
    (is (not (msg/motion? (msg/mouse :press :left 10 20)))))

  (testing "left-click?"
    (is (msg/left-click? (msg/mouse :press :left 10 20)))
    (is (not (msg/left-click? (msg/mouse :press :right 10 20)))))

  (testing "right-click?"
    (is (msg/right-click? (msg/mouse :press :right 10 20)))
    (is (not (msg/right-click? (msg/mouse :press :left 10 20)))))

  (testing "middle-click?"
    (is (msg/middle-click? (msg/mouse :press :middle 10 20)))
    (is (not (msg/middle-click? (msg/mouse :press :left 10 20)))))

  (testing "wheel-up?"
    (is (msg/wheel-up? (msg/mouse :wheel-up :none 10 20)))
    (is (not (msg/wheel-up? (msg/mouse :wheel-down :none 10 20)))))

  (testing "wheel-down?"
    (is (msg/wheel-down? (msg/mouse :wheel-down :none 10 20)))
    (is (not (msg/wheel-down? (msg/mouse :press :left 10 20)))))

  (testing "wheel?"
    (is (msg/wheel? (msg/mouse :wheel-up :none 10 20)))
    (is (msg/wheel? (msg/mouse :wheel-down :none 10 20)))
    (is (not (msg/wheel? (msg/mouse :press :left 10 20))))))
