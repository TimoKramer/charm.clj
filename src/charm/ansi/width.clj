(ns charm.ansi.width
  "Text width calculation for terminal display.

   Handles:
   - ANSI escape sequences (zero width)
   - Wide characters (CJK, emojis = 2 cells)
   - Combining characters (zero width)
   - Grapheme clusters (emoji sequences)"
  (:import [com.ibm.icu.text BreakIterator]
           [java.lang Character$UnicodeBlock]))

;; ---------------------------------------------------------------------------
;; ANSI Escape Sequence Handling
;; ---------------------------------------------------------------------------

(def ^:private ansi-pattern
  "Regex pattern matching ANSI escape sequences.
   Matches: CSI sequences, OSC sequences, and simple escapes."
  #"\x1b(?:\[[0-9;?]*[A-Za-z]|\][^\x07]*(?:\x07|\x1b\\)|\[[^\x1b]*|[^\[])|\x1b")

(defn strip-ansi
  "Remove ANSI escape sequences from a string."
  [s]
  (if (nil? s)
    ""
    (clojure.string/replace s ansi-pattern "")))

;; ---------------------------------------------------------------------------
;; Character Width Calculation
;; ---------------------------------------------------------------------------

(defn- zero-width-char?
  "Check if a code point is a zero-width character."
  [^long cp]
  (or
   ;; Combining marks
   (= (Character/getType cp) Character/NON_SPACING_MARK)
   (= (Character/getType cp) Character/ENCLOSING_MARK)
   (= (Character/getType cp) Character/COMBINING_SPACING_MARK)
   ;; Zero-width characters
   (= cp 0x200B)  ; ZERO WIDTH SPACE
   (= cp 0x200C)  ; ZERO WIDTH NON-JOINER
   (= cp 0x200D)  ; ZERO WIDTH JOINER
   (= cp 0xFEFF)  ; ZERO WIDTH NO-BREAK SPACE (BOM)
   ;; Control characters
   (= (Character/getType cp) Character/CONTROL)))

(defn- wide-char?
  "Check if a code point is a wide (2-cell) character."
  [^long cp]
  (let [block (Character$UnicodeBlock/of cp)]
    (or
     ;; CJK characters
     (= block Character$UnicodeBlock/CJK_UNIFIED_IDEOGRAPHS)
     (= block Character$UnicodeBlock/CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A)
     (= block Character$UnicodeBlock/CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B)
     (= block Character$UnicodeBlock/CJK_COMPATIBILITY)
     (= block Character$UnicodeBlock/CJK_COMPATIBILITY_FORMS)
     (= block Character$UnicodeBlock/CJK_COMPATIBILITY_IDEOGRAPHS)
     (= block Character$UnicodeBlock/CJK_RADICALS_SUPPLEMENT)
     (= block Character$UnicodeBlock/CJK_SYMBOLS_AND_PUNCTUATION)
     (= block Character$UnicodeBlock/ENCLOSED_CJK_LETTERS_AND_MONTHS)
     ;; Japanese
     (= block Character$UnicodeBlock/HIRAGANA)
     (= block Character$UnicodeBlock/KATAKANA)
     (= block Character$UnicodeBlock/KATAKANA_PHONETIC_EXTENSIONS)
     ;; Korean
     (= block Character$UnicodeBlock/HANGUL_SYLLABLES)
     (= block Character$UnicodeBlock/HANGUL_JAMO)
     (= block Character$UnicodeBlock/HANGUL_COMPATIBILITY_JAMO)
     ;; Full-width forms
     (= block Character$UnicodeBlock/HALFWIDTH_AND_FULLWIDTH_FORMS)
     ;; Emojis and pictographs
     (= block Character$UnicodeBlock/EMOTICONS)
     (= block Character$UnicodeBlock/MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS)
     (= block Character$UnicodeBlock/TRANSPORT_AND_MAP_SYMBOLS)
     (= block Character$UnicodeBlock/SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS)
     (= block Character$UnicodeBlock/DINGBATS)
     ;; Supplementary characters (often emojis)
     (Character/isSupplementaryCodePoint cp))))

(defn char-width
  "Get the display width of a single code point.
   Returns 0, 1, or 2."
  [^long cp]
  (cond
    (zero-width-char? cp) 0
    (wide-char? cp) 2
    :else 1))

;; ---------------------------------------------------------------------------
;; Grapheme Cluster Handling
;; ---------------------------------------------------------------------------

(defn graphemes
  "Split a string into grapheme clusters.
   A grapheme cluster is what users perceive as a single character,
   which may consist of multiple Unicode code points (e.g., emoji sequences)."
  [^String s]
  (when (and s (pos? (count s)))
    (let [iter (BreakIterator/getCharacterInstance)]
      (.setText iter s)
      (loop [start (.first iter)
             clusters []]
        (let [end (.next iter)]
          (if (= end BreakIterator/DONE)
            clusters
            (recur end (conj clusters (.substring s start end)))))))))

(defn grapheme-width
  "Get the display width of a grapheme cluster."
  [^String grapheme]
  (if (or (nil? grapheme) (empty? grapheme))
    0
    (let [cp (.codePointAt grapheme 0)]
      (char-width cp))))

;; ---------------------------------------------------------------------------
;; String Width Measurement
;; ---------------------------------------------------------------------------

(defn string-width
  "Measure the display width of a string in terminal cells.

   - ANSI escape sequences have zero width
   - Wide characters (CJK, emojis) count as 2 cells
   - Combining characters count as 0 cells

   Example:
     (string-width \"hello\")     ; => 5
     (string-width \"你好\")       ; => 4 (2 wide chars)
     (string-width \"\\033[31mhi\") ; => 2 (ANSI ignored)"
  [s]
  (if (or (nil? s) (empty? s))
    0
    (let [stripped (strip-ansi s)
          clusters (graphemes stripped)]
      (reduce + 0 (map grapheme-width clusters)))))

;; ---------------------------------------------------------------------------
;; Truncation
;; ---------------------------------------------------------------------------

(defn truncate
  "Truncate a string to fit within a given display width.

   Options:
     :tail - String to append when truncated (default \"...\")

   The tail is included in the width calculation.

   Example:
     (truncate \"hello world\" 8)           ; => \"hello...\"
     (truncate \"hello world\" 8 :tail \"…\") ; => \"hello w…\""
  [s width & {:keys [tail] :or {tail "..."}}]
  (if (or (nil? s) (<= (string-width s) width))
    s
    (let [tail-width (string-width tail)
          target-width (- width tail-width)]
      (if (neg? target-width)
        ""
        (let [stripped (strip-ansi s)
              clusters (graphemes stripped)]
          (loop [result []
                 remaining clusters
                 current-width 0]
            (if (empty? remaining)
              (str (apply str result) tail)
              (let [g (first remaining)
                    gw (grapheme-width g)
                    new-width (+ current-width gw)]
                (if (> new-width target-width)
                  (str (apply str result) tail)
                  (recur (conj result g)
                         (rest remaining)
                         new-width))))))))))

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
