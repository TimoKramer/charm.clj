(ns charm.style.border-test
  (:require [clojure.test :refer [deftest is testing]]
            [charm.style.border :as b]
            [clojure.string :as str]))

(deftest predefined-borders-test
  (testing "normal border has correct characters"
    (is (= "─" (:top b/normal)))
    (is (= "│" (:left b/normal)))
    (is (= "┌" (:top-left b/normal))))

  (testing "rounded border has rounded corners"
    (is (= "╭" (:top-left b/rounded)))
    (is (= "╯" (:bottom-right b/rounded))))

  (testing "double border has double lines"
    (is (= "═" (:top b/double-border)))
    (is (= "║" (:left b/double-border)))))

(deftest apply-border-test
  (testing "applies full border"
    (let [result (b/apply-border "hi" :border b/normal)
          lines (str/split-lines result)]
      (is (= 3 (count lines)))  ; top, content, bottom
      (is (str/includes? (first lines) "┌"))
      (is (str/includes? (first lines) "┐"))
      (is (str/includes? (last lines) "└"))
      (is (str/includes? (last lines) "┘"))
      (is (str/includes? (second lines) "│"))
      (is (str/includes? (second lines) "hi"))))

  (testing "applies rounded border"
    (let [result (b/apply-border "hi" :border b/rounded)
          lines (str/split-lines result)]
      (is (str/includes? (first lines) "╭"))
      (is (str/includes? (first lines) "╮"))))

  (testing "handles multiline content"
    (let [result (b/apply-border "a\nb" :border b/normal)
          lines (str/split-lines result)]
      (is (= 4 (count lines)))))  ; top, line1, line2, bottom

  (testing "can disable individual sides"
    (let [result (b/apply-border "hi" :border b/normal :top? false)
          lines (str/split-lines result)]
      (is (= 2 (count lines)))  ; content, bottom only
      (is (not (str/includes? (first lines) "┌"))))))

(deftest border-width-test
  (testing "calculates border width"
    (is (= 2 (b/border-width b/normal)))  ; │ on each side
    (is (= 2 (b/border-width b/rounded))))

  (testing "calculates partial border width"
    (is (= 1 (b/border-width b/normal :left? true :right? false)))
    (is (= 0 (b/border-width b/normal :left? false :right? false)))))

(deftest border-height-test
  (testing "calculates border height"
    (is (= 2 (b/border-height)))  ; top and bottom
    (is (= 1 (b/border-height :top? true :bottom? false)))
    (is (= 0 (b/border-height :top? false :bottom? false)))))
