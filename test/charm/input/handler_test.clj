(ns charm.input.handler-test
  (:require [clojure.test :refer [deftest is testing]]
            [charm.input.handler :as h]))

(deftest parse-input-test
  (testing "control characters"
    (is (= {:type :null} (h/parse-input 0)))
    (is (= {:type :enter} (h/parse-input 10)))
    (is (= {:type :tab} (h/parse-input 9)))
    (is (= {:type :escape} (h/parse-input 27)))
    (is (= {:type :backspace} (h/parse-input 127))))

  (testing "printable characters"
    (is (= {:type :runes :runes "a"} (h/parse-input 97)))
    (is (= {:type :runes :runes "A"} (h/parse-input 65)))
    (is (= {:type :runes :runes "1"} (h/parse-input 49)))
    (is (= {:type :runes :runes "!"} (h/parse-input 33)))))

(deftest parse-input-with-escape-test
  (testing "just ESC"
    (is (= {:type :escape} (h/parse-input 27 nil))))

  (testing "arrow keys"
    (is (= {:type :up} (h/parse-input 27 "[A")))
    (is (= {:type :down} (h/parse-input 27 "[B")))
    (is (= {:type :right} (h/parse-input 27 "[C")))
    (is (= {:type :left} (h/parse-input 27 "[D"))))

  (testing "alt+key"
    (is (= {:type :runes :runes "a" :alt true} (h/parse-input 27 "a")))
    (is (= {:type :runes :runes "x" :alt true} (h/parse-input 27 "x"))))

  (testing "function keys"
    (is (= {:type :f1} (h/parse-input 27 "OP")))
    (is (= {:type :f5} (h/parse-input 27 "[15~"))))

  (testing "navigation keys"
    (is (= {:type :home} (h/parse-input 27 "[H")))
    (is (= {:type :end} (h/parse-input 27 "[F")))
    (is (= {:type :page-up} (h/parse-input 27 "[5~")))
    (is (= {:type :page-down} (h/parse-input 27 "[6~"))))

  (testing "modifiers on arrows"
    (is (= {:type :up :shift true} (h/parse-input 27 "[1;2A")))
    (is (= {:type :down :ctrl true} (h/parse-input 27 "[1;5B")))))

(deftest parse-input-mouse-test
  (testing "SGR mouse click"
    (let [result (h/parse-input 27 "[<0;10;5M")]
      (is (= :mouse (:type result)))
      (is (= 10 (:x result)))
      (is (= 5 (:y result)))))

  (testing "SGR mouse release"
    (let [result (h/parse-input 27 "[<0;10;5m")]
      (is (= :mouse (:type result)))
      (is (= :release (:action result))))))
