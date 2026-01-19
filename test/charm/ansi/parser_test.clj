(ns charm.ansi.parser-test
  (:require [clojure.test :refer [deftest is testing]]
            [charm.ansi.parser :as p]))

(deftest parse-sgr-test
  (testing "simple SGR"
    (let [result (p/parse-sgr "\033[31m")]
      (is (= :sgr (:type result)))
      (is (= [31] (:params result)))))

  (testing "multiple params"
    (let [result (p/parse-sgr "\033[1;31;47m")]
      (is (= [1 31 47] (:params result)))))

  (testing "reset (empty params)"
    (let [result (p/parse-sgr "\033[m")]
      (is (= [0] (:params result)))))

  (testing "non-SGR returns nil"
    (is (nil? (p/parse-sgr "plain text")))))

(deftest parse-csi-test
  (testing "cursor movement"
    (let [result (p/parse-csi "\033[5A")]
      (is (= :csi (:type result)))
      (is (= [5] (:params result)))
      (is (= \A (:final result)))))

  (testing "clear screen"
    (let [result (p/parse-csi "\033[2J")]
      (is (= [2] (:params result)))
      (is (= \J (:final result))))))

(deftest split-ansi-test
  (testing "plain text only"
    (let [result (p/split-ansi "hello world")]
      (is (= 1 (count result)))
      (is (= :text (:type (first result))))
      (is (= "hello world" (:content (first result))))))

  (testing "text with ANSI"
    (let [result (p/split-ansi "Hello \033[31mred\033[0m world")]
      (is (= 5 (count result)))
      (is (= :text (:type (first result))))
      (is (= "Hello " (:content (first result))))
      (is (= :ansi (:type (second result))))
      (is (= :text (:type (nth result 2))))
      (is (= "red" (:content (nth result 2))))))

  (testing "ANSI at start"
    (let [result (p/split-ansi "\033[1mbold")]
      (is (= :ansi (:type (first result))))
      (is (= :text (:type (second result)))))))

(deftest sgr-generation-test
  (testing "generate with keywords"
    (is (= "\033[1;31m" (p/sgr :bold :fg-red))))

  (testing "generate with numbers"
    (is (= "\033[1;31m" (p/sgr 1 31))))

  (testing "reset"
    (is (= "\033[0m" (p/reset-style)))))

(deftest extract-sequences-test
  (testing "extracts multiple sequences"
    (let [result (p/extract-sequences "\033[1mbold\033[0m and \033[31mred\033[0m")]
      (is (= 4 (count result)))
      (is (every? #(contains? % :start) result))
      (is (every? #(contains? % :parsed) result)))))
