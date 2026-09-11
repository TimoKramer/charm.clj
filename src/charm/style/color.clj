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
   [org.jline.utils AttributedString AttributedStyle Colors]))

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

(defn downgrade-color
  "Downgrade a color to fit a color profile.

   Nearest-color matching is JLine's (`org.jline.utils.Colors`), which searches
   the whole 256-color table — the 16 basic entries included — and measures
   distance in CIE Lab rather than RGB space."
  [{:keys [type code r g b] :as color} profile]
  (case profile
    :ascii (no-color)
    :ansi (case type
            :ansi color
            :ansi256 (ansi (Colors/roundColor (int code) 16))
            :rgb (ansi (Colors/roundRgbColor (int r) (int g) (int b) 16))
            color)
    :ansi256 (case type
               (:ansi :ansi256) color
               :rgb (ansi256 (Colors/roundRgbColor (int r) (int g) (int b) 256))
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
