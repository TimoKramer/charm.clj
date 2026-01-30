(ns charm.input.handler
  "Terminal input handling.

   Reads raw terminal input and converts it to structured
   key and mouse events."
  (:require
   [charm.input.keymap :as km]
   [charm.input.keys :as keys]
   [charm.input.mouse :as mouse]
   [clojure.string :as str])
  (:import
   [org.jline.keymap KeyMap]
   [org.jline.terminal Terminal]
   [org.jline.utils NonBlockingReader]))

;; ---------------------------------------------------------------------------
;; Input Reading
;; ---------------------------------------------------------------------------

(defn- read-char
  "Read a single character from the terminal with timeout.
   Returns the character code or -1 if timeout/EOF."
  [^NonBlockingReader reader ^long timeout-ms]
  (.read reader timeout-ms))

(defn- read-char-blocking
  "Read a single character from the terminal, blocking."
  [^NonBlockingReader reader]
  (.read reader))

(defn- read-available
  "Read all available characters up to a limit.
   Returns a vector of character codes."
  [^NonBlockingReader reader max-chars timeout-ms]
  (loop [chars []
         remaining max-chars]
    (if (zero? remaining)
      chars
      (let [c (read-char reader timeout-ms)]
        (if (neg? c)
          chars
          (recur (conj chars c) (dec remaining)))))))

;; ---------------------------------------------------------------------------
;; Escape Sequence Parsing
;; ---------------------------------------------------------------------------

(def ^:private ESC 27)
(def ^:private CSI-BRACKET (int \[))
(def ^:private SS3-O (int \O))

(defn- csi-final-byte?
  "Check if a byte is a CSI final byte (0x40-0x7E)."
  [b]
  (<= 0x40 b 0x7E))

(defn- read-csi-sequence
  "Read a CSI sequence after ESC [.
   Returns the sequence string (without ESC)."
  [^NonBlockingReader reader timeout-ms]
  (loop [chars [(int \[)]]
    (let [c (read-char reader timeout-ms)]
      (cond
        (neg? c)
        ;; Timeout - return what we have
        (apply str (map char chars))

        (csi-final-byte? c)
        ;; Final byte - sequence complete
        (apply str (map char (conj chars c)))

        :else
        ;; Parameter or intermediate byte
        (recur (conj chars c))))))

(defn- read-ss3-sequence
  "Read an SS3 sequence after ESC O.
   Returns the sequence string (without ESC)."
  [^NonBlockingReader reader timeout-ms]
  (let [c (read-char reader timeout-ms)]
    (if (neg? c)
      "O"
      (str "O" (char c)))))

(defn- read-escape-sequence
  "Read an escape sequence after ESC.
   Returns the full sequence string (without ESC) or nil if just ESC."
  [^NonBlockingReader reader timeout-ms]
  (let [c (read-char reader timeout-ms)]
    (cond
      (neg? c)
      nil  ; Just ESC

      (= c CSI-BRACKET)
      (read-csi-sequence reader timeout-ms)

      (= c SS3-O)
      (read-ss3-sequence reader timeout-ms)

      :else
      ;; Alt+key or unknown
      (str (char c)))))

;; ---------------------------------------------------------------------------
;; Mouse Sequence Detection
;; ---------------------------------------------------------------------------

(defn- x10-mouse-sequence?
  "Check if a CSI sequence is an X10 mouse sequence (CSI M)."
  [seq-str]
  (and (str/starts-with? seq-str "[M")
       (= (count seq-str) 5)))  ; [M + 3 bytes

(defn- sgr-mouse-sequence?
  "Check if a CSI sequence is an SGR mouse sequence (CSI <)."
  [seq-str]
  (str/starts-with? seq-str "[<"))

(defn- parse-mouse-sequence
  "Parse a mouse sequence, returns mouse event or nil."
  [^String seq-str]
  (cond
    (x10-mouse-sequence? seq-str)
    (mouse/parse-x10-mouse
     (int (.charAt seq-str 2))
     (int (.charAt seq-str 3))
     (int (.charAt seq-str 4)))

    (sgr-mouse-sequence? seq-str)
    (mouse/parse-sgr-mouse (str "\u001b" seq-str))

    :else nil))

;; ---------------------------------------------------------------------------
;; Input Event Parsing
;; ---------------------------------------------------------------------------

(defn parse-input
  "Parse a raw input byte into an event.
   For escape sequences, pass the sequence after ESC.
   Optionally accepts a KeyMap for terminal-aware sequence lookup."
  ([byte-val]
   (if (keys/ctrl-char? byte-val)
     (keys/parse-ctrl-char byte-val)
     {:type :runes :runes (str (char byte-val))}))

  ([byte-val escape-seq]
   (parse-input byte-val escape-seq nil))

  ([byte-val escape-seq ^KeyMap keymap]
   (if (nil? escape-seq)
     {:type :escape}  ; Just ESC
     (if-let [mouse (parse-mouse-sequence escape-seq)]
       mouse
       (if (= (count escape-seq) 1)
         ;; Alt+key
         {:type :runes :runes escape-seq :alt true}
         ;; Escape sequence - use keymap if provided
         (if keymap
           (keys/parse-escape-sequence keymap escape-seq)
           (keys/parse-escape-sequence escape-seq)))))))

;; ---------------------------------------------------------------------------
;; High-Level Input Reading
;; ---------------------------------------------------------------------------

(defn read-event
  "Read a single input event from the terminal.
   Returns an event map with :type and other keys, or nil on timeout.

   Options:
     :timeout-ms - Timeout for reading (default 50)
     :keymap     - KeyMap for escape sequence lookup (optional, creates terminal-aware one if nil)"
  [^Terminal terminal & {:keys [timeout-ms keymap] :or {timeout-ms 50}}]
  (let [reader (.reader terminal)
        c (read-char reader timeout-ms)]
    (cond
      (neg? c)
      nil  ; Timeout or EOF

      (= c ESC)
      (parse-input c (read-escape-sequence reader timeout-ms) keymap)

      :else
      (parse-input c))))

(defn read-events
  "Create a lazy sequence of input events from the terminal.
   Blocks waiting for each event.

   Options:
     :timeout-ms - Timeout for reading (default 50)
     :keymap     - KeyMap for escape sequence lookup (optional)"
  [^Terminal terminal & opts]
  (lazy-seq
   (when-let [event (apply read-event terminal opts)]
     (cons event (apply read-events terminal opts)))))

;; ---------------------------------------------------------------------------
;; Input Handler State
;; ---------------------------------------------------------------------------

(defn create-handler
  "Create an input handler for a terminal.

   Returns a map with:
     :terminal   - The terminal
     :keymap     - Terminal-aware KeyMap for escape sequences
     :read-event - Function to read next event
     :stop       - Function to stop the handler"
  [^Terminal terminal]
  (let [running (atom true)
        keymap (km/create-keymap terminal)]
    {:terminal terminal
     :keymap keymap
     :running running
     :read-event (fn []
                   (when @running
                     (read-event terminal :keymap keymap)))
     :stop (fn []
             (reset! running false))}))
