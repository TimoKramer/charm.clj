(ns ^:no-doc charm.render.screen
  "ANSI control sequences for terminal features without JLine capability equivalents.

   For cursor movement, screen clearing, and alt screen, use charm.terminal
   which uses JLine's capability-based approach for better terminal compatibility."
  (:require [charm.ansi.sanitize :as san]
            [clojure.string :as str])
  (:import [java.util Base64]))

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

(defn set-window-title
  "OSC 2 sequence setting the window title.

   Control characters are removed from `title`: a BEL or an ESC in it would end
   this OSC early and let the rest of the argument open one of its own."
  [title]
  (str ESC "]2;" (san/strip-controls title) "\u0007"))

;; ---------------------------------------------------------------------------
;; Clipboard (OSC 52)
;; ---------------------------------------------------------------------------

(def ^:const max-clipboard-bytes
  "Largest clipboard payload we will emit, in base64 characters.

   Terminals cap OSC 52 and truncate what is over the cap silently, so a
   too-large copy would otherwise leave the user with a corrupted clipboard and
   no error. 74994 is tmux's limit, the smallest of the common ones."
  74994)

(defn copy-to-clipboard
  "OSC 52 sequence copying `text` to the system clipboard.

   Throws an ex-info naming the size if the encoded payload exceeds
   `max-clipboard-bytes`, rather than letting the terminal truncate it."
  [^String text]
  (let [encoded (.encodeToString (Base64/getEncoder) (.getBytes text "UTF-8"))]
    (when (> (count encoded) max-clipboard-bytes)
      (throw (ex-info (str "Clipboard payload of " (count encoded)
                           " bytes exceeds the " max-clipboard-bytes
                           "-byte limit terminals impose on OSC 52")
                      {:encoded-bytes (count encoded)
                       :max-bytes max-clipboard-bytes})))
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

