(ns charm.input.keys
  "Key sequence definitions and parsing.

   Maps terminal escape sequences to key types and provides
   utilities for key identification.

   For escape sequence lookup, see charm.input.keymap which uses
   JLine's KeyMap for efficient terminal-aware sequence matching."
  (:require
   [charm.input.keymap :as km]
   [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Key Types
;; ---------------------------------------------------------------------------

(def key-types
  "All recognized key types."
  #{:unknown :runes
    ;; Control keys
    :null :enter :tab :backspace :escape :space
    ;; Navigation
    :up :down :left :right
    :home :end :page-up :page-down
    :insert :delete
    ;; Function keys
    :f1 :f2 :f3 :f4 :f5 :f6 :f7 :f8 :f9 :f10 :f11 :f12
    :f13 :f14 :f15 :f16 :f17 :f18 :f19 :f20})

;; ---------------------------------------------------------------------------
;; Control Character Mappings (Single bytes)
;; ---------------------------------------------------------------------------

(def ctrl-char->key
  "Maps control characters (0-31) to key types."
  {0   {:type :null}
   1   {:type :runes :runes "a" :ctrl true}   ; Ctrl+A
   2   {:type :runes :runes "b" :ctrl true}   ; Ctrl+B
   3   {:type :runes :runes "c" :ctrl true}   ; Ctrl+C
   4   {:type :runes :runes "d" :ctrl true}   ; Ctrl+D
   5   {:type :runes :runes "e" :ctrl true}   ; Ctrl+E
   6   {:type :runes :runes "f" :ctrl true}   ; Ctrl+F
   7   {:type :runes :runes "g" :ctrl true}   ; Ctrl+G (BEL)
   8   {:type :backspace}                     ; Ctrl+H / Backspace
   9   {:type :tab}                           ; Ctrl+I / Tab
   10  {:type :enter}                         ; Ctrl+J / Line Feed
   11  {:type :runes :runes "k" :ctrl true}   ; Ctrl+K
   12  {:type :runes :runes "l" :ctrl true}   ; Ctrl+L
   13  {:type :enter}                         ; Ctrl+M / Carriage Return
   14  {:type :runes :runes "n" :ctrl true}   ; Ctrl+N
   15  {:type :runes :runes "o" :ctrl true}   ; Ctrl+O
   16  {:type :runes :runes "p" :ctrl true}   ; Ctrl+P
   17  {:type :runes :runes "q" :ctrl true}   ; Ctrl+Q
   18  {:type :runes :runes "r" :ctrl true}   ; Ctrl+R
   19  {:type :runes :runes "s" :ctrl true}   ; Ctrl+S
   20  {:type :runes :runes "t" :ctrl true}   ; Ctrl+T
   21  {:type :runes :runes "u" :ctrl true}   ; Ctrl+U
   22  {:type :runes :runes "v" :ctrl true}   ; Ctrl+V
   23  {:type :runes :runes "w" :ctrl true}   ; Ctrl+W
   24  {:type :runes :runes "x" :ctrl true}   ; Ctrl+X
   25  {:type :runes :runes "y" :ctrl true}   ; Ctrl+Y
   26  {:type :runes :runes "z" :ctrl true}   ; Ctrl+Z
   27  {:type :escape}                        ; ESC
   28  {:type :runes :runes "\\" :ctrl true}  ; Ctrl+\
   29  {:type :runes :runes "]" :ctrl true}   ; Ctrl+]
   30  {:type :runes :runes "^" :ctrl true}   ; Ctrl+^
   31  {:type :runes :runes "_" :ctrl true}   ; Ctrl+_
   32  {:type :space}                         ; Space
   127 {:type :backspace}})                   ; DEL / Backspace

;; ---------------------------------------------------------------------------
;; Escape Sequence Lookup (via KeyMap)
;; ---------------------------------------------------------------------------

;; Escape sequences are now handled by charm.input.keymap using JLine's KeyMap
;; for efficient O(1) lookup with terminal capability awareness.

;; ---------------------------------------------------------------------------
;; Parsing Functions
;; ---------------------------------------------------------------------------

(defn parse-ctrl-char
  "Parse a control character (byte 0-31 or 127) into a key map."
  [byte-val]
  (get ctrl-char->key byte-val {:type :unknown :code byte-val}))

(defn parse-escape-sequence
  "Parse an escape sequence (without ESC prefix) into a key map.
   Uses JLine KeyMap for efficient lookup."
  ([seq-str]
   (parse-escape-sequence @km/default-keymap seq-str))
  ([keymap seq-str]
   (km/lookup-or-unknown keymap seq-str)))

(defn ctrl-char?
  "Check if a byte value is a control character."
  [byte-val]
  (or (<= 0 byte-val 31) (= byte-val 127)))

(defn escape-prefix?
  "Check if a string starts with a known escape sequence prefix."
  [s]
  (or (str/starts-with? s "[")
      (str/starts-with? s "O")))

;; ---------------------------------------------------------------------------
;; Key Construction
;; ---------------------------------------------------------------------------

(defn make-key
  "Create a key event map.

   Options:
     :type  - Key type keyword (required)
     :runes - Character(s) for :runes type
     :ctrl  - Ctrl modifier pressed
     :alt   - Alt modifier pressed
     :shift - Shift modifier pressed"
  [{:keys [type runes ctrl alt shift] :or {ctrl false alt false shift false}}]
  (cond-> {:type type}
    runes (assoc :runes runes)
    ctrl  (assoc :ctrl true)
    alt   (assoc :alt true)
    shift (assoc :shift true)))

(defn key-matches?
  "Check if a key event matches a pattern.
   Pattern can be:
   - A string like \"ctrl+c\", \"alt+x\", \"enter\"
   - A key type keyword like :enter, :up
   - A map like {:type :runes :runes \"c\" :ctrl true}"
  [key pattern]
  (cond
    (keyword? pattern)
    (= (:type key) pattern)

    (map? pattern)
    (every? (fn [[k v]] (= (get key k) v)) pattern)

    (string? pattern)
    (let [parts (str/split (str/lower-case pattern) #"\+")
          mods (set (butlast parts))
          key-part (last parts)]
      (and (if (contains? mods "ctrl") (:ctrl key) (not (:ctrl key)))
           (if (contains? mods "alt") (:alt key) (not (:alt key)))
           (if (contains? mods "shift") (:shift key) (not (:shift key)))
           (or (= key-part (name (:type key)))
               (= key-part (:runes key)))))

    :else false))
