(ns charm.render.screen
  "Screen buffer and ANSI control sequences.

   Provides low-level terminal control operations and
   screen buffer management for efficient rendering."
  (:require [charm.ansi.width :as w]
            [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; ANSI Control Sequences
;; ---------------------------------------------------------------------------

(def ^:const CSI "\u001b[")
(def ^:const ESC "\u001b")

;; Cursor movement
(defn cursor-up [n] (str CSI n "A"))
(defn cursor-down [n] (str CSI n "B"))
(defn cursor-forward [n] (str CSI n "C"))
(defn cursor-back [n] (str CSI n "D"))
(defn cursor-to [row col] (str CSI row ";" col "H"))
(def cursor-home (str CSI "H"))
(def cursor-save (str CSI "s"))
(def cursor-restore (str CSI "u"))

;; Cursor visibility
(def cursor-hide (str CSI "?25l"))
(def cursor-show (str CSI "?25h"))

;; Screen clearing
(def clear-screen (str CSI "2J"))
(def clear-line (str CSI "K"))
(def clear-line-before (str CSI "1K"))
(def clear-line-full (str CSI "2K"))
(def clear-below (str CSI "J"))

;; Alt screen
(def enter-alt-screen (str CSI "?1049h"))
(def exit-alt-screen (str CSI "?1049l"))

;; Mouse
(def enable-mouse-normal (str CSI "?1000h"))
(def disable-mouse-normal (str CSI "?1000l"))
(def enable-mouse-cell-motion (str CSI "?1002h"))
(def disable-mouse-cell-motion (str CSI "?1002l"))
(def enable-mouse-all-motion (str CSI "?1003h"))
(def disable-mouse-all-motion (str CSI "?1003l"))
(def enable-mouse-sgr (str CSI "?1006h"))
(def disable-mouse-sgr (str CSI "?1006l"))

;; Focus reporting
(def enable-focus-reporting (str CSI "?1004h"))
(def disable-focus-reporting (str CSI "?1004l"))

;; Bracketed paste
(def enable-bracketed-paste (str CSI "?2004h"))
(def disable-bracketed-paste (str CSI "?2004l"))

;; Reset
(def reset (str CSI "0m"))

;; Window title
(defn set-window-title [title]
  (str ESC "]2;" title "\u0007"))

;; Clipboard (OSC 52)
(defn copy-to-clipboard [text]
  (let [encoder (java.util.Base64/getEncoder)
        bytes (.getBytes text "UTF-8")
        encoded (.encodeToString encoder bytes)]
    (str ESC "]52;c;" encoded "\u0007")))

;; ---------------------------------------------------------------------------
;; Screen Buffer
;; ---------------------------------------------------------------------------

(defn create-screen
  "Create a screen buffer with the given dimensions."
  [width height]
  {:width width
   :height height
   :lines (vec (repeat height ""))
   :cursor-x 0
   :cursor-y 0})

(defn set-line
  "Set a line in the screen buffer."
  [screen y content]
  (if (and (>= y 0) (< y (:height screen)))
    (assoc-in screen [:lines y] content)
    screen))

(defn get-line
  "Get a line from the screen buffer."
  [screen y]
  (get-in screen [:lines y]))

(defn write-at
  "Write content at a specific position in the screen buffer."
  [screen x y content]
  (let [line (get-line screen y)
        ;; Pad line to reach x position if needed
        padded (if (< (count line) x)
                 (str line (apply str (repeat (- x (count line)) " ")))
                 line)
        new-line (str (subs padded 0 x) content)]
    (set-line screen y new-line)))

(defn clear
  "Clear the screen buffer."
  [screen]
  (assoc screen :lines (vec (repeat (:height screen) ""))))

;; ---------------------------------------------------------------------------
;; Line Diffing
;; ---------------------------------------------------------------------------

(defn lines-diff
  "Compare two line arrays and return indices of changed lines."
  [old-lines new-lines]
  (let [max-len (max (count old-lines) (count new-lines))]
    (filter some?
            (for [i (range max-len)]
              (let [old (get old-lines i)
                    new (get new-lines i)]
                (when (not= old new)
                  i))))))

;; ---------------------------------------------------------------------------
;; Content Rendering
;; ---------------------------------------------------------------------------

(defn content->lines
  "Split content into lines, handling CRLF and LF."
  [content]
  (-> content
      (str/replace "\r\n" "\n")
      (str/split-lines)))

(defn truncate-line
  "Truncate a line to fit within terminal width."
  [line width]
  (if (or (<= width 0) (<= (w/string-width line) width))
    line
    (w/truncate line width :tail "")))

(defn fit-content
  "Fit content to screen dimensions.
   Truncates lines to width and limits to height."
  [content width height]
  (let [lines (content->lines content)
        ;; Truncate to height (keep last lines if overflow)
        lines (if (> (count lines) height)
                (subvec (vec lines) (- (count lines) height))
                lines)
        ;; Truncate each line to width
        lines (mapv #(truncate-line % width) lines)]
    lines))
