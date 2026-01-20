(ns charm.input.keys-test
  (:require [clojure.test :refer [deftest is testing]]
            [charm.input.keys :as k]))

(deftest parse-ctrl-char-test
  (testing "common control characters"
    (is (= {:type :null} (k/parse-ctrl-char 0)))
    (is (= {:type :enter} (k/parse-ctrl-char 10)))
    (is (= {:type :enter} (k/parse-ctrl-char 13)))
    (is (= {:type :tab} (k/parse-ctrl-char 9)))
    (is (= {:type :escape} (k/parse-ctrl-char 27)))
    (is (= {:type :backspace} (k/parse-ctrl-char 8)))
    (is (= {:type :backspace} (k/parse-ctrl-char 127)))
    (is (= {:type :space} (k/parse-ctrl-char 32))))

  (testing "ctrl+letter combinations"
    (is (= {:type :runes :runes "c" :ctrl true} (k/parse-ctrl-char 3)))
    (is (= {:type :runes :runes "d" :ctrl true} (k/parse-ctrl-char 4)))
    (is (= {:type :runes :runes "z" :ctrl true} (k/parse-ctrl-char 26)))))

(deftest parse-escape-sequence-test
  (testing "arrow keys"
    (is (= {:type :up} (k/parse-escape-sequence "[A")))
    (is (= {:type :down} (k/parse-escape-sequence "[B")))
    (is (= {:type :right} (k/parse-escape-sequence "[C")))
    (is (= {:type :left} (k/parse-escape-sequence "[D")))
    ;; Application mode
    (is (= {:type :up} (k/parse-escape-sequence "OA")))
    (is (= {:type :down} (k/parse-escape-sequence "OB"))))

  (testing "arrow keys with modifiers"
    (is (= {:type :up :shift true} (k/parse-escape-sequence "[1;2A")))
    (is (= {:type :down :alt true} (k/parse-escape-sequence "[1;3B")))
    (is (= {:type :right :ctrl true} (k/parse-escape-sequence "[1;5C")))
    (is (= {:type :left :shift true :alt true} (k/parse-escape-sequence "[1;4D"))))

  (testing "navigation keys"
    (is (= {:type :home} (k/parse-escape-sequence "[H")))
    (is (= {:type :end} (k/parse-escape-sequence "[F")))
    (is (= {:type :insert} (k/parse-escape-sequence "[2~")))
    (is (= {:type :delete} (k/parse-escape-sequence "[3~")))
    (is (= {:type :page-up} (k/parse-escape-sequence "[5~")))
    (is (= {:type :page-down} (k/parse-escape-sequence "[6~"))))

  (testing "function keys"
    (is (= {:type :f1} (k/parse-escape-sequence "OP")))
    (is (= {:type :f2} (k/parse-escape-sequence "OQ")))
    (is (= {:type :f3} (k/parse-escape-sequence "OR")))
    (is (= {:type :f4} (k/parse-escape-sequence "OS")))
    (is (= {:type :f5} (k/parse-escape-sequence "[15~")))
    (is (= {:type :f12} (k/parse-escape-sequence "[24~"))))

  (testing "function keys with modifiers"
    (is (= {:type :f1 :shift true} (k/parse-escape-sequence "[1;2P")))
    (is (= {:type :f5 :ctrl true} (k/parse-escape-sequence "[15;5~"))))

  (testing "focus events"
    (is (= {:type :focus} (k/parse-escape-sequence "[I")))
    (is (= {:type :blur} (k/parse-escape-sequence "[O"))))

  (testing "unknown sequences"
    (let [result (k/parse-escape-sequence "[999X")]
      (is (= :unknown (:type result)))
      (is (= "[999X" (:sequence result))))))

(deftest ctrl-char?-test
  (testing "identifies control characters"
    (is (k/ctrl-char? 0))
    (is (k/ctrl-char? 31))
    (is (k/ctrl-char? 127))
    (is (not (k/ctrl-char? 32)))
    (is (not (k/ctrl-char? 65)))
    (is (not (k/ctrl-char? 128)))))

(deftest make-key-test
  (testing "creates key events"
    (is (= {:type :enter} (k/make-key {:type :enter})))
    (is (= {:type :runes :runes "a"} (k/make-key {:type :runes :runes "a"})))
    (is (= {:type :runes :runes "c" :ctrl true}
           (k/make-key {:type :runes :runes "c" :ctrl true})))
    (is (= {:type :up :shift true :alt true}
           (k/make-key {:type :up :shift true :alt true})))))

(deftest key-matches?-test
  (testing "matches by keyword"
    (is (k/key-matches? {:type :enter} :enter))
    (is (not (k/key-matches? {:type :tab} :enter))))

  (testing "matches by map"
    (is (k/key-matches? {:type :runes :runes "c" :ctrl true}
                        {:type :runes :ctrl true}))
    (is (not (k/key-matches? {:type :runes :runes "c"}
                             {:ctrl true}))))

  (testing "matches by string pattern"
    (is (k/key-matches? {:type :runes :runes "c" :ctrl true} "ctrl+c"))
    (is (k/key-matches? {:type :runes :runes "x" :alt true} "alt+x"))
    (is (k/key-matches? {:type :enter} "enter"))
    (is (k/key-matches? {:type :up :shift true} "shift+up"))
    (is (not (k/key-matches? {:type :runes :runes "c"} "ctrl+c")))))
