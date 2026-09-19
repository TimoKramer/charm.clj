(ns ^:no-doc charm.ansi.width
  "Text width calculation for terminal display.

   Handles:
   - ANSI escape sequences (zero width)
   - Wide characters (CJK, emojis = 2 cells)
   - Combining characters (zero width)
   - Grapheme clusters (emoji sequences)"
  (:require
   [charm.ansi.sanitize :as san])
  (:import
   [org.jline.utils AttributedString]))

(defn strip-ansi
  "Remove ANSI escape sequences from a string, leaving the text a terminal
   would actually display.

   See `charm.ansi.sanitize/strip`: this removes OSC, DCS and the rest as well
   as the styling codes, so it is safe to reach for when displaying data the
   application did not author."
  [s]
  (san/strip s))

(defn column-length
  "Get the display width of an AttributedString."
  [^AttributedString attr-s]
  (.columnLength attr-s))

(defn column-sub-sequence
  "Get a column-based subsequence of an AttributedString."
  [^AttributedString attr-s start end]
  (.columnSubSequence attr-s (int start) (int end)))

(defn string-width
  "Measure the display width of a string in terminal cells.

   - ANSI escape sequences have zero width
   - Wide characters (CJK, emojis) count as 2 cells
   - Combining characters count as 0 cells
   - Grapheme clusters (ZWJ emoji) count as 2 cells

   Sequences JLine's SGR parser does not recognise are sanitized away first, so
   the measurement matches what the terminal will be given rather than counting
   an OSC's bytes as text.

   Example:
     (string-width \"hello\")     ; => 5
     (string-width \"你好\")       ; => 4 (2 wide chars)
     (string-width \"\\033[31mhi\") ; => 2 (ANSI ignored)"
  [s]
  (if (or (nil? s) (empty? s))
    0
    (column-length (AttributedString/fromAnsi (san/sanitize s)))))

(defn truncate
  "Truncate a string to fit within a given display width.

   Options:
     :tail - String to append when truncated (default \"...\")

   The tail is included in the width calculation.

   Example:
     (truncate \"hello world\" 8)           ; => \"hello...\"
     (truncate \"hello world\" 8 :tail \"…\") ; => \"hello w…\""
  [s width & {:keys [tail] :or {tail "..."}}]
  (if (nil? s)
    s
    (let [s (san/sanitize s)
          attr-s (AttributedString/fromAnsi s)]
      (if (<= (column-length attr-s) width)
        s
        (let [tail-width (string-width tail)
              target-width (- width tail-width)]
          (if (neg? target-width)
            ""
            (str (column-sub-sequence attr-s 0 target-width) tail)))))))

(defn repeat-char
  "Build a string of `n` copies of `c`, without going through a seq."
  ^String [c n]
  (.repeat ^String (str c) (int n)))

(defn pad-right
  "Pad a string on the right to reach a target display width."
  [s width & {:keys [char] :or {char \space}}]
  (let [current (string-width s)
        needed (- width current)]
    (if (pos? needed)
      (str s (repeat-char char needed))
      s)))

(defn pad-left
  "Pad a string on the left to reach a target display width."
  [s width & {:keys [char] :or {char \space}}]
  (let [current (string-width s)
        needed (- width current)]
    (if (pos? needed)
      (str (repeat-char char needed) s)
      s)))
