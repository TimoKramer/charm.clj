(ns charm.style.overlay-test
  (:require
   [charm.style.overlay :as overlay]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]))

(deftest place-overlay-test
  (testing "basic overlay placement"
    (let [base "aaaaaa\nbbbbbb\ncccccc\ndddddd"
          over "XX\nYY"
          result (overlay/place-overlay base over 2 1)]
      (is (= "aaaaaa\nbbXXbb\nccYYcc\ndddddd" result))))

  (testing "overlay at origin"
    (let [base "aaa\nbbb\nccc"
          over "X\nY"
          result (overlay/place-overlay base over 0 0)]
      (is (= "Xaa\nYbb\nccc" result))))

  (testing "overlay at end of line"
    (let [base "aaaa\nbbbb\ncccc"
          over "XX\nYY"
          result (overlay/place-overlay base over 2 0)]
      (is (= "aaXX\nbbYY\ncccc" result))))

  (testing "overlay extends beyond base width"
    (let [base "aaa\nbbb"
          over "XXXXX"
          result (overlay/place-overlay base over 1 0)]
      ;; Overlay replaces from x=1 and extends beyond
      (is (str/starts-with? result "aXXXXX"))))

  (testing "overlay with empty base"
    (let [result (overlay/place-overlay "" "X" 0 0)]
      ;; Single line base
      (is (= "X" result)))))

(deftest center-overlay-test
  (testing "centered overlay"
    (let [base "...........\n...........\n...........\n...........\n..........."
          over "XXX\nYYY"
          result (overlay/center-overlay base over)
          lines (str/split-lines result)]
      ;; Base is 11 wide, 5 tall. Overlay is 3 wide, 2 tall.
      ;; x = (11-3)/2 = 4, y = (5-2)/2 = 1
      (is (= "..........." (nth lines 0)))
      (is (str/includes? (nth lines 1) "XXX"))
      (is (str/includes? (nth lines 2) "YYY"))
      (is (= "..........." (nth lines 3)))
      (is (= "..........." (nth lines 4))))))

(deftest place-overlay-position-test
  (testing "top-left"
    (let [base ".....\n.....\n....."
          over "XX"
          result (overlay/place-overlay-position base over :top-left)
          lines (str/split-lines result)]
      (is (str/starts-with? (first lines) "XX"))))

  (testing "top-right"
    (let [base ".....\n.....\n....."
          over "XX"
          result (overlay/place-overlay-position base over :top-right)
          lines (str/split-lines result)]
      (is (str/ends-with? (first lines) "XX"))))

  (testing "bottom-left"
    (let [base ".....\n.....\n....."
          over "XX"
          result (overlay/place-overlay-position base over :bottom-left)
          lines (str/split-lines result)]
      (is (str/starts-with? (last lines) "XX"))))

  (testing "bottom-right"
    (let [base ".....\n.....\n....."
          over "XX"
          result (overlay/place-overlay-position base over :bottom-right)
          lines (str/split-lines result)]
      (is (str/ends-with? (last lines) "XX"))))

  (testing "center position"
    (let [base ".........\n.........\n.........\n.........\n........."
          over "XXX"
          result (overlay/place-overlay-position base over :center)
          lines (str/split-lines result)]
      ;; x=(9-3)/2=3, y=(5-1)/2=2
      (is (str/includes? (nth lines 2) "XXX")))))

(deftest composited-lines-keep-their-characters-test
  (testing "compositing does not rewrite box-drawing characters"
    ;; Every composited line goes through an AttributedStringBuilder, whose
    ;; toAnsi used to turn the base's │ and ─ into | and -
    (let [base (str/join "\n" (repeat 4 "│    ────    │"))
          panel "╭─╮\n│x│\n╰─╯"
          out (overlay/place-overlay base panel 3 1)]
      (is (str/includes? out "│"))
      (is (str/includes? out "─"))
      (is (str/includes? out "╭"))
      (is (not (str/includes? out "|")))
      (is (not (str/includes? out "-"))))))
