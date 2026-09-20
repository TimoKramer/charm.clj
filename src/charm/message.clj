(ns charm.message
  "Message types for charm.clj TUI applications.

   Messages are plain maps with a :type key for easy pattern matching.
   Use factory functions to create messages and predicates to check types."
  (:require
   [clojure.string :as string]))

;; ---------------------------------------------------------------------------
;; Message Factories
;; ---------------------------------------------------------------------------

(defn key-press
  "Create a key press message.

   Options:
     :alt   - Alt key modifier (default false)
     :ctrl  - Ctrl key modifier (default false)
     :shift - Shift key modifier (default false)"
  [key & {:keys [alt ctrl shift]
          :or {alt false ctrl false shift false}}]
  {:type :key-press
   :key key
   :alt alt
   :ctrl ctrl
   :shift shift})

(defn window-size
  "Create a window size message."
  [width height]
  {:type :window-size
   :width width
   :height height})

(defn quit
  "Create a quit message to exit the program."
  []
  {:type :quit})

(defn error
  "Create an error message."
  [throwable]
  {:type :error
   :error throwable})

(defn mouse
  "Create a mouse event message.

   Action is one of: :press :release :motion :wheel-up :wheel-down
   Button is one of: :left :middle :right :none"
  [action button x y & {:keys [alt ctrl shift]
                        :or {alt false ctrl false shift false}}]
  {:type :mouse
   :action action
   :button button
   :x x
   :y y
   :alt alt
   :ctrl ctrl
   :shift shift})

(defn environment
  "Create an environment message describing the terminal.
   Sent once at program startup so apps can branch on the environment.

   Keys:
     :color-profile    - :ascii, :ansi, :ansi256, or :true-color
     :dark-background? - whether the terminal background is dark"
  [color-profile dark-background?]
  {:type :environment
   :color-profile color-profile
   :dark-background? dark-background?})

(defn focus
  "Create a focus gained message."
  []
  {:type :focus})

(defn blur
  "Create a focus lost (blur) message."
  []
  {:type :blur})

(defn paste
  "Create a paste message carrying the whole pasted text.

   Sent instead of one key press per character when `run` was given
   `:bracketed-paste true` and the terminal supports it."
  [text]
  {:type :paste
   :text text})

;; ---------------------------------------------------------------------------
;; Type Predicates
;; ---------------------------------------------------------------------------

(defn msg-type
  "Get the type of a message."
  [msg]
  (:type msg))

(defn key-press?
  "Check if message is a key press."
  [msg]
  (= :key-press (:type msg)))

(defn window-size?
  "Check if message is a window size change."
  [msg]
  (= :window-size (:type msg)))

(defn quit?
  "Check if message is a quit signal."
  [msg]
  (= :quit (:type msg)))

(defn error?
  "Check if message is an error."
  [msg]
  (= :error (:type msg)))

(defn paste?
  "Check if message is a paste."
  [msg]
  (= :paste (:type msg)))

(defn mouse?
  "Check if message is a mouse event."
  [msg]
  (= :mouse (:type msg)))

(defn environment?
  "Check if message is an environment message."
  [msg]
  (= :environment (:type msg)))

(defn focus?
  "Check if message is a focus event."
  [msg]
  (= :focus (:type msg)))

(defn blur?
  "Check if message is a blur event."
  [msg]
  (= :blur (:type msg)))

;; ---------------------------------------------------------------------------
;; Key Helpers
;; ---------------------------------------------------------------------------

(def ^:private key-aliases
  "Short spellings accepted in key patterns, mapped to the real key name."
  {"esc"    "escape"
   "pgup"   "page-up"
   "pgdown" "page-down"})

(defn- key-name
  "The canonical name of a key pattern part, resolving short spellings."
  [k]
  (let [n (if (keyword? k) (name k) k)]
    (get key-aliases n n)))

(defn key-match?
  "Check if a key-press message matches the given key.

   Key can be:
   - A string like \"q\", \"a\" (matches character keys)
   - A keyword like :enter, :up, :tab (matches special keys)
   - A pattern like \"ctrl+c\" (matches with modifiers)

   The short spellings \"esc\", \"pgup\" and \"pgdown\" are accepted for
   :escape, :page-up and :page-down."
  [msg key]
  (when (key-press? msg)
    (let [msg-key (:key msg)
          msg-name (if (keyword? msg-key) (name msg-key) msg-key)]
      (cond
        ;; Pattern with modifiers like "ctrl+c"
        (and (string? key) (string/includes? key "+"))
        (let [parts (string/split (string/lower-case key) #"\+")
              mods (set (butlast parts))
              key-part (key-name (last parts))]
          (and (if (contains? mods "ctrl") (:ctrl msg) (not (:ctrl msg)))
               (if (contains? mods "alt") (:alt msg) (not (:alt msg)))
               (if (contains? mods "shift") (:shift msg) (not (:shift msg)))
               (= key-part msg-name)))

        ;; A key name, as a keyword (:up) or a string (\"up\", \"q\")
        (or (keyword? key) (string? key))
        (= (key-name key) msg-name)

        :else false))))

(defn ctrl?
  "Check if ctrl modifier is set."
  [msg]
  (:ctrl msg false))

(defn alt?
  "Check if alt modifier is set."
  [msg]
  (:alt msg false))

(defn shift?
  "Check if shift modifier is set."
  [msg]
  (:shift msg false))

;; ---------------------------------------------------------------------------
;; Mouse Helpers
;; ---------------------------------------------------------------------------

(defn click?
  "Check if a mouse message is a click (press action)."
  [msg]
  (and (mouse? msg) (= :press (:action msg))))

(defn release?
  "Check if a mouse message is a release."
  [msg]
  (and (mouse? msg) (= :release (:action msg))))

(defn motion?
  "Check if a mouse message is motion (drag)."
  [msg]
  (and (mouse? msg) (= :motion (:action msg))))

(defn left-click?
  "Check if a mouse message is a left click."
  [msg]
  (and (click? msg) (= :left (:button msg))))

(defn right-click?
  "Check if a mouse message is a right click."
  [msg]
  (and (click? msg) (= :right (:button msg))))

(defn middle-click?
  "Check if a mouse message is a middle click."
  [msg]
  (and (click? msg) (= :middle (:button msg))))

(defn wheel-up?
  "Check if a mouse message is a wheel up event."
  [msg]
  (and (mouse? msg) (= :wheel-up (:action msg))))

(defn wheel-down?
  "Check if a mouse message is a wheel down event."
  [msg]
  (and (mouse? msg) (= :wheel-down (:action msg))))

(defn wheel?
  "Check if a mouse message is any wheel event."
  [msg]
  (or (wheel-up? msg) (wheel-down? msg)))
