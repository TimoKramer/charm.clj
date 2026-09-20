(ns charm.integration.screen-test
  "What the user actually sees, read back off an emulated screen.

   See charm.test.screen for why these live under test-jvm."
  (:require
   [charm.components.table :as table]
   [charm.components.viewport :as viewport]
   [charm.style.border :as border]
   [charm.style.core :as style]
   [charm.style.overlay :as overlay]
   [charm.test.screen :as screen]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]))

(deftest plain-content-test
  (testing "lines land on the rows they belong to, at column 0"
    (let [s (screen/render-screen "aaa\nbbb\nccc" [20 4])]
      (is (= ["aaa" "bbb" "ccc" ""] (mapv #(str/trimr %) (:grid s))))))

  (testing "a line wider than the screen is cut, not wrapped"
    (let [s (screen/render-screen "0123456789" [4 2])]
      (is (= "0123" (screen/row-text s 0)))
      ;; not wrapped onto the next row
      (is (= "" (screen/row-text s 1))))))

(deftest border-reaches-the-screen-test
  ;; The renderer sends box-drawing characters as ACS escapes on a terminal that
  ;; wants them, so asserting on bytes says little; the emulator decodes them
  ;; back, which is what the user sees.
  (testing "an unstyled box draws box-drawing characters"
    (let [s (screen/render-screen
             (style/render (style/style :border border/rounded :padding [0 1]) "hello")
             [20 4])]
      (is (= ["╭───────╮" "│ hello │" "╰───────╯"] (take 3 (map str/trimr (:grid s)))))))

  (testing "a styled box draws the same characters, coloured"
    ;; This is the regression that mattered: styling used to replace them with
    ;; +--+ because toAnsi rewrites characters when it has no terminal to ask
    (let [s (screen/render-screen
             (style/render (style/style :border border/rounded :border-fg :red
                                        :padding [0 1]) "hello")
             [20 4])]
      (is (= "╭───────╮" (screen/row-text s 0)))
      (is (= \╭ (:char (screen/cell s 0 0))))
      ;; the border is coloured and the text inside it is not
      (is (some? (:fg (screen/cell s 0 0))))
      (is (nil? (:fg (screen/cell s 1 2)))))))

(deftest styling-reaches-the-screen-test
  (testing "bold and colour arrive as cell attributes"
    (let [s (screen/render-screen
             (str (style/render (style/style :bold true) "BOLD") " "
                  (style/render (style/style :fg :red) "red") " plain")
             [20 2])]
      (is (= "BOLD red plain" (screen/row-text s 0)))
      (is (true? (:bold? (screen/cell s 0 0))))
      (is (false? (:bold? (screen/cell s 0 5))))
      (is (some? (:fg (screen/cell s 0 5))))
      (is (nil? (:fg (screen/cell s 0 9))))))

  (testing "a cut line keeps the styling of what survives"
    (let [s (screen/render-screen (style/render (style/style :bold true) "0123456789") [4 2])]
      (is (= "0123" (screen/row-text s 0)))
      (is (every? #(:bold? (screen/cell s 0 %)) (range 4))))))

(deftest overflow-direction-test
  (testing ":top, the default, keeps the last lines"
    (let [s (screen/render-screen "one\ntwo\nthree" [10 2])]
      (is (= ["two" "three"] (map str/trimr (:grid s))))))

  (testing ":bottom keeps the first ones, so a title survives"
    (let [s (screen/render-screen "one\ntwo\nthree" [10 2] :overflow :bottom)]
      (is (= ["one" "two"] (map str/trimr (:grid s)))))))

(deftest components-on-screen-test
  (testing "a table lines its columns up on screen"
    (let [tbl (table/table [{:title "Name" :width 6} {:title "N" :width 3}]
                           [["ada" "1"] ["grace" "22"]]
                           :cursor 0)
          s (screen/render-screen (table/table-view tbl) [20 4])]
      (is (= ["Name    N" "ada     1" "grace   22"] (take 3 (map str/trimr (:grid s)))))
      ;; header is bold, and the cursor row is coloured
      (is (true? (:bold? (screen/cell s 0 0))))
      (is (some? (:fg (screen/cell s 1 0))))
      (is (nil? (:fg (screen/cell s 2 0))))))

  (testing "a viewport shows its window and nothing else"
    (let [vp (viewport/viewport (str/join "\n" (map #(str "line " %) (range 1 21)))
                                :width 10 :height 3)
          s (screen/render-screen (viewport/viewport-view vp) [12 4])]
      (is (= ["line 1" "line 2" "line 3"] (take 3 (map str/trimr (:grid s)))))))

  (testing "an overlay sits on top of the base, borders intact"
    (let [base (style/render (style/style :border border/normal :width 14) "base\nbase\nbase")
          panel (style/render (style/style :border border/rounded) "hi")
          s (screen/render-screen (overlay/place-overlay base panel 3 1) [20 6])]
      ;; the base's own border survives compositing
      (is (str/starts-with? (screen/row-text s 0) "┌"))
      (is (str/includes? (screen/screen-text s) "╭──╮"))
      (is (str/includes? (screen/screen-text s) "│hi│")))))
