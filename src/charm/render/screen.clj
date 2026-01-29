(ns charm.render.screen
  "ANSI control sequences for terminal features without JLine capability equivalents.

   For cursor movement, screen clearing, and alt screen, use charm.terminal
   which uses JLine's capability-based approach for better terminal compatibility."
  (:require [charm.ansi.width :as w]
            [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Constants
;; ---------------------------------------------------------------------------

(def ^:const ESC "\u001b")
(def ^:const CSI "\u001b[")

;; ---------------------------------------------------------------------------
;; Mouse Control (no JLine capability equivalent)
;; ---------------------------------------------------------------------------

(def enable-mouse-normal (str CSI "?1000h"))
(def disable-mouse-normal (str CSI "?1000l"))
(def enable-mouse-cell-motion (str CSI "?1002h"))
(def disable-mouse-cell-motion (str CSI "?1002l"))
(def enable-mouse-all-motion (str CSI "?1003h"))
(def disable-mouse-all-motion (str CSI "?1003l"))
(def enable-mouse-sgr (str CSI "?1006h"))
(def disable-mouse-sgr (str CSI "?1006l"))

;; ---------------------------------------------------------------------------
;; Focus Reporting (no JLine capability equivalent)
;; ---------------------------------------------------------------------------

(def enable-focus-reporting (str CSI "?1004h"))
(def disable-focus-reporting (str CSI "?1004l"))

;; ---------------------------------------------------------------------------
;; Bracketed Paste (no JLine capability equivalent)
;; ---------------------------------------------------------------------------

(def enable-bracketed-paste (str CSI "?2004h"))
(def disable-bracketed-paste (str CSI "?2004l"))

;; ---------------------------------------------------------------------------
;; Window Title (OSC 2)
;; ---------------------------------------------------------------------------

(defn set-window-title [title]
  (str ESC "]2;" title "\u0007"))

;; ---------------------------------------------------------------------------
;; Clipboard (OSC 52)
;; ---------------------------------------------------------------------------

(defn copy-to-clipboard [^String text]
  (let [encoder (java.util.Base64/getEncoder)
        bytes (.getBytes text "UTF-8")
        encoded (.encodeToString encoder bytes)]
    (str ESC "]52;c;" encoded "\u0007")))

;; ---------------------------------------------------------------------------
;; Content Utilities
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
