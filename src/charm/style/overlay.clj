(ns charm.style.overlay
  "Layout utility for compositing a floating panel on top of base content.

   Not a stateful component - just pure functions for overlay placement.

   Usage:
     (place-overlay base-text overlay-text 5 3)
     (center-overlay base-text overlay-text)"
  (:require
   [charm.ansi.width :as w]
   [clojure.string :as str])
  (:import
   [org.jline.utils AttributedString AttributedStringBuilder]))

;; ---------------------------------------------------------------------------
;; Internal Helpers
;; ---------------------------------------------------------------------------

(defn- split-lines
  "Split text into lines, handling empty strings."
  [s]
  (if (or (nil? s) (empty? s))
    [""]
    (str/split-lines s)))

(defn- text-dimensions
  "Get [width height] of a text block."
  [s]
  (let [lines (split-lines s)]
    [(reduce max 0 (map w/string-width lines))
     (count lines)]))

(defn- overlay-line
  "Overlay a single line onto a base line at column x.

   Uses JLine's AttributedString to handle ANSI sequences correctly:
   takes the base line, replaces columns x..x+overlay-width with the
   overlay content."
  [base-line overlay-line-str x]
  (let [base-attr (AttributedString/fromAnsi base-line)
        overlay-attr (AttributedString/fromAnsi overlay-line-str)
        base-width (w/column-length base-attr)
        overlay-width (w/column-length overlay-attr)
        builder (AttributedStringBuilder.)]
    ;; Part before overlay
    (when (pos? x)
      (let [before-width (min x base-width)]
        (.append builder (w/column-sub-sequence base-attr 0 before-width))
        ;; Pad if base is shorter than x
        (when (< base-width x)
          (.append builder (apply str (repeat (- x base-width) " "))))))
    ;; Overlay content
    (.append builder overlay-attr)
    ;; Part after overlay
    (let [after-start (+ x overlay-width)]
      (when (< after-start base-width)
        (.append builder (w/column-sub-sequence base-attr after-start base-width))))
    (.toAnsi (.toAttributedString builder))))

;; ---------------------------------------------------------------------------
;; Public API
;; ---------------------------------------------------------------------------

(defn place-overlay
  "Composite an overlay text on top of base text at position (x, y).

   Both base and overlay may contain ANSI escape sequences.
   The overlay replaces the corresponding region of the base content.

   Returns the composited string."
  [base overlay x y]
  (let [base-lines (split-lines base)
        overlay-lines (split-lines overlay)
        base-height (count base-lines)
        result (mapv (fn [row-idx base-line]
                       (let [overlay-idx (- row-idx y)]
                         (if (and (>= overlay-idx 0)
                                  (< overlay-idx (count overlay-lines)))
                           (overlay-line base-line (nth overlay-lines overlay-idx) x)
                           base-line)))
                     (range base-height)
                     base-lines)]
    (str/join "\n" result)))

(defn center-overlay
  "Composite an overlay centered on the base content.

   Both base and overlay may contain ANSI escape sequences."
  [base overlay]
  (let [[base-w base-h] (text-dimensions base)
        [overlay-w overlay-h] (text-dimensions overlay)
        x (max 0 (quot (- base-w overlay-w) 2))
        y (max 0 (quot (- base-h overlay-h) 2))]
    (place-overlay base overlay x y)))

(defn place-overlay-position
  "Composite an overlay at a named position.

   Position can be :center, :top-left, :top-right, :bottom-left, :bottom-right,
   :top-center, :bottom-center, :center-left, :center-right."
  [base overlay position]
  (let [[base-w base-h] (text-dimensions base)
        [overlay-w overlay-h] (text-dimensions overlay)
        [x y] (case position
                :center       [(quot (- base-w overlay-w) 2)
                               (quot (- base-h overlay-h) 2)]
                :top-left     [0 0]
                :top-right    [(- base-w overlay-w) 0]
                :top-center   [(quot (- base-w overlay-w) 2) 0]
                :bottom-left  [0 (- base-h overlay-h)]
                :bottom-right [(- base-w overlay-w) (- base-h overlay-h)]
                :bottom-center [(quot (- base-w overlay-w) 2)
                                (- base-h overlay-h)]
                :center-left  [0 (quot (- base-h overlay-h) 2)]
                :center-right [(- base-w overlay-w)
                               (quot (- base-h overlay-h) 2)])]
    (place-overlay base overlay (max 0 x) (max 0 y))))
