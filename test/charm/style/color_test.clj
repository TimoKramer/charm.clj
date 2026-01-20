(ns charm.style.color-test
  (:require [clojure.test :refer [deftest is testing]]
            [charm.style.color :as c]))

(deftest ansi-color-test
  (testing "creates ANSI colors from numbers"
    (is (= {:type :ansi :code 1} (c/ansi 1)))
    (is (= {:type :ansi :code 0} (c/ansi 0))))

  (testing "creates ANSI colors from keywords"
    (is (= {:type :ansi :code 1} (c/ansi :red)))
    (is (= {:type :ansi :code 4} (c/ansi :blue)))
    (is (= {:type :ansi :code 9} (c/ansi :bright-red)))))

(deftest ansi256-color-test
  (testing "creates ANSI 256 colors"
    (is (= {:type :ansi256 :code 196} (c/ansi256 196)))
    (is (= {:type :ansi256 :code 0} (c/ansi256 0)))
    (is (= {:type :ansi256 :code 255} (c/ansi256 255)))))

(deftest rgb-color-test
  (testing "creates RGB colors"
    (is (= {:type :rgb :r 255 :g 0 :b 0} (c/rgb 255 0 0)))
    (is (= {:type :rgb :r 0 :g 255 :b 0} (c/rgb 0 255 0)))))

(deftest hex-color-test
  (testing "parses hex colors with #"
    (is (= {:type :rgb :r 255 :g 0 :b 0} (c/hex "#ff0000")))
    (is (= {:type :rgb :r 0 :g 255 :b 0} (c/hex "#00ff00"))))

  (testing "parses hex colors without #"
    (is (= {:type :rgb :r 255 :g 255 :b 255} (c/hex "ffffff")))
    (is (= {:type :rgb :r 0 :g 0 :b 0} (c/hex "000000")))))

(deftest color->fg-seq-test
  (testing "generates ANSI foreground sequences"
    (is (= "\u001b[31m" (c/color->fg-seq (c/ansi :red))))
    (is (= "\u001b[34m" (c/color->fg-seq (c/ansi :blue)))))

  (testing "generates ANSI 256 foreground sequences"
    (is (= "\u001b[38;5;196m" (c/color->fg-seq (c/ansi256 196)))))

  (testing "generates RGB foreground sequences"
    (is (= "\u001b[38;2;255;0;0m" (c/color->fg-seq (c/rgb 255 0 0)))))

  (testing "returns nil for no-color"
    (is (nil? (c/color->fg-seq (c/no-color))))
    (is (nil? (c/color->fg-seq nil)))))

(deftest color->bg-seq-test
  (testing "generates ANSI background sequences"
    (is (= "\u001b[41m" (c/color->bg-seq (c/ansi :red))))
    (is (= "\u001b[44m" (c/color->bg-seq (c/ansi :blue)))))

  (testing "generates ANSI 256 background sequences"
    (is (= "\u001b[48;5;196m" (c/color->bg-seq (c/ansi256 196)))))

  (testing "generates RGB background sequences"
    (is (= "\u001b[48;2;255;0;0m" (c/color->bg-seq (c/rgb 255 0 0))))))

(deftest rgb->ansi256-test
  (testing "converts RGB to ANSI 256"
    ;; Pure red should map to color cube
    (let [result (c/rgb->ansi256 {:r 255 :g 0 :b 0})]
      (is (= :ansi256 (:type result))))
    ;; Gray should map to grayscale ramp
    (let [result (c/rgb->ansi256 {:r 128 :g 128 :b 128})]
      (is (= :ansi256 (:type result)))
      (is (>= (:code result) 232)))))  ; Grayscale starts at 232

(deftest predefined-colors-test
  (testing "predefined colors are available"
    (is (= {:type :ansi :code 0} c/black))
    (is (= {:type :ansi :code 1} c/red))
    (is (= {:type :ansi :code 2} c/green))
    (is (= {:type :ansi :code 7} c/white))
    (is (= {:type :ansi :code 9} c/bright-red))))
