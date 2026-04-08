(ns charm.style.core
  "Main styling API.

   Create styles as maps and apply them to text.

   Example:
     (def my-style (style :fg (rgb 255 0 0) :bold true :padding [1 2]))
     (render my-style \"Hello!\")  ; => styled text"
  (:require [charm.style.color :as c]
            [charm.style.border :as b]
            [charm.style.layout :as l]
            [charm.ansi.width :as w]
            [clojure.string :as str])
  (:import [org.jline.utils AttributedString AttributedStringBuilder AttributedStyle]))

;; ---------------------------------------------------------------------------
;; Style Definition
;; ---------------------------------------------------------------------------

(defn style
  "Create a style map.

   Options:
     ;; Colors
     :fg         - Foreground color
     :bg         - Background color

     ;; Text attributes
     :bold       - Bold text
     :italic     - Italic text
     :underline  - Underline text
     :blink      - Blinking text
     :faint      - Faint/dim text
     :reverse    - Reverse video

     ;; Dimensions
     :width      - Fixed width (pads/truncates)
     :height     - Fixed height

     ;; Alignment
     :align      - Horizontal alignment (:left :center :right)
     :valign     - Vertical alignment (:top :center :bottom)

     ;; Spacing
     :padding    - Padding [top right bottom left] or single value
     :margin     - Margin [top right bottom left] or single value

     ;; Border
     :border     - Border style (from charm.style.border)
     :border-fg  - Border foreground color
     :border-bg  - Border background color

     ;; Rendering
     :inline     - Remove newlines when true"
  [& {:as opts}]
  (merge
   {:fg nil
    :bg nil
    :bold false
    :italic false
    :underline false
    :blink false
    :faint false
    :reverse false
    :width nil
    :height nil
    :align :left
    :valign :top
    :padding nil
    :margin nil
    :border nil
    :border-fg nil
    :border-bg nil
    :inline false}
   opts))

;; ---------------------------------------------------------------------------
;; Style Modifiers
;; ---------------------------------------------------------------------------

(defn with-fg
  "Set foreground color."
  [s color]
  (assoc s :fg color))

(defn with-bg
  "Set background color."
  [s color]
  (assoc s :bg color))

(defn with-bold
  "Set bold."
  [s] (assoc s :bold true))

(defn with-italic
  "Set italic."
  [s] (assoc s :italic true))

(defn with-underline
  "Set underline."
  [s] (assoc s :underline true))

(defn with-padding
  "Set padding. Accepts [t r b l] or single value."
  [s padding]
  (assoc s :padding (if (number? padding) [padding] (vec padding))))

(defn with-margin
  "Set margin. Accepts [t r b l] or single value."
  [s margin]
  (assoc s :margin (if (number? margin) [margin] (vec margin))))

(defn with-border
  "Set border style."
  [s border-style]
  (assoc s :border border-style))

(defn with-width
  "Set fixed width."
  [s width]
  (assoc s :width width))

(defn with-height
  "Set fixed height."
  [s height]
  (assoc s :height height))

(defn with-align
  "Set horizontal alignment."
  [s align]
  (assoc s :align align))

(defn with-valign
  "Set vertical alignment."
  [s valign]
  (assoc s :valign valign))

;; ---------------------------------------------------------------------------
;; ANSI Sequence Generation (via JLine AttributedStyle)
;; ---------------------------------------------------------------------------

(defn style->attributed-style
  "Convert style map to JLine AttributedStyle.

   Useful for direct AttributedString construction:
     (AttributedString. text (style->attributed-style my-style))"
  ^AttributedStyle [{:keys [fg bg bold italic underline blink faint reverse]}]
  (cond-> AttributedStyle/DEFAULT
    bold      (.bold)
    faint     (.faint)
    italic    (.italic)
    underline (.underline)
    blink     (.blink)
    reverse   (.inverse)
    fg        (c/apply-color-fg fg)
    bg        (c/apply-color-bg bg)))

(defn attributed-string
  "Create a JLine AttributedString with the given style applied.

   This avoids the ANSI parse/unparse cycle when building content
   that will be rendered directly via JLine's Display.

   For single lines without layout (padding, border, margin):
     (attributed-string my-style \"Hello!\")

   For multiple parts with different styles:
     (-> (AttributedStringBuilder.)
         (.styled (style->attributed-style style1) \"part1\")
         (.styled (style->attributed-style style2) \"part2\")
         (.toAttributedString))"
  ^AttributedString [style-map text]
  (let [attr-style (style->attributed-style style-map)]
    (AttributedString. ^String text attr-style)))

(defn- apply-text-style
  "Apply text styling (colors and attributes) to a string."
  [text style]
  (let [attr-style (style->attributed-style style)]
    (if (= attr-style AttributedStyle/DEFAULT)
      text
      (->> (str/split-lines text)
           (map #(.toAnsi (AttributedString. ^String % attr-style)))
           (str/join "\n")))))

;; ---------------------------------------------------------------------------
;; Rendering
;; ---------------------------------------------------------------------------

(defn render
  "Render text with a style applied.

   (render style \"text\")
   (render style \"multiple\" \"strings\")"
  [style & strings]
  (let [text (str/join " " strings)
        {:keys [width height align valign padding margin
                border border-fg border-bg inline bg]} style

        ;; Handle inline mode
        text (if inline
               (str/replace text #"\n" "")
               text)

        ;; Apply text styling
        text (apply-text-style text style)

        ;; Apply padding
        text (if padding
               (let [[t r b l] (l/expand-box-values padding)]
                 (l/pad text t r b l :bg bg))
               text)

        ;; Apply vertical alignment if height specified
        text (if height
               (l/align-vertical text height valign)
               text)

        ;; Apply horizontal alignment if width specified
        text (if width
               (l/align-horizontal text width align :bg bg)
               text)

        ;; Apply border
        text (if border
               (b/apply-border text
                               :border border
                               :fg border-fg
                               :bg border-bg)
               text)

        ;; Apply margin
        text (if margin
               (let [[t r b l] (l/expand-box-values margin)]
                 (l/margin text t r b l))
               text)]
    text))

;; ---------------------------------------------------------------------------
;; Convenience Functions
;; ---------------------------------------------------------------------------

(defn styled
  "Apply style directly to text. Shorthand for (render (style opts...) text).

   (styled \"hello\" :fg (rgb 255 0 0) :bold true)"
  [text & style-opts]
  (render (apply style style-opts) text))

;; Re-export commonly used functions for convenience
(def rgb c/rgb)
(def hex c/hex)
(def ansi c/ansi)
(def ansi256 c/ansi256)

;; Common colors
(def black c/black)
(def red c/red)
(def green c/green)
(def yellow c/yellow)
(def blue c/blue)
(def magenta c/magenta)
(def cyan c/cyan)
(def white c/white)

;; Border styles
(def normal-border b/normal)
(def rounded-border b/rounded)
(def thick-border b/thick)
(def double-border b/double-border)
(def hidden-border b/hidden)

;; Join functions
(def join-horizontal l/join-horizontal)
(def join-vertical l/join-vertical)

;; ---------------------------------------------------------------------------
;; Frame Size Calculation
;; ---------------------------------------------------------------------------

(defn frame-size
  "Calculate the frame size (padding + border + margin) of a style.
   Returns [width height]."
  [{:keys [padding margin border]}]
  (let [[pt pr pb pl] (if padding (l/expand-box-values padding) [0 0 0 0])
        [mt mr mb ml] (if margin (l/expand-box-values margin) [0 0 0 0])
        border-h (if border (b/border-width border) 0)
        border-v (if border (b/border-height) 0)]
    [(+ pl pr ml mr border-h)
     (+ pt pb mt mb border-v)]))
