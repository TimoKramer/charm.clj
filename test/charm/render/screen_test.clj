(ns charm.render.screen-test
  (:require [clojure.test :refer [deftest is testing]]
            [charm.render.screen :as scr]))

(deftest mouse-control-test
  (testing "mouse control sequences"
    (is (= "\u001b[?1000h" scr/enable-mouse-normal))
    (is (= "\u001b[?1000l" scr/disable-mouse-normal))
    (is (= "\u001b[?1002h" scr/enable-mouse-cell-motion))
    (is (= "\u001b[?1002l" scr/disable-mouse-cell-motion))
    (is (= "\u001b[?1003h" scr/enable-mouse-all-motion))
    (is (= "\u001b[?1003l" scr/disable-mouse-all-motion))
    (is (= "\u001b[?1006h" scr/enable-mouse-sgr))
    (is (= "\u001b[?1006l" scr/disable-mouse-sgr))))

(deftest focus-reporting-test
  (testing "focus reporting sequences"
    (is (= "\u001b[?1004h" scr/enable-focus-reporting))
    (is (= "\u001b[?1004l" scr/disable-focus-reporting))))

(deftest bracketed-paste-test
  (testing "bracketed paste sequences"
    (is (= "\u001b[?2004h" scr/enable-bracketed-paste))
    (is (= "\u001b[?2004l" scr/disable-bracketed-paste))))

(deftest window-title-test
  (testing "sets window title"
    (is (= "\u001b]2;Hello\u0007" (scr/set-window-title "Hello")))))

(deftest clipboard-test
  (testing "copies to clipboard using OSC 52"
    ;; "Hello" in base64 is "SGVsbG8="
    (is (= "\u001b]52;c;SGVsbG8=\u0007" (scr/copy-to-clipboard "Hello")))))

(deftest content->lines-test
  (testing "splits on newlines"
    (is (= ["a" "b" "c"] (scr/content->lines "a\nb\nc"))))

  (testing "handles CRLF"
    (is (= ["a" "b"] (scr/content->lines "a\r\nb")))))

(deftest truncate-line-test
  (testing "truncates long lines"
    (is (= "hell" (scr/truncate-line "hello" 4))))

  (testing "leaves short lines alone"
    (is (= "hi" (scr/truncate-line "hi" 10))))

  (testing "handles zero width"
    (is (= "hello" (scr/truncate-line "hello" 0)))))
