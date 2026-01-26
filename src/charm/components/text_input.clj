(ns charm.components.text-input
  "Text input component with cursor movement and editing.

   Usage:
     (def my-input (text-input :prompt \"Name: \"))

     ;; In update function:
     (text-input-update my-input msg)

     ;; In view function:
     (text-input-view my-input)"
  (:require [charm.style.core :as style]
            [charm.message :as msg]
            [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Echo Modes
;; ---------------------------------------------------------------------------

(def echo-normal :normal)
(def echo-password :password)
(def echo-none :none)

;; ---------------------------------------------------------------------------
;; Key Bindings
;; ---------------------------------------------------------------------------

(def ^:private default-keys
  "Default key bindings for text input."
  {:character-forward    ["right" "ctrl+f"]
   :character-backward   ["left" "ctrl+b"]
   :word-forward         ["alt+right" "ctrl+right" "alt+f"]
   :word-backward        ["alt+left" "ctrl+left" "alt+b"]
   :delete-word-backward ["alt+backspace" "ctrl+w"]
   :delete-word-forward  ["alt+delete" "alt+d"]
   :delete-after-cursor  ["ctrl+k"]
   :delete-before-cursor ["ctrl+u"]
   :delete-char-backward ["backspace" "ctrl+h"]
   :delete-char-forward  ["delete" "ctrl+d"]
   :line-start           ["home" "ctrl+a"]
   :line-end             ["end" "ctrl+e"]})

(defn- matches-binding?
  "Check if a message matches any key in a binding."
  [m binding]
  (some #(msg/key-match? m %) binding))

;; ---------------------------------------------------------------------------
;; Text Input Creation
;; ---------------------------------------------------------------------------

(defn text-input
  "Create a text input component.

   Options:
     :prompt           - Prompt string (default \"> \")
     :placeholder      - Placeholder text when empty
     :value            - Initial value (default \"\")
     :echo-mode        - :normal, :password, or :none (default :normal)
     :echo-char        - Character for password mode (default \\*)
     :char-limit       - Maximum characters (0 = unlimited)
     :width            - Display width (0 = unlimited)
     :prompt-style     - Style for prompt
     :text-style       - Style for text
     :placeholder-style - Style for placeholder
     :cursor-style     - Style for cursor
     :focused          - Start focused (default true)
     :id               - Unique ID"
  [& {:keys [prompt placeholder value echo-mode echo-char char-limit width
             prompt-style text-style placeholder-style cursor-style
             focused id]
      :or {prompt "> "
           value ""
           echo-mode :normal
           echo-char \*
           char-limit 0
           width 0
           focused true
           id (rand-int 1000000)}}]
  {:type :text-input
   :id id
   :prompt prompt
   :placeholder placeholder
   :value (vec value)  ; Store as vector of chars for efficient editing
   :pos (count value)  ; Cursor position
   :echo-mode echo-mode
   :echo-char echo-char
   :char-limit char-limit
   :width width
   :offset 0           ; View offset for scrolling
   :focused focused
   :prompt-style prompt-style
   :text-style text-style
   :placeholder-style (or placeholder-style
                          (style/style :fg 240))
   :cursor-style (or cursor-style
                     (style/style :reverse true))
   :keys default-keys})

;; ---------------------------------------------------------------------------
;; Text Input Accessors
;; ---------------------------------------------------------------------------

(defn value
  "Get the current value as a string."
  [input]
  (apply str (:value input)))

(defn set-value
  "Set the value and move cursor to end."
  [input v]
  (let [chars (vec v)
        chars (if (and (pos? (:char-limit input))
                       (> (count chars) (:char-limit input)))
                (subvec chars 0 (:char-limit input))
                chars)]
    (-> input
        (assoc :value chars)
        (assoc :pos (count chars)))))

(defn position
  "Get cursor position."
  [input]
  (:pos input))

(defn focused?
  "Check if input is focused."
  [input]
  (:focused input))

(defn focus
  "Focus the input."
  [input]
  (assoc input :focused true))

(defn blur
  "Blur (unfocus) the input."
  [input]
  (assoc input :focused false))

(defn reset
  "Clear the input value."
  [input]
  (-> input
      (assoc :value [])
      (assoc :pos 0)
      (assoc :offset 0)))

;; ---------------------------------------------------------------------------
;; Cursor Movement
;; ---------------------------------------------------------------------------

(defn- clamp [n min-val max-val]
  (max min-val (min max-val n)))

(defn- set-cursor
  "Set cursor position, clamping to valid range."
  [input pos]
  (assoc input :pos (clamp pos 0 (count (:value input)))))

(defn cursor-start
  "Move cursor to start of input."
  [input]
  (set-cursor input 0))

(defn cursor-end
  "Move cursor to end of input."
  [input]
  (set-cursor input (count (:value input))))

(defn- whitespace? [c]
  (Character/isWhitespace (char c)))

(defn- word-backward
  "Move cursor backward one word."
  [input]
  (let [{:keys [value pos]} input]
    (if (or (zero? pos) (empty? value))
      input
      (loop [i (dec pos)]
        (cond
          (neg? i)
          (set-cursor input 0)

          ;; Skip whitespace
          (whitespace? (nth value i))
          (recur (dec i))

          ;; Found non-whitespace, find word start
          :else
          (loop [j i]
            (if (or (neg? j) (whitespace? (nth value j)))
              (set-cursor input (inc j))
              (recur (dec j)))))))))

(defn- word-forward
  "Move cursor forward one word."
  [input]
  (let [{:keys [value pos]} input
        len (count value)]
    (if (or (>= pos len) (empty? value))
      input
      (loop [i pos]
        (cond
          (>= i len)
          (set-cursor input len)

          ;; Skip whitespace
          (whitespace? (nth value i))
          (recur (inc i))

          ;; Found non-whitespace, find word end
          :else
          (loop [j i]
            (if (or (>= j len) (whitespace? (nth value j)))
              (set-cursor input j)
              (recur (inc j)))))))))

;; ---------------------------------------------------------------------------
;; Text Editing
;; ---------------------------------------------------------------------------

(defn- delete-char-backward
  "Delete character before cursor."
  [input]
  (let [{:keys [value pos]} input]
    (if (and (pos? pos) (seq value))
      (-> input
          (assoc :value (into (subvec value 0 (dec pos))
                              (subvec value pos)))
          (update :pos dec))
      input)))

(defn- delete-char-forward
  "Delete character after cursor."
  [input]
  (let [{:keys [value pos]} input]
    (if (and (< pos (count value)) (seq value))
      (assoc input :value (into (subvec value 0 pos)
                                (subvec value (inc pos))))
      input)))

(defn- delete-word-backward
  "Delete word before cursor."
  [input]
  (let [{:keys [value pos]} input]
    (if (or (zero? pos) (empty? value))
      input
      (let [new-input (word-backward input)
            new-pos (:pos new-input)]
        (-> input
            (assoc :value (into (subvec value 0 new-pos)
                                (subvec value pos)))
            (assoc :pos new-pos))))))

(defn- delete-word-forward
  "Delete word after cursor."
  [input]
  (let [{:keys [value pos]} input]
    (if (or (>= pos (count value)) (empty? value))
      input
      (let [new-input (word-forward input)
            new-pos (:pos new-input)]
        (assoc input :value (into (subvec value 0 pos)
                                  (subvec value new-pos)))))))

(defn- delete-before-cursor
  "Delete everything before cursor."
  [input]
  (let [{:keys [value pos]} input]
    (-> input
        (assoc :value (subvec value pos))
        (assoc :pos 0)
        (assoc :offset 0))))

(defn- delete-after-cursor
  "Delete everything after cursor."
  [input]
  (let [{:keys [value pos]} input]
    (assoc input :value (subvec value 0 pos))))

(defn- insert-chars
  "Insert characters at cursor position."
  [input chars]
  (let [{:keys [value pos char-limit]} input
        ;; Filter out control characters except for printable ones
        chars (filterv #(or (>= (int %) 32) (= % \tab)) chars)
        ;; Apply char limit
        chars (if (and (pos? char-limit)
                       (> (+ (count value) (count chars)) char-limit))
                (subvec chars 0 (max 0 (- char-limit (count value))))
                chars)]
    (if (empty? chars)
      input
      (-> input
          (assoc :value (into (into (subvec value 0 pos) chars)
                              (subvec value pos)))
          (update :pos + (count chars))))))

;; ---------------------------------------------------------------------------
;; Text Input Update
;; ---------------------------------------------------------------------------

(defn text-input-update
  "Update text input state based on a message.
   Returns [new-input cmd] or [input nil] if message not handled."
  [input msg]
  (if-not (:focused input)
    [input nil]
    (let [keys (:keys input)]
      (cond
        ;; Character backward (left arrow)
        (matches-binding? msg (:character-backward keys))
        [(set-cursor input (dec (:pos input))) nil]

        ;; Character forward (right arrow)
        (matches-binding? msg (:character-forward keys))
        [(set-cursor input (inc (:pos input))) nil]

        ;; Word backward
        (matches-binding? msg (:word-backward keys))
        [(word-backward input) nil]

        ;; Word forward
        (matches-binding? msg (:word-forward keys))
        [(word-forward input) nil]

        ;; Line start
        (matches-binding? msg (:line-start keys))
        [(cursor-start input) nil]

        ;; Line end
        (matches-binding? msg (:line-end keys))
        [(cursor-end input) nil]

        ;; Delete char backward (backspace)
        (matches-binding? msg (:delete-char-backward keys))
        [(delete-char-backward input) nil]

        ;; Delete char forward (delete)
        (matches-binding? msg (:delete-char-forward keys))
        [(delete-char-forward input) nil]

        ;; Delete word backward
        (matches-binding? msg (:delete-word-backward keys))
        [(delete-word-backward input) nil]

        ;; Delete word forward
        (matches-binding? msg (:delete-word-forward keys))
        [(delete-word-forward input) nil]

        ;; Delete before cursor
        (matches-binding? msg (:delete-before-cursor keys))
        [(delete-before-cursor input) nil]

        ;; Delete after cursor
        (matches-binding? msg (:delete-after-cursor keys))
        [(delete-after-cursor input) nil]

        ;; Regular character input
        (and (msg/key-press? msg)
             (string? (:key msg))
             (not (:ctrl msg))
             (not (:alt msg)))
        [(insert-chars input (vec (:key msg))) nil]

        ;; Not handled
        :else
        [input nil]))))

;; ---------------------------------------------------------------------------
;; Text Input View
;; ---------------------------------------------------------------------------

(defn- echo-transform
  "Transform text according to echo mode."
  [input text]
  (case (:echo-mode input)
    :normal text
    :password (apply str (repeat (count text) (:echo-char input)))
    :none ""))

(defn text-input-view
  "Render the text input to a string."
  [input]
  (let [{:keys [prompt placeholder value pos focused
                prompt-style text-style placeholder-style cursor-style
                echo-mode]} input
        prompt-str (if prompt-style
                     (style/render prompt-style prompt)
                     prompt)]
    (if (and (empty? value) placeholder (not (str/blank? placeholder)))
      ;; Show placeholder
      (let [placeholder-str (if focused
                              ;; Show cursor at start when focused
                              (str (style/render cursor-style
                                                 (subs placeholder 0 1))
                                   (style/render placeholder-style
                                                 (subs placeholder 1)))
                              (style/render placeholder-style placeholder))]
        (str prompt-str placeholder-str))

      ;; Show value with cursor
      (let [text (apply str value)
            transformed (echo-transform input text)
            before (subs transformed 0 (min pos (count transformed)))
            cursor-char (if (< pos (count transformed))
                          (subs transformed pos (inc pos))
                          " ")
            after (if (< pos (count transformed))
                    (subs transformed (inc pos))
                    "")

            before-str (if text-style
                         (style/render text-style before)
                         before)
            cursor-str (if focused
                         (style/render cursor-style cursor-char)
                         cursor-char)
            after-str (if text-style
                        (style/render text-style after)
                        after)]
        (str prompt-str before-str cursor-str after-str)))))

;; ---------------------------------------------------------------------------
;; Text Input Init
;; ---------------------------------------------------------------------------

(defn text-input-init
  "Initialize the text input, returns [input cmd].
   Currently just returns the input with no command."
  [input]
  [input nil])
