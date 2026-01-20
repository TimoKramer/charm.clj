(ns charm.input.mouse-test
  (:require [clojure.test :refer [deftest is testing]]
            [charm.input.mouse :as m]))

(deftest parse-x10-button-test
  (testing "basic buttons"
    (is (= m/button-left (:button (m/parse-x10-button 0))))
    (is (= m/button-middle (:button (m/parse-x10-button 1))))
    (is (= m/button-right (:button (m/parse-x10-button 2))))
    (is (= m/button-release (:button (m/parse-x10-button 3)))))

  (testing "wheel buttons"
    (is (= m/button-wheel-up (:button (m/parse-x10-button 64))))
    (is (= m/button-wheel-down (:button (m/parse-x10-button 65)))))

  (testing "modifier detection"
    (let [result (m/parse-x10-button 4)]  ; shift
      (is (:shift result))
      (is (not (:alt result)))
      (is (not (:ctrl result))))
    (let [result (m/parse-x10-button 8)]  ; alt
      (is (not (:shift result)))
      (is (:alt result))
      (is (not (:ctrl result))))
    (let [result (m/parse-x10-button 16)] ; ctrl
      (is (not (:shift result)))
      (is (not (:alt result)))
      (is (:ctrl result))))

  (testing "motion detection"
    (is (:motion (m/parse-x10-button 32)))
    (is (not (:motion (m/parse-x10-button 0))))))

(deftest parse-x10-mouse-test
  (testing "left click at position"
    (let [result (m/parse-x10-mouse (+ 32 0) (+ 32 10) (+ 32 5))]
      (is (= :mouse (:type result)))
      (is (= m/button-left (:button result)))
      (is (= 10 (:x result)))
      (is (= 5 (:y result)))
      (is (= m/action-press (:action result)))))

  (testing "button release"
    (let [result (m/parse-x10-mouse (+ 32 3) (+ 32 10) (+ 32 5))]
      (is (= m/action-release (:action result)))
      (is (= m/button-none (:button result))))))

(deftest parse-sgr-mouse-test
  (testing "left click"
    (let [result (m/parse-sgr-mouse "\u001b[<0;15;10M")]
      (is (= :mouse (:type result)))
      (is (= m/button-left (:button result)))
      (is (= 15 (:x result)))
      (is (= 10 (:y result)))
      (is (= m/action-press (:action result)))))

  (testing "left release"
    (let [result (m/parse-sgr-mouse "\u001b[<0;15;10m")]
      (is (= m/action-release (:action result)))
      (is (= m/button-none (:button result)))))

  (testing "right click"
    (let [result (m/parse-sgr-mouse "\u001b[<2;20;25M")]
      (is (= m/button-right (:button result)))
      (is (= 20 (:x result)))
      (is (= 25 (:y result)))))

  (testing "wheel events"
    (let [result (m/parse-sgr-mouse "\u001b[<64;10;5M")]
      (is (= m/button-wheel-up (:button result)))))

  (testing "invalid input"
    (is (nil? (m/parse-sgr-mouse "not a mouse sequence")))
    (is (nil? (m/parse-sgr-mouse "")))))

(deftest mouse-event-predicates-test
  (let [click {:type :mouse :button m/button-left :action m/action-press :x 10 :y 5}
        release {:type :mouse :button m/button-none :action m/action-release :x 10 :y 5}
        motion {:type :mouse :button m/button-left :action m/action-motion :x 12 :y 5}
        wheel {:type :mouse :button m/button-wheel-up :action m/action-press :x 10 :y 5}
        right {:type :mouse :button m/button-right :action m/action-press :x 10 :y 5}
        middle {:type :mouse :button m/button-middle :action m/action-press :x 10 :y 5}
        key-event {:type :runes :runes "a"}]

    (testing "mouse-event?"
      (is (m/mouse-event? click))
      (is (not (m/mouse-event? key-event))))

    (testing "click?"
      (is (m/click? click))
      (is (not (m/click? release)))
      (is (not (m/click? motion))))

    (testing "release?"
      (is (m/release? release))
      (is (not (m/release? click))))

    (testing "motion?"
      (is (m/motion? motion))
      (is (not (m/motion? click))))

    (testing "wheel?"
      (is (m/wheel? wheel))
      (is (not (m/wheel? click))))

    (testing "left-click?"
      (is (m/left-click? click))
      (is (not (m/left-click? right))))

    (testing "right-click?"
      (is (m/right-click? right))
      (is (not (m/right-click? click))))

    (testing "middle-click?"
      (is (m/middle-click? middle))
      (is (not (m/middle-click? click))))))
