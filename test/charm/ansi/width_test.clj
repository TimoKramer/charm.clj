(ns charm.ansi.width-test
  (:require [clojure.test :refer [deftest is testing]]
            [charm.ansi.width :as w]))

(deftest strip-ansi-test
  (testing "removes CSI sequences"
    (is (= "red" (w/strip-ansi "\033[31mred\033[0m")))
    (is (= "text" (w/strip-ansi "\033[1;31;47mtext\033[0m"))))

  (testing "handles empty/nil input"
    (is (= "" (w/strip-ansi "")))
    (is (= "" (w/strip-ansi nil))))

  (testing "preserves plain text"
    (is (= "hello world" (w/strip-ansi "hello world")))))

(deftest string-width-test
  (testing "basic ASCII"
    (is (= 5 (w/string-width "hello")))
    (is (= 0 (w/string-width "")))
    (is (= 0 (w/string-width nil))))

  (testing "wide characters (CJK)"
    (is (= 4 (w/string-width "你好")))    ; 2 chars * 2 width
    (is (= 6 (w/string-width "日本語"))))  ; 3 chars * 2 width

  (testing "mixed content"
    (is (= 7 (w/string-width "Hi 你好"))))  ; 3 + 4

  (testing "ANSI sequences ignored"
    (is (= 3 (w/string-width "\033[31mred\033[0m")))
    (is (= 4 (w/string-width "\033[1mbold\033[0m")))))

(deftest truncate-test
  (testing "no truncation needed"
    (is (= "hello" (w/truncate "hello" 10)))
    (is (= "hi" (w/truncate "hi" 5))))

  (testing "truncation with default tail"
    (is (= "hello..." (w/truncate "hello world" 8))))

  (testing "truncation with custom tail"
    (is (= "hello…" (w/truncate "hello world" 6 :tail "…"))))

  (testing "wide characters"
    (is (= "你..." (w/truncate "你好世界" 5)))  ; 你(2) + ...(3) = 5
    (is (= "..." (w/truncate "你好" 3))))       ; only tail fits

  (testing "nil/empty handling"
    (is (nil? (w/truncate nil 10)))
    (is (= "" (w/truncate "" 10)))))

(deftest pad-test
  (testing "pad-right"
    (is (= "hi   " (w/pad-right "hi" 5)))
    (is (= "hello" (w/pad-right "hello" 3))))  ; no padding if already wide enough

  (testing "pad-left"
    (is (= "   hi" (w/pad-left "hi" 5)))
    (is (= "hello" (w/pad-left "hello" 3)))))
