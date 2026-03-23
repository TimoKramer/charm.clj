(ns charm.ansi.width
  "Text width calculation for terminal display.

   Handles:
   - ANSI escape sequences (zero width)
   - Wide characters (CJK, emojis = 2 cells)
   - Combining characters (zero width)
   - Grapheme clusters (emoji sequences, via JLine 4 Mode 2027)"
  (:import
   [org.jline.terminal Terminal]
   [org.jline.utils AttributedString]))

(def ^:dynamic *terminal*
  "Bound terminal for grapheme-cluster-aware width calculation.
   When bound, JLine uses Mode 2027 grapheme clustering if the terminal
   supports it. When nil, falls back to per-codepoint wcwidth."
  nil)

(defn strip-ansi
  "Remove ANSI escape sequences from a string."
  [s]
  (if (nil? s)
    ""
    (.toString (AttributedString/fromAnsi s))))

(defn column-length
  "Get the display width of an AttributedString.
   Uses grapheme clustering when *terminal* is bound and supports Mode 2027."
  [^AttributedString attr-s]
  (if *terminal*
    (.columnLength attr-s ^Terminal *terminal*)
    (.columnLength attr-s)))

(defn column-sub-sequence
  "Get a column-based subsequence of an AttributedString.
   Uses grapheme clustering when *terminal* is bound and supports Mode 2027."
  [^AttributedString attr-s start end]
  (if *terminal*
    (.columnSubSequence attr-s (int start) (int end) ^Terminal *terminal*)
    (.columnSubSequence attr-s (int start) (int end))))

(defn string-width
  "Measure the display width of a string in terminal cells.

   - ANSI escape sequences have zero width
   - Wide characters (CJK, emojis) count as 2 cells
   - Combining characters count as 0 cells
   - Grapheme clusters (ZWJ emoji) count as 2 cells when *terminal* is bound

   Example:
     (string-width \"hello\")     ; => 5
     (string-width \"你好\")       ; => 4 (2 wide chars)
     (string-width \"\\033[31mhi\") ; => 2 (ANSI ignored)"
  [s]
  (if (or (nil? s) (empty? s))
    0
    (column-length (AttributedString/fromAnsi s))))

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
    (let [attr-s (AttributedString/fromAnsi s)]
      (if (<= (column-length attr-s) width)
        s
        (let [tail-width (string-width tail)
              target-width (- width tail-width)]
          (if (neg? target-width)
            ""
            (str (column-sub-sequence attr-s 0 target-width) tail)))))))

(defn pad-right
  "Pad a string on the right to reach a target display width."
  [s width & {:keys [char] :or {char \space}}]
  (let [current (string-width s)
        needed (- width current)]
    (if (pos? needed)
      (str s (apply str (repeat needed char)))
      s)))

(defn pad-left
  "Pad a string on the left to reach a target display width."
  [s width & {:keys [char] :or {char \space}}]
  (let [current (string-width s)
        needed (- width current)]
    (if (pos? needed)
      (str (apply str (repeat needed char)) s)
      s)))
