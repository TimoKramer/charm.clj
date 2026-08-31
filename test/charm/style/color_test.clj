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

(deftest adaptive-color-test
  (testing "creates adaptive colors"
    (is (= {:type :adaptive :light {:type :ansi :code 0} :dark {:type :ansi :code 7}}
           (c/adaptive c/black c/white)))))

(deftest coerce-color-test
  (testing "returns nil for nil"
    (is (nil? (c/coerce-color nil))))

  (testing "returns color maps unchanged"
    (is (= c/red (c/coerce-color c/red)))
    (is (= (c/rgb 1 2 3) (c/coerce-color (c/rgb 1 2 3)))))

  (testing "coerces integers to ANSI 256"
    (is (= {:type :ansi256 :code 240} (c/coerce-color 240)))
    (is (= {:type :ansi256 :code 0} (c/coerce-color 0))))

  (testing "coerces ANSI 16 name keywords"
    (is (= {:type :ansi :code 1} (c/coerce-color :red)))
    (is (= {:type :ansi :code 12} (c/coerce-color :bright-blue))))

  (testing "coerces hex strings"
    (is (= {:type :rgb :r 255 :g 128 :b 0} (c/coerce-color "#ff8000"))))

  (testing "throws on unrecognised values"
    (is (thrown? clojure.lang.ExceptionInfo (c/coerce-color :no-such-color)))
    (is (thrown? clojure.lang.ExceptionInfo (c/coerce-color 1.5)))))

(deftest resolve-color-test
  (testing "resolves adaptive colors against *dark-background?*"
    (let [color (c/adaptive c/black c/white)]
      (binding [c/*dark-background?* true]
        (is (= c/white (c/resolve-color color))))
      (binding [c/*dark-background?* false]
        (is (= c/black (c/resolve-color color))))))

  (testing "coerces the chosen adaptive variant"
    (binding [c/*dark-background?* true]
      (is (= {:type :ansi256 :code 254} (c/resolve-color (c/adaptive 236 254))))))

  (testing "downgrades to *color-profile*"
    (binding [c/*color-profile* :ansi256]
      (is (= :ansi256 (:type (c/resolve-color (c/rgb 255 0 0))))))
    (binding [c/*color-profile* :ascii]
      (is (= {:type :none} (c/resolve-color (c/rgb 255 0 0)))))))

(deftest styled-str-test
  (testing "applies foreground color"
    (is (= "\u001b[31mx\u001b[0m" (c/styled-str "x" :fg (c/ansi :red))))
    (is (= "\u001b[34mx\u001b[0m" (c/styled-str "x" :fg (c/ansi :blue)))))

  (testing "applies ANSI 256 foreground"
    (is (= "\u001b[38;5;196mx\u001b[0m" (c/styled-str "x" :fg (c/ansi256 196)))))

  (testing "applies RGB foreground"
    (is (= "\u001b[38;5;196mx\u001b[0m" (c/styled-str "x" :fg (c/rgb 255 0 0)))))

  (testing "applies background color"
    (is (= "\u001b[41mx\u001b[0m" (c/styled-str "x" :bg (c/ansi :red))))
    (is (= "\u001b[44mx\u001b[0m" (c/styled-str "x" :bg (c/ansi :blue)))))

  (testing "applies ANSI 256 background"
    (is (= "\u001b[48;5;196mx\u001b[0m" (c/styled-str "x" :bg (c/ansi256 196)))))

  (testing "applies both foreground and background"
    (is (= "\u001b[31;44mx\u001b[0m" (c/styled-str "x" :fg (c/ansi :red) :bg (c/ansi :blue)))))

  (testing "returns unchanged for no colors"
    (is (= "x" (c/styled-str "x")))
    (is (= "x" (c/styled-str "x" :fg nil :bg nil))))

  (testing "coerces shorthand color values"
    (is (= "\u001b[38;5;240mx\u001b[0m" (c/styled-str "x" :fg 240)))
    (is (= "\u001b[31mx\u001b[0m" (c/styled-str "x" :fg :red))))

  (testing "renders plain text under the :ascii profile"
    (binding [c/*color-profile* :ascii]
      (is (= "x" (c/styled-str "x" :fg (c/rgb 255 0 0)))))))

(deftest rgb->ansi256-test
  (testing "converts RGB to ANSI 256"
    ;; Pure red should map to color cube
    (let [result (c/rgb->ansi256 {:r 255 :g 0 :b 0})]
      (is (= :ansi256 (:type result))))
    ;; Gray should map to grayscale ramp
    (let [result (c/rgb->ansi256 {:r 128 :g 128 :b 128})]
      (is (= :ansi256 (:type result)))
      (is (>= (:code result) 232)))))  ; Grayscale starts at 232

(deftest ansi256->rgb-test
  (testing "converts standard colors"
    (is (= {:type :rgb :r 0 :g 0 :b 0} (c/ansi256->rgb (c/ansi256 0))))
    (is (= {:type :rgb :r 255 :g 0 :b 0} (c/ansi256->rgb (c/ansi256 9)))))

  (testing "converts the 6x6x6 cube"
    ;; 196 is the cube's pure red corner
    (is (= {:type :rgb :r 255 :g 0 :b 0} (c/ansi256->rgb (c/ansi256 196))))
    (is (= {:type :rgb :r 0 :g 0 :b 0} (c/ansi256->rgb (c/ansi256 16)))))

  (testing "converts the grayscale ramp"
    (is (= {:type :rgb :r 8 :g 8 :b 8} (c/ansi256->rgb (c/ansi256 232))))
    (is (= {:type :rgb :r 238 :g 238 :b 238} (c/ansi256->rgb (c/ansi256 255))))))

(deftest rgb->ansi16-test
  (testing "maps colors to the nearest ANSI 16 entry"
    (is (= c/black (c/rgb->ansi16 (c/rgb 0 0 0))))
    (is (= c/bright-red (c/rgb->ansi16 (c/rgb 255 0 0))))
    (is (= c/bright-green (c/rgb->ansi16 (c/rgb 0 255 0))))
    (is (= c/bright-white (c/rgb->ansi16 (c/rgb 255 255 255)))))

  (testing "orange lands on a warm color, not an arbitrary one"
    ;; Regression: (mod code 16) used to turn orange into cyan
    (is (contains? #{c/yellow c/red c/bright-red c/bright-yellow}
                   (c/rgb->ansi16 (c/rgb 255 128 0))))))

(deftest downgrade-color-test
  (testing ":ansi downgrade preserves hue"
    (is (contains? #{c/yellow c/red c/bright-red c/bright-yellow}
                   (c/downgrade-color (c/rgb 255 128 0) :ansi)))
    (is (contains? #{c/yellow c/red c/bright-red c/bright-yellow}
                   (c/downgrade-color (c/ansi256 214) :ansi))))

  (testing ":ansi leaves ANSI 16 colors alone"
    (is (= c/red (c/downgrade-color c/red :ansi))))

  (testing ":ansi256 downgrades RGB only"
    (is (= :ansi256 (:type (c/downgrade-color (c/rgb 255 128 0) :ansi256))))
    (is (= c/red (c/downgrade-color c/red :ansi256))))

  (testing ":ascii drops all color"
    (is (= {:type :none} (c/downgrade-color (c/rgb 255 128 0) :ascii))))

  (testing ":true-color passes through"
    (is (= (c/rgb 255 128 0) (c/downgrade-color (c/rgb 255 128 0) :true-color)))))

(deftest predefined-colors-test
  (testing "predefined colors are available"
    (is (= {:type :ansi :code 0} c/black))
    (is (= {:type :ansi :code 1} c/red))
    (is (= {:type :ansi :code 2} c/green))
    (is (= {:type :ansi :code 7} c/white))
    (is (= {:type :ansi :code 9} c/bright-red))))
