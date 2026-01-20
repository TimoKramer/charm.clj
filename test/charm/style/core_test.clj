(ns charm.style.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [charm.style.core :as s]
            [charm.style.color :as c]
            [charm.style.border :as b]
            [clojure.string :as str]))

(deftest style-creation-test
  (testing "creates style with defaults"
    (let [st (s/style)]
      (is (nil? (:fg st)))
      (is (nil? (:bg st)))
      (is (false? (:bold st)))
      (is (= :left (:align st)))))

  (testing "creates style with options"
    (let [st (s/style :fg c/red :bold true)]
      (is (= c/red (:fg st)))
      (is (true? (:bold st))))))

(deftest style-modifiers-test
  (testing "with-fg sets foreground"
    (let [st (-> (s/style) (s/with-fg c/red))]
      (is (= c/red (:fg st)))))

  (testing "with-bold sets bold"
    (let [st (-> (s/style) s/with-bold)]
      (is (true? (:bold st)))))

  (testing "with-padding sets padding"
    (let [st (-> (s/style) (s/with-padding 2))]
      (is (= [2] (:padding st))))
    (let [st (-> (s/style) (s/with-padding [1 2 3 4]))]
      (is (= [1 2 3 4] (:padding st)))))

  (testing "with-border sets border"
    (let [st (-> (s/style) (s/with-border b/rounded))]
      (is (= b/rounded (:border st))))))

(deftest render-test
  (testing "renders plain text"
    (let [result (s/render (s/style) "hello")]
      (is (= "hello" result))))

  (testing "renders with foreground color"
    (let [result (s/render (s/style :fg c/red) "hello")]
      (is (str/includes? result "\u001b[31m"))
      (is (str/includes? result "hello"))
      (is (str/includes? result "\u001b[0m"))))

  (testing "renders with bold"
    (let [result (s/render (s/style :bold true) "hello")]
      (is (str/includes? result "\u001b[1m"))))

  (testing "renders with padding"
    (let [result (s/render (s/style :padding [0 2 0 2]) "hi")]
      (is (str/includes? result "  hi  "))))

  (testing "renders with border"
    (let [result (s/render (s/style :border b/normal) "hi")
          lines (str/split-lines result)]
      (is (= 3 (count lines)))
      (is (str/includes? (first lines) "┌"))))

  (testing "renders with width alignment"
    (let [result (s/render (s/style :width 10 :align :center) "hi")]
      ;; "hi" centered in width 10
      (is (= 10 (count result)))))

  (testing "renders inline (removes newlines)"
    (let [result (s/render (s/style :inline true) "a\nb")]
      (is (= "ab" result)))))

(deftest styled-shorthand-test
  (testing "applies style directly"
    (let [result (s/styled "hello" :bold true)]
      (is (str/includes? result "\u001b[1m"))
      (is (str/includes? result "hello")))))

(deftest reexported-functions-test
  (testing "color functions are available"
    (is (= {:type :rgb :r 255 :g 0 :b 0} (s/rgb 255 0 0)))
    (is (= {:type :rgb :r 255 :g 0 :b 0} (s/hex "#ff0000")))
    (is (= c/red s/red)))

  (testing "border styles are available"
    (is (= b/rounded s/rounded-border))
    (is (= b/normal s/normal-border))))

(deftest frame-size-test
  (testing "calculates frame size without decorations"
    (is (= [0 0] (s/frame-size (s/style)))))

  (testing "calculates frame size with padding"
    (let [[w h] (s/frame-size (s/style :padding [1 2]))]
      (is (= 4 w))   ; 2 left + 2 right
      (is (= 2 h)))) ; 1 top + 1 bottom

  (testing "calculates frame size with margin"
    (let [[w h] (s/frame-size (s/style :margin [1]))]
      (is (= 2 w))   ; 1 left + 1 right
      (is (= 2 h)))) ; 1 top + 1 bottom

  (testing "calculates frame size with border"
    (let [[w h] (s/frame-size (s/style :border b/normal))]
      (is (= 2 w))   ; │ on each side
      (is (= 2 h)))) ; top and bottom lines

  (testing "calculates combined frame size"
    (let [[w h] (s/frame-size (s/style :padding [1 2]
                                       :border b/normal
                                       :margin [1]))]
      ;; padding: 2+2, border: 1+1, margin: 1+1 = 8
      (is (= 8 w))
      ;; padding: 1+1, border: 1+1, margin: 1+1 = 6
      (is (= 6 h)))))
