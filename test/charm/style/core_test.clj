(ns charm.style.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [charm.style.core :as s]
            [charm.style.color :as c]
            [charm.style.border :as b]
            [clojure.string :as str]))

(deftest color-shorthand-render-test
  (testing "integer :fg renders as ANSI 256"
    (is (str/includes? (s/render (s/style :fg 240) "hi") "\u001b[38;5;240m")))

  (testing "keyword and hex string colors render"
    (is (str/includes? (s/render (s/style :fg :red) "hi") "\u001b[31m"))
    (is (str/includes? (s/render (s/style :fg "#ff0000") "hi") "\u001b[")))

  (testing "adaptive colors resolve against the background"
    (let [st (s/style :fg (s/adaptive c/black c/white))]
      (binding [c/*dark-background?* true]
        (is (str/includes? (s/render st "hi") "\u001b[37m")))
      (binding [c/*dark-background?* false]
        (is (str/includes? (s/render st "hi") "\u001b[30m"))))))

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
      (is (true? (:bold st)))))

  (testing "normalizes a bare padding or margin value"
    (is (= [3] (:padding (s/style :padding 3))))
    (is (= [3] (:margin (s/style :margin 3))))
    (is (= [1 2] (:padding (s/style :padding [1 2]))))
    (is (nil? (:padding (s/style))))))

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

  (testing "renders every documented text attribute"
    (doseq [[attr code] {:bold "1" :faint "2" :italic "3" :underline "4"
                         :blink "5" :reverse "7" :strikethrough "9"}]
      (is (str/includes? (s/render (s/style attr true) "hello")
                         (str "\u001b[" code "m"))
          (str attr " should emit SGR " code))))

  (testing "renders with padding"
    (let [result (s/render (s/style :padding [0 2 0 2]) "hi")]
      (is (str/includes? result "  hi  "))))

  (testing "renders with a bare padding value"
    (let [lines (str/split-lines (s/render (s/style :padding 1) "hi"))]
      (is (= 3 (count lines)))
      (is (str/includes? (second lines) " hi "))))

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

  (testing "calculates frame size with a bare padding value"
    (is (= [6 6] (s/frame-size (s/style :padding 3)))))

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

(deftest merge-style-test
  (let [base (s/style :fg c/red :padding [0 1] :width 20)]
    (testing "changes only the named options"
      (let [variant (s/merge-style base :fg c/blue :bold true)]
        (is (= c/blue (:fg variant)))
        (is (true? (:bold variant)))
        ;; inherited, not reset to the constructor default
        (is (= [0 1] (:padding variant)))
        (is (= 20 (:width variant)))))

    (testing "plain merge of two styles is what this avoids"
      ;; (merge base (style :bold true)) would overwrite :fg with nil
      (is (nil? (:fg (merge base (s/style :bold true)))))
      (is (= c/red (:fg (s/merge-style base :bold true)))))

    (testing "an option can be turned back off"
      (let [bolded (s/merge-style base :bold true)]
        (is (false? (:bold (s/merge-style bolded :bold false))))))

    (testing "a bare padding or margin value is normalized, as in style"
      (is (= [2] (:padding (s/merge-style base :padding 2))))
      (is (= [3] (:margin (s/merge-style base :margin 3)))))

    (testing "with no options it is a copy"
      (is (= base (s/merge-style base))))

    (testing "the result renders"
      (is (str/includes? (s/render (s/merge-style base :fg c/blue) "hi") "hi")))))
