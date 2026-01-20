(ns charm.style.layout-test
  (:require [clojure.test :refer [deftest is testing]]
            [charm.style.layout :as l]
            [clojure.string :as str]))

(deftest expand-box-values-test
  (testing "expands 1 value to all sides"
    (is (= [5 5 5 5] (l/expand-box-values [5]))))

  (testing "expands 2 values to vertical/horizontal"
    (is (= [1 2 1 2] (l/expand-box-values [1 2]))))

  (testing "expands 3 values (top, horizontal, bottom)"
    (is (= [1 2 3 2] (l/expand-box-values [1 2 3]))))

  (testing "keeps 4 values as-is"
    (is (= [1 2 3 4] (l/expand-box-values [1 2 3 4])))))

(deftest pad-test
  (testing "adds padding to text"
    (let [result (l/pad "hi" 1 2 1 2)]
      (is (str/includes? result "  hi  "))  ; left and right padding
      (is (= 3 (count (str/split-lines result))))))  ; top, content, bottom

  (testing "adds only horizontal padding"
    (let [result (l/pad "hi" 0 2 0 2)]
      (is (str/includes? result "  hi  "))
      (is (= 1 (count (str/split-lines result))))))

  (testing "handles multiline content"
    (let [result (l/pad "a\nb" 1 1 1 1)]
      (is (= 4 (count (str/split-lines result)))))))

(deftest align-horizontal-test
  (testing "left alignment"
    (let [result (l/align-horizontal "hi" 5 :left)]
      (is (str/starts-with? result "hi"))))

  (testing "right alignment"
    (let [result (l/align-horizontal "hi" 5 :right)]
      (is (str/ends-with? result "hi"))))

  (testing "center alignment"
    (let [result (l/align-horizontal "hi" 6 :center)]
      ;; "hi" is 2 chars, 6 total width = 2 padding each side
      (is (str/starts-with? result "  "))
      (is (str/includes? result "hi"))))

  (testing "handles multiline text"
    (let [result (l/align-horizontal "a\nbb" 4 :right)
          lines (str/split-lines result)]
      (is (str/ends-with? (first lines) "a"))
      (is (str/ends-with? (second lines) "bb")))))

(deftest align-vertical-test
  (testing "top alignment"
    (let [result (l/align-vertical "hi" 3 :top)]
      (is (str/starts-with? result "hi"))
      ;; Count newlines to verify padding
      (is (= 2 (count (filter #(= % \newline) result))))))

  (testing "bottom alignment"
    (let [result (l/align-vertical "hi" 3 :bottom)]
      (is (str/ends-with? result "hi"))
      (is (= 2 (count (filter #(= % \newline) result))))))

  (testing "center alignment"
    (let [result (l/align-vertical "hi" 3 :center)]
      ;; Should have empty line before "hi"
      (is (str/starts-with? result "\n"))
      (is (str/includes? result "hi")))))

(deftest join-horizontal-test
  (testing "joins text blocks horizontally"
    (let [result (l/join-horizontal :top "a" "b")]
      (is (= "ab" result))))

  (testing "aligns blocks of different heights"
    (let [result (l/join-horizontal :top "a\nb" "c")
          lines (str/split-lines result)]
      (is (= 2 (count lines)))
      (is (str/includes? (first lines) "c"))))

  (testing "bottom alignment"
    (let [result (l/join-horizontal :bottom "a" "b\nc")
          lines (str/split-lines result)]
      (is (= 2 (count lines)))
      (is (str/includes? (last lines) "a")))))

(deftest join-vertical-test
  (testing "joins text blocks vertically"
    (let [result (l/join-vertical :left "a" "b")
          lines (str/split-lines result)]
      (is (= 2 (count lines)))
      (is (= "a" (first lines)))
      (is (= "b" (second lines)))))

  (testing "aligns blocks of different widths"
    (let [result (l/join-vertical :right "a" "bb")
          lines (str/split-lines result)]
      (is (str/ends-with? (first lines) "a"))
      (is (str/ends-with? (second lines) "bb"))))

  (testing "center alignment"
    (let [result (l/join-vertical :center "a" "bbb")
          lines (str/split-lines result)]
      ;; "a" should be centered within width 3
      (is (= 3 (count (first lines)))))))
