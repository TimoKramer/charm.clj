(ns charm.style.layout
  "Layout utilities: padding, margin, alignment, and joining."
  (:require [charm.ansi.width :as w]
            [charm.style.color :as color]
            [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Position Constants
;; ---------------------------------------------------------------------------

(def left :left)
(def center :center)
(def right :right)
(def top :top)
(def bottom :bottom)

;; ---------------------------------------------------------------------------
;; Box Value Expansion (CSS-style)
;; ---------------------------------------------------------------------------

(defn expand-box-values
  "Expand 1-4 values to [top right bottom left] (CSS box model).

   1 value:  [a]       -> [a a a a]
   2 values: [a b]     -> [a b a b]
   3 values: [a b c]   -> [a b c b]
   4 values: [a b c d] -> [a b c d]"
  [values]
  (case (count values)
    1 (let [a (first values)] [a a a a])
    2 (let [[a b] values] [a b a b])
    3 (let [[a b c] values] [a b c b])
    4 (vec values)
    (throw (ex-info "Expected 1-4 values" {:values values}))))

;; ---------------------------------------------------------------------------
;; Text Measurement
;; ---------------------------------------------------------------------------

(defn- split-lines
  "Split text into lines, handling empty strings."
  [s]
  (if (empty? s)
    [""]
    (str/split-lines s)))

(defn- widest-line
  "Get the width of the widest line in text."
  [s]
  (reduce max 0 (map w/string-width (split-lines s))))

(defn- line-count
  "Count lines in text."
  [s]
  (count (split-lines s)))

;; ---------------------------------------------------------------------------
;; Padding
;; ---------------------------------------------------------------------------

(defn pad
  "Add padding around text.

   Accepts either:
   - (pad text [top right bottom left])
   - (pad text top right bottom left)

   Options:
     :bg - Background color for padding"
  ([text values] (apply pad text values))
  ([text top-pad right-pad bottom-pad left-pad & {:keys [bg]}]
   (let [lines (split-lines text)
         content-width (widest-line text)
         bg-seq (color/color->bg-seq bg)
         reset (when bg-seq "\u001b[0m")
         ;; Build padding strings
         left-str (apply str (repeat left-pad " "))
         right-str (apply str (repeat right-pad " "))
         full-width (+ left-pad content-width right-pad)
         empty-line (apply str (repeat full-width " "))
         ;; Wrap line with padding
         wrap-line (fn [line]
                     (let [line-width (w/string-width line)
                           line-right-pad (apply str (repeat (+ right-pad (- content-width line-width)) " "))]
                       (str (or bg-seq "") left-str line line-right-pad (or reset ""))))
         ;; Create top/bottom padding lines
         pad-line (str (or bg-seq "") empty-line (or reset ""))]
     (str/join
      "\n"
      (concat
       (repeat top-pad pad-line)
       (map wrap-line lines)
       (repeat bottom-pad pad-line))))))

;; ---------------------------------------------------------------------------
;; Margin
;; ---------------------------------------------------------------------------

(defn margin
  "Add margin around text.

   Accepts either:
   - (margin text [top right bottom left])
   - (margin text top right bottom left)

   Options:
     :bg - Background color for margin"
  ([text values] (apply margin text values))
  ([text top-margin right-margin bottom-margin left-margin & {:keys [bg]}]
   (let [lines (split-lines text)
         content-width (widest-line text)
         bg-seq (color/color->bg-seq bg)
         reset (when bg-seq "\u001b[0m")
         ;; Build margin strings
         left-str (apply str (repeat left-margin " "))
         right-str (apply str (repeat right-margin " "))
         full-width (+ left-margin content-width right-margin)
         empty-line (apply str (repeat full-width " "))
         ;; Wrap line with margin
         wrap-line (fn [line]
                     (let [line-width (w/string-width line)
                           line-right-margin (apply str (repeat (+ right-margin (- content-width line-width)) " "))]
                       (str (or bg-seq "") left-str (or reset "")
                            line
                            (or bg-seq "") line-right-margin (or reset ""))))
         ;; Create top/bottom margin lines
         margin-line (str (or bg-seq "") empty-line (or reset ""))]
     (str/join
      "\n"
      (concat
       (repeat top-margin margin-line)
       (map wrap-line lines)
       (repeat bottom-margin margin-line))))))

;; ---------------------------------------------------------------------------
;; Horizontal Alignment
;; ---------------------------------------------------------------------------

(defn align-horizontal
  "Align text horizontally within a given width.

   Position can be :left, :center, or :right.

   Options:
     :bg - Background color for fill"
  [text width position & {:keys [bg]}]
  (let [bg-seq (color/color->bg-seq bg)
        reset (when bg-seq "\u001b[0m")]
    (str/join
     "\n"
     (for [line (split-lines text)]
       (let [line-width (w/string-width line)
             total-pad (max 0 (- width line-width))
             [left-pad right-pad] (case position
                                    :left [0 total-pad]
                                    :right [total-pad 0]
                                    :center [(quot total-pad 2)
                                             (- total-pad (quot total-pad 2))])]
         (str (or bg-seq "")
              (apply str (repeat left-pad " "))
              (or reset "")
              line
              (or bg-seq "")
              (apply str (repeat right-pad " "))
              (or reset "")))))))

;; ---------------------------------------------------------------------------
;; Vertical Alignment
;; ---------------------------------------------------------------------------

(defn align-vertical
  "Align text vertically within a given height.

   Position can be :top, :center, or :bottom."
  [text height position]
  (let [lines (split-lines text)
        current-height (count lines)
        total-pad (max 0 (- height current-height))
        [top-pad bottom-pad] (case position
                               :top [0 total-pad]
                               :bottom [total-pad 0]
                               :center [(quot total-pad 2)
                                        (- total-pad (quot total-pad 2))])
        empty-lines (repeat total-pad "")]
    (str/join
     "\n"
     (concat
      (take top-pad empty-lines)
      lines
      (take bottom-pad empty-lines)))))

;; ---------------------------------------------------------------------------
;; Joining
;; ---------------------------------------------------------------------------

(defn join-horizontal
  "Join multiple text blocks horizontally.

   Position specifies vertical alignment: :top, :center, or :bottom."
  [position & texts]
  (let [blocks (map split-lines texts)
        heights (map count blocks)
        max-height (reduce max 0 heights)
        ;; Pad each block to same height
        padded-blocks (for [block blocks]
                        (let [block-height (count block)
                              pad-count (- max-height block-height)
                              width (reduce max 0 (map w/string-width block))
                              empty-line (apply str (repeat width " "))
                              [top-pad bottom-pad] (case position
                                                     :top [0 pad-count]
                                                     :bottom [pad-count 0]
                                                     :center [(quot pad-count 2)
                                                              (- pad-count (quot pad-count 2))])]
                          (vec (concat
                                (repeat top-pad empty-line)
                                block
                                (repeat bottom-pad empty-line)))))]
    (str/join
     "\n"
     (for [i (range max-height)]
       (str/join (map #(nth % i "") padded-blocks))))))

(defn join-vertical
  "Join multiple text blocks vertically.

   Position specifies horizontal alignment: :left, :center, or :right."
  [position & texts]
  (let [blocks (map split-lines texts)
        widths (for [block blocks]
                 (reduce max 0 (map w/string-width block)))
        max-width (reduce max 0 widths)
        ;; Pad each block to same width
        padded-blocks (for [block blocks]
                        (for [line block]
                          (let [line-width (w/string-width line)
                                pad-count (- max-width line-width)
                                [left-pad right-pad] (case position
                                                       :left [0 pad-count]
                                                       :right [pad-count 0]
                                                       :center [(quot pad-count 2)
                                                                (- pad-count (quot pad-count 2))])]
                            (str (apply str (repeat left-pad " "))
                                 line
                                 (apply str (repeat right-pad " "))))))]
    (str/join "\n" (apply concat padded-blocks))))
