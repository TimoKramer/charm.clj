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

(defn focus
  "Create a focus gained message."
  []
  {:type :focus})

(defn blur
  "Create a focus lost (blur) message."
  []
  {:type :blur})

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

(defn mouse?
  "Check if message is a mouse event."
  [msg]
  (= :mouse (:type msg)))

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

(defn key-match?
  "Check if a key-press message matches the given key.

   Key can be:
   - A string like \"q\", \"a\" (matches character keys)
   - A keyword like :enter, :up, :tab (matches special keys)
   - A pattern like \"ctrl+c\" (matches with modifiers)"
  [msg key]
  (when (key-press? msg)
    (let [msg-key (:key msg)]
      (cond
        ;; Pattern with modifiers like "ctrl+c"
        (and (string? key) (string/includes? key "+"))
        (let [parts (string/split (string/lower-case key) #"\+")
              mods (set (butlast parts))
              key-part (last parts)]
          (and (if (contains? mods "ctrl") (:ctrl msg) (not (:ctrl msg)))
               (if (contains? mods "alt") (:alt msg) (not (:alt msg)))
               (if (contains? mods "shift") (:shift msg) (not (:shift msg)))
               (or (= key-part (if (keyword? msg-key) (name msg-key) msg-key))
                   (= key-part (str msg-key)))))

        ;; Keyword matches keyword or string
        (keyword? key)
        (or (= key msg-key)
            (= (name key) msg-key))

        ;; String matches string or keyword name
        (string? key)
        (or (= key msg-key)
            (= key (when (keyword? msg-key) (name msg-key))))

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
