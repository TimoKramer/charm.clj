(ns charm.ansi.sanitize
  "Make text safe to write to a terminal.

   A TUI usually renders data its user did not author - filenames, log lines,
   HTTP responses. Escape sequences in that data are instructions to the
   terminal, not characters: OSC 52 writes the attacker's data into the user's
   system clipboard, OSC 8 hyperlinks text to somewhere other than what it
   says, OSC 2 rewrites the window title, and CSI 2K with a carriage return
   erases the line the content just drew.

   `sanitize` keeps the one sequence family that only paints characters - SGR,
   the styling codes - and drops the rest. `strip` drops those too, leaving the
   text that will actually be displayed.

   Both return the argument unchanged when there is nothing to remove, so the
   common case of plain or styled text costs one scan and no allocation."
  (:import
   [java.lang StringBuilder]))

;; ---------------------------------------------------------------------------
;; Character Classes
;; ---------------------------------------------------------------------------

(def ^:private ^:const ESC 0x1b)
(def ^:private ^:const BEL 0x07)

(defn- keep-control?
  "Is this control character one we pass through?

   Newline and tab lay out text; every other one - carriage return, backspace,
   BEL, the shift codes - moves or reprograms the terminal."
  [^long c]
  (or (= c 0x0a) (= c 0x09)))

(defn- control?
  "C0, DEL, or a C1 control character."
  [^long c]
  (or (< c 0x20) (= c 0x7f) (<= 0x80 c 0x9f)))

(defn- csi-final?
  "Does this byte end a CSI sequence? (0x40-0x7e)"
  [^long c]
  (<= 0x40 c 0x7e))

;; ---------------------------------------------------------------------------
;; Scanning
;; ---------------------------------------------------------------------------

(defn- skip-csi
  "Index just past the CSI sequence starting at `from` (the byte after ESC [)."
  ^long [^String s ^long from]
  (let [len (.length s)]
    (loop [i from]
      (cond
        (>= i len) len
        (csi-final? (int (.charAt s i))) (inc i)
        :else (recur (inc i))))))

(defn- skip-string-sequence
  "Index just past a string-terminated sequence - OSC, DCS, APC, PM or SOS -
   whose data starts at `from`. These end at BEL or at ST (ESC \\), and an
   unterminated one swallows the rest of the string, which is what a terminal
   does with it too."
  ^long [^String s ^long from]
  (let [len (.length s)]
    (loop [i from]
      (if (>= i len)
        len
        (let [c (int (.charAt s i))]
          (cond
            (= c BEL) (inc i)
            (and (= c ESC) (< (inc i) len) (= (.charAt s (inc i)) \\)) (+ i 2)
            :else (recur (inc i))))))))

(defn- sgr?
  "Is the CSI sequence between `from` and `end` an SGR (styling) sequence?
   That is the one family that only paints characters."
  [^String s ^long from ^long end]
  (and (> end from) (= \m (.charAt s (dec end)))))

(defn- dropping
  "The builder to collect into, seeded with the `i` characters kept so far.
   Called on the first drop, which is what keeps the clean case
   allocation-free."
  ^StringBuilder [^StringBuilder sb ^String s ^long i]
  (or sb (doto (StringBuilder. (.length s)) (.append s 0 i))))

(defn- scan
  "Copy `s` minus the sequences the policy rejects.

   `keep-sgr?` decides whether SGR (CSI ... m) survives. Returns `s` itself
   when nothing was dropped."
  [^String s keep-sgr?]
  (let [len (.length s)]
    (loop [i 0
           ^StringBuilder sb nil]
      (if (>= i len)
        (if sb (.toString sb) s)
        (let [c (int (.charAt s i))]
          (cond
            ;; An escape sequence: keep only SGR, and only if asked to
            (= c ESC)
            (let [n (inc i)
                  intro (when (< n len) (.charAt s n))]
              (case intro
                \[ (let [end (skip-csi s (inc n))]
                     (if (and keep-sgr? (sgr? s i end))
                       (do (when sb (.append sb s i end))
                           (recur end sb))
                       (recur end (dropping sb s i))))

                (\] \P \_ \^ \X)
                (recur (skip-string-sequence s (inc n)) (dropping sb s i))

                ;; ESC at the very end of the string
                nil (recur (inc i) (dropping sb s i))

                ;; Two-character escapes like ESC c (full reset), and anything
                ;; unrecognised
                (recur (+ n 1) (dropping sb s i))))

            ;; A bare control character: newline and tab lay out text, the rest
            ;; move or reprogram the terminal
            (control? c)
            (if (keep-control? c)
              (do (when sb (.append sb (char c)))
                  (recur (inc i) sb))
              (recur (inc i) (dropping sb s i)))

            ;; Ordinary text - the hot path, which must not allocate
            :else
            (do (when sb (.append sb (char c)))
                (recur (inc i) sb))))))))

;; ---------------------------------------------------------------------------
;; Public API
;; ---------------------------------------------------------------------------

(defn sanitize
  "Remove every escape sequence except SGR styling, and every control
   character except newline and tab.

   Use this on any text a terminal will display that the application did not
   author itself. Styling survives, so already-styled content passes through
   unchanged."
  [s]
  (if (nil? s) "" (scan s true)))

(defn strip
  "Remove every escape sequence, including SGR styling, and every control
   character except newline and tab.

   What is left is the text the terminal would actually display."
  [s]
  (if (nil? s) "" (scan s false)))

(defn strip-controls
  "Remove every control character, including newline and tab, and with them
   any escape sequence.

   For strings that go inside a sequence charm writes itself - a window title,
   where a BEL or an ESC would end the OSC early and let the rest of the
   argument start a new one."
  [s]
  (if (nil? s)
    ""
    (let [len (.length ^String s)]
      (loop [i 0
             sb nil]
        (if (>= i len)
          (if sb (.toString ^StringBuilder sb) s)
          (let [c (int (.charAt ^String s i))]
            (if (control? c)
              (recur (inc i)
                     (or sb (doto (StringBuilder. len) (.append ^String s 0 i))))
              (do (when sb (.append ^StringBuilder sb (char c)))
                  (recur (inc i) sb)))))))))
