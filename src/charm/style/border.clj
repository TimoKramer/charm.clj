(ns charm.style.border
  "Border rendering for styled boxes.

   Provides predefined border styles and functions for
   rendering borders around text content."
  (:require [charm.ansi.width :as w]
            [charm.style.color :as color]
            [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Border Definitions
;; ---------------------------------------------------------------------------

(defn border
  "Create a border definition.

   Options:
     :top, :bottom, :left, :right - Edge characters
     :top-left, :top-right, :bottom-left, :bottom-right - Corner characters"
  [{:keys [top bottom left right
           top-left top-right bottom-left bottom-right]}]
  {:top top
   :bottom bottom
   :left left
   :right right
   :top-left top-left
   :top-right top-right
   :bottom-left bottom-left
   :bottom-right bottom-right})

;; ---------------------------------------------------------------------------
;; Standard Border Styles
;; ---------------------------------------------------------------------------

(def no-border
  "No border (empty strings)."
  (border {:top "" :bottom "" :left "" :right ""
           :top-left "" :top-right "" :bottom-left "" :bottom-right ""}))

(def normal
  "Normal single-line border."
  (border {:top "─" :bottom "─" :left "│" :right "│"
           :top-left "┌" :top-right "┐" :bottom-left "└" :bottom-right "┘"}))

(def rounded
  "Rounded corner border."
  (border {:top "─" :bottom "─" :left "│" :right "│"
           :top-left "╭" :top-right "╮" :bottom-left "╰" :bottom-right "╯"}))

(def block
  "Block/filled border."
  (border {:top "█" :bottom "█" :left "█" :right "█"
           :top-left "█" :top-right "█" :bottom-left "█" :bottom-right "█"}))

(def outer-half-block
  "Outer half-block border."
  (border {:top "▀" :bottom "▄" :left "▌" :right "▐"
           :top-left "▛" :top-right "▜" :bottom-left "▙" :bottom-right "▟"}))

(def inner-half-block
  "Inner half-block border."
  (border {:top "▄" :bottom "▀" :left "▐" :right "▌"
           :top-left "▗" :top-right "▖" :bottom-left "▝" :bottom-right "▘"}))

(def thick
  "Thick double-line border."
  (border {:top "━" :bottom "━" :left "┃" :right "┃"
           :top-left "┏" :top-right "┓" :bottom-left "┗" :bottom-right "┛"}))

(def double-border
  "Double-line border."
  (border {:top "═" :bottom "═" :left "║" :right "║"
           :top-left "╔" :top-right "╗" :bottom-left "╚" :bottom-right "╝"}))

(def hidden
  "Hidden border (spaces)."
  (border {:top " " :bottom " " :left " " :right " "
           :top-left " " :top-right " " :bottom-left " " :bottom-right " "}))

;; ---------------------------------------------------------------------------
;; Border Rendering
;; ---------------------------------------------------------------------------

(defn- max-char-width
  "Get the maximum display width of any character in a string."
  [s]
  (if (or (nil? s) (empty? s))
    0
    (let [clusters (w/graphemes s)]
      (if clusters
        (reduce max 0 (map w/grapheme-width clusters))
        0))))

(defn- render-horizontal-edge
  "Render a horizontal border edge (top or bottom)."
  [left-corner middle right-corner width]
  (let [middle (if (empty? middle) " " middle)
        left-width (w/string-width left-corner)
        right-width (w/string-width right-corner)
        middle-width (- width left-width right-width)
        middle-chars (w/graphemes middle)
        middle-count (count middle-chars)]
    (loop [result (StringBuilder. ^String left-corner)
           current-width (long 0)
           idx 0]
      (if (>= current-width middle-width)
        (str result right-corner)
        (let [c (nth middle-chars (mod idx middle-count))
              cw (w/grapheme-width c)]
          (recur (.append result c)
                 (long (+ current-width cw))
                 (inc idx)))))))

(defn- style-text
  "Apply foreground and background color to text."
  [text fg bg]
  (let [fg-seq (color/color->fg-seq fg)
        bg-seq (color/color->bg-seq bg)
        reset "\u001b[0m"]
    (str (or fg-seq "") (or bg-seq "") text reset)))

(defn apply-border
  "Apply a border around text content.

   Options:
     :border      - Border definition (default: normal)
     :top?        - Show top border (default: true)
     :right?      - Show right border (default: true)
     :bottom?     - Show bottom border (default: true)
     :left?       - Show left border (default: true)
     :fg          - Border foreground color
     :bg          - Border background color"
  [text & {:keys [border top? right? bottom? left? fg bg]
           :or {border normal
                top? true right? true bottom? true left? true}}]
  (let [lines (str/split-lines text)
        ;; Calculate content width
        content-width (reduce max 0 (map w/string-width lines))
        ;; Get border parts
        {:keys [top bottom left right
                top-left top-right bottom-left bottom-right]} border
        ;; Apply defaults for missing corners/edges
        left (if (and left? (empty? left)) " " left)
        right (if (and right? (empty? right)) " " right)
        top-left (if (and top? left? (empty? top-left)) " " top-left)
        top-right (if (and top? right? (empty? top-right)) " " top-right)
        bottom-left (if (and bottom? left? (empty? bottom-left)) " " bottom-left)
        bottom-right (if (and bottom? right? (empty? bottom-right)) " " bottom-right)
        ;; Calculate total width including borders
        left-width (if left? (max-char-width left) 0)
        right-width (if right? (max-char-width right) 0)
        total-width (+ content-width left-width right-width)
        ;; Style helper
        style-border (fn [s] (if (or fg bg) (style-text s fg bg) s))]
    (str/join
     "\n"
     (concat
      ;; Top border
      (when top?
        [(style-border
          (render-horizontal-edge
           (if left? top-left "")
           top
           (if right? top-right "")
           total-width))])
      ;; Content lines with side borders
      (for [line lines]
        (str (when left? (style-border left))
             line
             (when right? (style-border right))))
      ;; Bottom border
      (when bottom?
        [(style-border
          (render-horizontal-edge
           (if left? bottom-left "")
           bottom
           (if right? bottom-right "")
           total-width))])))))

;; ---------------------------------------------------------------------------
;; Border Size Calculation
;; ---------------------------------------------------------------------------

(defn border-width
  "Calculate the total horizontal width added by a border."
  [border & {:keys [left? right?] :or {left? true right? true}}]
  (+ (if left? (max-char-width (:left border)) 0)
     (if right? (max-char-width (:right border)) 0)))

(defn border-height
  "Calculate the total vertical height added by a border."
  [& {:keys [top? bottom?] :or {top? true bottom? true}}]
  (+ (if top? 1 0)
     (if bottom? 1 0)))
