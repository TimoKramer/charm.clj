(ns charm.ansi.parser
  "ANSI escape sequence parsing.

   Parses ANSI escape sequences into structured data for processing."
  (:require
   [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Constants
;; ---------------------------------------------------------------------------

(def ^:const ESC "\u001b")
(def ^:const CSI (str ESC "["))
(def ^:const OSC (str ESC "]"))
(def ^:const BEL "\u0007")
(def ^:const ST (str ESC "\\"))

;; ---------------------------------------------------------------------------
;; Sequence Type Detection
;; ---------------------------------------------------------------------------

(def ^:private csi-pattern
  "Pattern for CSI (Control Sequence Introducer) sequences.
   Format: ESC [ <params> <intermediate> <final>"
  #"\x1b\[([0-9;:<=>?]*)([!\"#$%&'()*+,\-.\/]*)([@A-Za-z\\^_`{|}~])")

(def ^:private osc-pattern
  "Pattern for OSC (Operating System Command) sequences.
   Format: ESC ] <params> ; <data> (BEL | ST)"
  #"\x1b\]([^\x07\x1b]*)(?:\x07|\x1b\\)")

(def ^:private sgr-pattern
  "Pattern specifically for SGR (Select Graphic Rendition) sequences.
   Format: ESC [ <params> m"
  #"\x1b\[([0-9;]*)m")

;; ---------------------------------------------------------------------------
;; Parsing Functions
;; ---------------------------------------------------------------------------

(defn parse-csi
  "Parse a CSI sequence into its components.
   Returns {:type :csi :params [...] :intermediate str :final char} or nil."
  [s]
  (when-let [match (re-find csi-pattern s)]
    (let [[_ params intermediate final] match
          param-list (when (and params (not (empty? params)))
                       (mapv #(if (empty? %) nil (parse-long %))
                             (str/split params #";")))]
      {:type :csi
       :params (or param-list [])
       :intermediate intermediate
       :final (first final)
       :raw (first match)})))

(defn parse-osc
  "Parse an OSC sequence into its components.
   Returns {:type :osc :command int :data str} or nil."
  [s]
  (when-let [match (re-find osc-pattern s)]
    (let [[_ content] match
          [cmd & data-parts] (str/split content #";" 2)]
      {:type :osc
       :command (when cmd (parse-long cmd))
       :data (first data-parts)
       :raw (first match)})))

(defn parse-sgr
  "Parse an SGR (style) sequence into its parameters.
   Returns {:type :sgr :params [...]} or nil."
  [s]
  (when-let [match (re-find sgr-pattern s)]
    (let [[_ params] match
          param-list (if (or (nil? params) (empty? params))
                       [0]  ; Reset
                       (mapv parse-long (str/split params #";")))]
      {:type :sgr
       :params param-list
       :raw (first match)})))

;; ---------------------------------------------------------------------------
;; Sequence Extraction
;; ---------------------------------------------------------------------------

(def ^:private any-sequence-pattern
  "Pattern matching any ANSI escape sequence."
  #"\x1b(?:\[[0-9;?]*[A-Za-z]|\][^\x07\x1b]*(?:\x07|\x1b\\)|\[[^\x1b]*|[^\[])")

(defn extract-sequences
  "Extract all ANSI sequences from a string.
   Returns a seq of {:start int :end int :raw str :parsed map}."
  [s]
  (let [matcher (re-matcher any-sequence-pattern s)]
    (loop [results []]
      (if (.find matcher)
        (let [raw (.group matcher)
              start (.start matcher)
              end (.end matcher)
              parsed (or (parse-sgr raw)
                         (parse-csi raw)
                         (parse-osc raw)
                         {:type :unknown :raw raw})]
          (recur (conj results {:start start
                                :end end
                                :raw raw
                                :parsed parsed})))
        results))))

(defn split-ansi
  "Split a string into segments of plain text and ANSI sequences.
   Returns a seq of {:type (:text | :ansi) :content str :parsed map?}."
  [s]
  (let [sequences (extract-sequences s)]
    (if (empty? sequences)
      [{:type :text :content s}]
      (loop [pos 0
             remaining sequences
             result []]
        (if (empty? remaining)
          ;; Add remaining text after last sequence
          (if (< pos (count s))
            (conj result {:type :text :content (subs s pos)})
            result)
          (let [{:keys [start end raw parsed]} (first remaining)
                ;; Add text before this sequence
                result' (if (< pos start)
                          (conj result {:type :text :content (subs s pos start)})
                          result)]
            (recur end
                   (rest remaining)
                   (conj result' {:type :ansi :content raw :parsed parsed}))))))))

;; ---------------------------------------------------------------------------
;; SGR (Style) Helpers
;; ---------------------------------------------------------------------------

(def sgr-codes
  "Common SGR parameter codes."
  {:reset 0
   :bold 1
   :dim 2
   :italic 3
   :underline 4
   :blink 5
   :reverse 7
   :hidden 8
   :strikethrough 9
   ;; Foreground colors
   :fg-black 30
   :fg-red 31
   :fg-green 32
   :fg-yellow 33
   :fg-blue 34
   :fg-magenta 35
   :fg-cyan 36
   :fg-white 37
   :fg-default 39
   ;; Background colors
   :bg-black 40
   :bg-red 41
   :bg-green 42
   :bg-yellow 43
   :bg-blue 44
   :bg-magenta 45
   :bg-cyan 46
   :bg-white 47
   :bg-default 49
   ;; Bright foreground
   :fg-bright-black 90
   :fg-bright-red 91
   :fg-bright-green 92
   :fg-bright-yellow 93
   :fg-bright-blue 94
   :fg-bright-magenta 95
   :fg-bright-cyan 96
   :fg-bright-white 97
   ;; Bright background
   :bg-bright-black 100
   :bg-bright-red 101
   :bg-bright-green 102
   :bg-bright-yellow 103
   :bg-bright-blue 104
   :bg-bright-magenta 105
   :bg-bright-cyan 106
   :bg-bright-white 107})

(def sgr-code->name
  "Reverse lookup: code number to keyword."
  (into {} (map (fn [[k v]] [v k]) sgr-codes)))

(defn sgr
  "Generate an SGR escape sequence from parameters.
   Accepts numbers or keywords from sgr-codes."
  [& params]
  (let [codes (map #(if (keyword? %) (get sgr-codes % 0) %) params)]
    (str CSI (str/join ";" codes) "m")))

(defn reset-style
  "Generate a reset SGR sequence."
  []
  (str CSI "0m"))
