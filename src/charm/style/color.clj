(ns charm.style.color
  "Terminal color handling.

   Supports:
   - ANSI 16 basic colors (0-15)
   - ANSI 256 extended palette (0-255)
   - True color RGB (24-bit)" 
  (:require
   [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Color Profile Detection
;; ---------------------------------------------------------------------------

(def color-profiles
  "Available color profiles."
  #{:ascii :ansi :ansi256 :true-color})

(def ^:dynamic *color-profile*
  "Current color profile. Default is true-color."
  :true-color)

(defn detect-color-profile
  "Detect terminal color profile from environment.
   Returns :ascii, :ansi, :ansi256, or :true-color."
  []
  (let [term (System/getenv "TERM")
        colorterm (System/getenv "COLORTERM")]
    (cond
      (or (= colorterm "truecolor")
          (= colorterm "24bit"))
      :true-color

      (and term (re-find #"256color" term))
      :ansi256

      (or (nil? term)
          (= term "dumb"))
      :ascii

      :else
      :ansi)))

;; ---------------------------------------------------------------------------
;; ANSI Basic Colors (0-15)
;; ---------------------------------------------------------------------------

(def ansi-colors
  "ANSI 16 basic color names to codes."
  {:black 0
   :red 1
   :green 2
   :yellow 3
   :blue 4
   :magenta 5
   :cyan 6
   :white 7
   :bright-black 8
   :bright-red 9
   :bright-green 10
   :bright-yellow 11
   :bright-blue 12
   :bright-magenta 13
   :bright-cyan 14
   :bright-white 15})

(def ansi-hex
  "ANSI 16 colors as hex values for conversion."
  ["#000000" "#800000" "#008000" "#808000" "#000080" "#800080" "#008080" "#c0c0c0"
   "#808080" "#ff0000" "#00ff00" "#ffff00" "#0000ff" "#ff00ff" "#00ffff" "#ffffff"])

;; ---------------------------------------------------------------------------
;; Color Construction
;; ---------------------------------------------------------------------------

(defn ansi
  "Create an ANSI 16 color (0-15).
   Accepts a number or keyword like :red, :bright-blue."
  [color]
  (let [code (if (keyword? color)
               (get ansi-colors color color)
               color)]
    {:type :ansi :code code}))

(defn ansi256
  "Create an ANSI 256 color (0-255)."
  [code]
  {:type :ansi256 :code code})

(defn rgb
  "Create a true color from RGB values (0-255 each)."
  [r g b]
  {:type :rgb :r r :g g :b b})

(defn hex
  "Create a true color from a hex string like \"#ff0000\" or \"ff0000\"."
  [hex-str]
  (let [s (if (str/starts-with? hex-str "#")
            (subs hex-str 1)
            hex-str)
        r (Integer/parseInt (subs s 0 2) 16)
        g (Integer/parseInt (subs s 2 4) 16)
        b (Integer/parseInt (subs s 4 6) 16)]
    (rgb r g b)))

(defn no-color
  "Create a no-color (transparent) value."
  []
  {:type :none})

;; ---------------------------------------------------------------------------
;; Color Application (ANSI Escape Sequences)
;; ---------------------------------------------------------------------------

(def ^:private CSI "\u001b[")

(defn- ansi-fg-code
  "Get foreground SGR code for an ANSI color."
  [code]
  (if (< code 8)
    (+ 30 code)
    (+ 82 code)))  ; 90-97 for bright colors

(defn- ansi-bg-code
  "Get background SGR code for an ANSI color."
  [code]
  (if (< code 8)
    (+ 40 code)
    (+ 92 code)))  ; 100-107 for bright colors

(defn color->fg-seq
  "Convert a color to foreground ANSI escape sequence."
  [color]
  (when color
    (case (:type color)
      :none nil
      :ansi (str CSI (ansi-fg-code (:code color)) "m")
      :ansi256 (str CSI "38;5;" (:code color) "m")
      :rgb (str CSI "38;2;" (:r color) ";" (:g color) ";" (:b color) "m")
      nil)))

(defn color->bg-seq
  "Convert a color to background ANSI escape sequence."
  [color]
  (when color
    (case (:type color)
      :none nil
      :ansi (str CSI (ansi-bg-code (:code color)) "m")
      :ansi256 (str CSI "48;5;" (:code color) "m")
      :rgb (str CSI "48;2;" (:r color) ";" (:g color) ";" (:b color) "m")
      nil)))

;; ---------------------------------------------------------------------------
;; Color Conversion
;; ---------------------------------------------------------------------------

(defn rgb->ansi256
  "Convert RGB to closest ANSI 256 color."
  [{:keys [r g b]}]
  (let [;; Check grayscale first (232-255)
        gray-start 232
        gray-levels 24
        gray? (and (< (Math/abs (long (- r g))) 10)
                   (< (Math/abs (long (- g b))) 10)
                   (< (Math/abs (long (- r b))) 10))

        ;; Convert to 6x6x6 color cube (16-231)
        cube-value (fn [v] (int (Math/round (/ (* v 5.0) 255.0))))
        cube-r (cube-value r)
        cube-g (cube-value g)
        cube-b (cube-value b)]
    (if gray?
      ;; Map to grayscale ramp
      (let [gray-idx (int (Math/round (* (/ (+ r g b) 3.0 255.0) (dec gray-levels))))]
        (ansi256 (+ gray-start gray-idx)))
      ;; Map to 6x6x6 color cube
      (ansi256 (+ 16 (* 36 cube-r) (* 6 cube-g) cube-b)))))

(defn downgrade-color
  "Downgrade a color to fit a color profile."
  [color profile]
  (case profile
    :ascii (no-color)
    :ansi (case (:type color)
            :ansi color
            :ansi256 (ansi (mod (:code color) 16))
            :rgb (ansi (mod (:code (rgb->ansi256 color)) 16))
            color)
    :ansi256 (case (:type color)
               (:ansi :ansi256) color
               :rgb (rgb->ansi256 color)
               color)
    :true-color color
    color))

;; ---------------------------------------------------------------------------
;; Convenience Colors
;; ---------------------------------------------------------------------------

(def black (ansi :black))
(def red (ansi :red))
(def green (ansi :green))
(def yellow (ansi :yellow))
(def blue (ansi :blue))
(def magenta (ansi :magenta))
(def cyan (ansi :cyan))
(def white (ansi :white))

(def bright-black (ansi :bright-black))
(def bright-red (ansi :bright-red))
(def bright-green (ansi :bright-green))
(def bright-yellow (ansi :bright-yellow))
(def bright-blue (ansi :bright-blue))
(def bright-magenta (ansi :bright-magenta))
(def bright-cyan (ansi :bright-cyan))
(def bright-white (ansi :bright-white))
