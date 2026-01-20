(ns charm.render.screen-test
  (:require [clojure.test :refer [deftest is testing]]
            [charm.render.screen :as scr]))

(deftest ansi-sequences-test
  (testing "cursor movement"
    (is (= "\u001b[5A" (scr/cursor-up 5)))
    (is (= "\u001b[3B" (scr/cursor-down 3)))
    (is (= "\u001b[10;20H" (scr/cursor-to 10 20))))

  (testing "cursor visibility"
    (is (= "\u001b[?25l" scr/cursor-hide))
    (is (= "\u001b[?25h" scr/cursor-show)))

  (testing "screen clearing"
    (is (= "\u001b[2J" scr/clear-screen))
    (is (= "\u001b[K" scr/clear-line)))

  (testing "alt screen"
    (is (= "\u001b[?1049h" scr/enter-alt-screen))
    (is (= "\u001b[?1049l" scr/exit-alt-screen)))

  (testing "mouse control"
    (is (= "\u001b[?1000h" scr/enable-mouse-normal))
    (is (= "\u001b[?1003h" scr/enable-mouse-all-motion))
    (is (= "\u001b[?1006h" scr/enable-mouse-sgr))))

(deftest window-title-test
  (testing "sets window title"
    (is (= "\u001b]2;Hello\u0007" (scr/set-window-title "Hello")))))

(deftest create-screen-test
  (testing "creates screen with dimensions"
    (let [screen (scr/create-screen 80 24)]
      (is (= 80 (:width screen)))
      (is (= 24 (:height screen)))
      (is (= 24 (count (:lines screen))))
      (is (every? empty? (:lines screen))))))

(deftest set-line-test
  (testing "sets a line in the screen"
    (let [screen (scr/create-screen 80 24)
          screen' (scr/set-line screen 5 "hello")]
      (is (= "hello" (scr/get-line screen' 5)))))

  (testing "ignores out of bounds"
    (let [screen (scr/create-screen 80 24)
          screen' (scr/set-line screen 100 "hello")]
      (is (= screen screen')))))

(deftest clear-test
  (testing "clears all lines"
    (let [screen (-> (scr/create-screen 80 24)
                     (scr/set-line 0 "hello")
                     (scr/set-line 1 "world")
                     scr/clear)]
      (is (every? empty? (:lines screen))))))

(deftest lines-diff-test
  (testing "finds changed lines"
    (is (= [1 2] (scr/lines-diff ["a" "b" "c"] ["a" "x" "y"]))))

  (testing "handles different lengths"
    (is (= [2] (scr/lines-diff ["a" "b"] ["a" "b" "c"])))
    (is (= [2] (scr/lines-diff ["a" "b" "c"] ["a" "b"]))))

  (testing "empty when identical"
    (is (= [] (scr/lines-diff ["a" "b"] ["a" "b"])))))

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

(deftest fit-content-test
  (testing "fits content to dimensions"
    (let [result (scr/fit-content "a\nb\nc\nd\ne" 10 3)]
      ;; Should keep last 3 lines
      (is (= 3 (count result)))
      (is (= ["c" "d" "e"] result))))

  (testing "truncates lines to width"
    (let [result (scr/fit-content "hello world\nshort" 5 10)]
      (is (= "hello" (first result)))
      (is (= "short" (second result))))))
