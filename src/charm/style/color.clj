(ns ^:no-doc charm.style.color
  "Terminal color handling.

   Supports:
   - ANSI 16 basic colors (0-15)
   - ANSI 256 extended palette (0-255)
   - True color RGB (24-bit)
   - Adaptive colors that resolve against the terminal background"
  (:require
   [clojure.string :as str])
  (:import
   [org.jline.utils AttributedString AttributedStyle]))

;; ---------------------------------------------------------------------------
;; Color Profile Detection
;; ---------------------------------------------------------------------------

(def color-profiles
  "Available color profiles."
  #{:ascii :ansi :ansi256 :true-color})

(def ^:dynamic *color-profile*
  "Current color profile. Default is true-color.
   Bound by charm.program/run from the detected terminal profile;
   colors are downgraded to this profile at render time."
  :true-color)

(def ^:dynamic *dark-background?*
  "Whether the terminal background is dark. Default is true.
   Bound by charm.program/run from the detected terminal background;
   adaptive colors resolve against this at render time."
  true)

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

(defn adaptive
  "Create an adaptive color that resolves against the terminal background:
   `light` is used on light backgrounds, `dark` on dark backgrounds.

   (adaptive (hex \"#333333\") (hex \"#dddddd\"))"
  [light dark]
  {:type :adaptive :light light :dark dark})

(defn coerce-color
  "Coerce a color value to a color map. Accepts:
   - color maps (returned unchanged)
   - integers: ANSI 256 codes, e.g. 240
   - keywords: ANSI 16 color names, e.g. :red, :bright-blue
   - strings: hex colors, e.g. \"#ff0000\"

   Returns nil for nil; throws ex-info on unrecognised values."
  [color]
  (cond
    (nil? color) nil
    (and (map? color) (:type color)) color
    (integer? color) (ansi256 color)
    (and (keyword? color) (contains? ansi-colors color)) (ansi color)
    (string? color) (hex color)
    :else (throw (ex-info (str "Unrecognised color value: " (pr-str color))
                          {:color color}))))

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

(def ^:private cube-levels
  "The six channel levels of the ANSI 256 color cube."
  [0 95 135 175 215 255])

(defn ansi256->rgb
  "Convert an ANSI 256 color to its RGB value."
  [{:keys [code]}]
  (cond
    ;; 0-15: standard colors, from the hex table
    (< code 16) (hex (nth ansi-hex code))

    ;; 232-255: grayscale ramp
    (>= code 232) (let [v (+ 8 (* 10 (- code 232)))]
                    (rgb v v v))

    ;; 16-231: 6x6x6 color cube
    :else (let [idx (- code 16)]
            (rgb (nth cube-levels (quot idx 36))
                 (nth cube-levels (mod (quot idx 6) 6))
                 (nth cube-levels (mod idx 6))))))

(defn rgb->ansi16
  "Convert RGB to the nearest ANSI 16 color by distance in RGB space."
  [{:keys [r g b]}]
  (let [distance (fn [hex-str]
                   (let [{hr :r hg :g hb :b} (hex hex-str)]
                     (+ (* (- r hr) (- r hr))
                        (* (- g hg) (- g hg))
                        (* (- b hb) (- b hb)))))]
    (ansi (first (apply min-key second
                        (map-indexed (fn [i h] [i (distance h)]) ansi-hex))))))

(defn downgrade-color
  "Downgrade a color to fit a color profile."
  [color profile]
  (case profile
    :ascii (no-color)
    :ansi (case (:type color)
            :ansi color
            :ansi256 (rgb->ansi16 (ansi256->rgb color))
            :rgb (rgb->ansi16 color)
            color)
    :ansi256 (case (:type color)
               (:ansi :ansi256) color
               :rgb (rgb->ansi256 color)
               color)
    :true-color color
    color))

(defn resolve-color
  "Resolve a color value for rendering: coerce it to a color map, pick the
   light or dark variant of adaptive colors against *dark-background?*, and
   downgrade it to *color-profile*. Returns nil for nil."
  [color]
  (when-let [color (coerce-color color)]
    (-> (if (= :adaptive (:type color))
          (coerce-color (if *dark-background?* (:dark color) (:light color)))
          color)
        (downgrade-color *color-profile*))))

;; ---------------------------------------------------------------------------
;; Color Application (via JLine AttributedStyle)
;; ---------------------------------------------------------------------------

(defn apply-color-fg
  "Resolve a color value and apply it as foreground to an AttributedStyle."
  ^AttributedStyle [^AttributedStyle style color]
  (let [color (resolve-color color)]
    (if (or (nil? color) (= :none (:type color)))
      style
      (case (:type color)
        :ansi    (.foreground style (int (:code color)))
        :ansi256 (.foreground style (int (:code color)))
        :rgb     (.foreground style (int (:r color)) (int (:g color)) (int (:b color)))
        style))))

(defn apply-color-bg
  "Resolve a color value and apply it as background to an AttributedStyle."
  ^AttributedStyle [^AttributedStyle style color]
  (let [color (resolve-color color)]
    (if (or (nil? color) (= :none (:type color)))
      style
      (case (:type color)
        :ansi    (.background style (int (:code color)))
        :ansi256 (.background style (int (:code color)))
        :rgb     (.background style (int (:r color)) (int (:g color)) (int (:b color)))
        style))))

(defn styled-str
  "Create a styled string with foreground and/or background color.
   Returns the string with ANSI escape sequences applied."
  [text & {:keys [fg bg]}]
  (if (and (nil? fg) (nil? bg))
    text
    (let [style (-> AttributedStyle/DEFAULT
                    (apply-color-fg fg)
                    (apply-color-bg bg))]
      (.toAnsi (AttributedString. ^String text style)))))

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
