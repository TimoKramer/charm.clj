(ns charm.input.keys
  "Key sequence definitions and parsing.

   Maps terminal escape sequences to key types and provides
   utilities for key identification." 
  (:require
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
;; Escape Sequences
;; ---------------------------------------------------------------------------

(def escape-sequences
  "Maps escape sequences to key types.
   Format: sequence-string -> {:type key-type ...}"
  {;; Arrow keys (standard)
   "[A"     {:type :up}
   "[B"     {:type :down}
   "[C"     {:type :right}
   "[D"     {:type :left}

   ;; Arrow keys (application mode)
   "OA"     {:type :up}
   "OB"     {:type :down}
   "OC"     {:type :right}
   "OD"     {:type :left}

   ;; Arrow keys with modifiers (CSI 1;mod X format)
   "[1;2A"  {:type :up :shift true}
   "[1;2B"  {:type :down :shift true}
   "[1;2C"  {:type :right :shift true}
   "[1;2D"  {:type :left :shift true}
   "[1;3A"  {:type :up :alt true}
   "[1;3B"  {:type :down :alt true}
   "[1;3C"  {:type :right :alt true}
   "[1;3D"  {:type :left :alt true}
   "[1;4A"  {:type :up :shift true :alt true}
   "[1;4B"  {:type :down :shift true :alt true}
   "[1;4C"  {:type :right :shift true :alt true}
   "[1;4D"  {:type :left :shift true :alt true}
   "[1;5A"  {:type :up :ctrl true}
   "[1;5B"  {:type :down :ctrl true}
   "[1;5C"  {:type :right :ctrl true}
   "[1;5D"  {:type :left :ctrl true}
   "[1;6A"  {:type :up :shift true :ctrl true}
   "[1;6B"  {:type :down :shift true :ctrl true}
   "[1;6C"  {:type :right :shift true :ctrl true}
   "[1;6D"  {:type :left :shift true :ctrl true}
   "[1;7A"  {:type :up :alt true :ctrl true}
   "[1;7B"  {:type :down :alt true :ctrl true}
   "[1;7C"  {:type :right :alt true :ctrl true}
   "[1;7D"  {:type :left :alt true :ctrl true}
   "[1;8A"  {:type :up :shift true :alt true :ctrl true}
   "[1;8B"  {:type :down :shift true :alt true :ctrl true}
   "[1;8C"  {:type :right :shift true :alt true :ctrl true}
   "[1;8D"  {:type :left :shift true :alt true :ctrl true}

   ;; Navigation keys
   "[H"     {:type :home}
   "[F"     {:type :end}
   "OH"     {:type :home}
   "OF"     {:type :end}
   "[1~"    {:type :home}
   "[4~"    {:type :end}
   "[7~"    {:type :home}
   "[8~"    {:type :end}
   "[2~"    {:type :insert}
   "[3~"    {:type :delete}
   "[5~"    {:type :page-up}
   "[6~"    {:type :page-down}

   ;; Navigation with modifiers
   "[1;2H"  {:type :home :shift true}
   "[1;2F"  {:type :end :shift true}
   "[1;3H"  {:type :home :alt true}
   "[1;3F"  {:type :end :alt true}
   "[1;5H"  {:type :home :ctrl true}
   "[1;5F"  {:type :end :ctrl true}
   "[2;3~"  {:type :insert :alt true}
   "[3;3~"  {:type :delete :alt true}
   "[2;5~"  {:type :insert :ctrl true}
   "[3;5~"  {:type :delete :ctrl true}
   "[5;5~"  {:type :page-up :ctrl true}
   "[6;5~"  {:type :page-down :ctrl true}

   ;; Function keys (standard)
   "OP"     {:type :f1}
   "OQ"     {:type :f2}
   "OR"     {:type :f3}
   "OS"     {:type :f4}
   "[15~"   {:type :f5}
   "[17~"   {:type :f6}
   "[18~"   {:type :f7}
   "[19~"   {:type :f8}
   "[20~"   {:type :f9}
   "[21~"   {:type :f10}
   "[23~"   {:type :f11}
   "[24~"   {:type :f12}

   ;; Function keys (alternate)
   "[11~"   {:type :f1}
   "[12~"   {:type :f2}
   "[13~"   {:type :f3}
   "[14~"   {:type :f4}

   ;; Function keys with shift
   "[1;2P"  {:type :f1 :shift true}
   "[1;2Q"  {:type :f2 :shift true}
   "[1;2R"  {:type :f3 :shift true}
   "[1;2S"  {:type :f4 :shift true}
   "[15;2~" {:type :f5 :shift true}
   "[17;2~" {:type :f6 :shift true}
   "[18;2~" {:type :f7 :shift true}
   "[19;2~" {:type :f8 :shift true}
   "[20;2~" {:type :f9 :shift true}
   "[21;2~" {:type :f10 :shift true}
   "[23;2~" {:type :f11 :shift true}
   "[24;2~" {:type :f12 :shift true}

   ;; Function keys with ctrl
   "[1;5P"  {:type :f1 :ctrl true}
   "[1;5Q"  {:type :f2 :ctrl true}
   "[1;5R"  {:type :f3 :ctrl true}
   "[1;5S"  {:type :f4 :ctrl true}
   "[15;5~" {:type :f5 :ctrl true}
   "[17;5~" {:type :f6 :ctrl true}
   "[18;5~" {:type :f7 :ctrl true}
   "[19;5~" {:type :f8 :ctrl true}
   "[20;5~" {:type :f9 :ctrl true}
   "[21;5~" {:type :f10 :ctrl true}
   "[23;5~" {:type :f11 :ctrl true}
   "[24;5~" {:type :f12 :ctrl true}

   ;; Extended function keys
   "[25~"   {:type :f13}
   "[26~"   {:type :f14}
   "[28~"   {:type :f15}
   "[29~"   {:type :f16}
   "[31~"   {:type :f17}
   "[32~"   {:type :f18}
   "[33~"   {:type :f19}
   "[34~"   {:type :f20}

   ;; Focus events
   "[I"     {:type :focus}
   "[O"     {:type :blur}

   ;; Bracketed paste
   "[200~"  {:type :paste-start}
   "[201~"  {:type :paste-end}})

;; ---------------------------------------------------------------------------
;; Parsing Functions
;; ---------------------------------------------------------------------------

(defn parse-ctrl-char
  "Parse a control character (byte 0-31 or 127) into a key map."
  [byte-val]
  (get ctrl-char->key byte-val {:type :unknown :code byte-val}))

(defn parse-escape-sequence
  "Parse an escape sequence (without ESC prefix) into a key map."
  [seq-str]
  (get escape-sequences seq-str {:type :unknown :sequence seq-str}))

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
