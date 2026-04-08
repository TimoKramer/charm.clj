(ns charm.input.mouse
  "Mouse event parsing for terminal input.

   Supports:
   - X10 mouse encoding (CSI M followed by 3 bytes)
   - SGR mouse encoding (CSI < params M/m)")

;; ---------------------------------------------------------------------------
;; Mouse Button Constants
;; ---------------------------------------------------------------------------

(def button-none -1)
(def button-left 0)
(def button-middle 1)
(def button-right 2)
(def button-release 3)
(def button-wheel-up 4)
(def button-wheel-down 5)
(def button-wheel-left 6)
(def button-wheel-right 7)
(def button-back 8)
(def button-forward 9)
(def button-extra-10 10)
(def button-extra-11 11)

;; ---------------------------------------------------------------------------
;; Mouse Action Types
;; ---------------------------------------------------------------------------

(def action-press :press)
(def action-release :release)
(def action-motion :motion)

;; ---------------------------------------------------------------------------
;; X10 Mouse Parsing (Traditional)
;; ---------------------------------------------------------------------------

(defn parse-x10-button
  "Parse the button byte from X10 mouse encoding.
   Returns {:button int :shift bool :alt bool :ctrl bool :motion bool}"
  [b]
  (let [;; Modifier flags
        shift (pos? (bit-and b 0x04))
        alt   (pos? (bit-and b 0x08))
        ctrl  (pos? (bit-and b 0x10))
        motion (pos? (bit-and b 0x20))
        ;; Button bits (lower 2 bits + bit 6 for extended)
        button-base (bit-and b 0x03)
        extended (pos? (bit-and b 0x40))]
    {:button (if extended
               (+ 4 button-base)  ; Wheel/extended buttons
               (if (= button-base 3)
                 button-release
                 button-base))
     :shift shift
     :alt alt
     :ctrl ctrl
     :motion motion}))

(defn parse-x10-mouse
  "Parse an X10 mouse sequence.
   Input: 3 bytes after 'CSI M' (button, x+32, y+32)
   Returns mouse event map or nil."
  [b1 b2 b3]
  (let [{:keys [button shift alt ctrl motion]} (parse-x10-button (- b1 32))
        x (- b2 32)
        y (- b3 32)
        action (cond
                 motion action-motion
                 (= button button-release) action-release
                 :else action-press)]
    {:type :mouse
     :x x
     :y y
     :button (if (= action action-release) button-none button)
     :action action
     :shift shift
     :alt alt
     :ctrl ctrl}))

;; ---------------------------------------------------------------------------
;; SGR Mouse Parsing (Extended)
;; ---------------------------------------------------------------------------

(def ^:private sgr-pattern
  "Pattern for SGR mouse sequences: CSI < Pb ; Px ; Py M/m"
  #"\x1b\[<(\d+);(\d+);(\d+)([Mm])")

(defn parse-sgr-button
  "Parse the button code from SGR mouse encoding."
  [code]
  (let [motion (pos? (bit-and code 32))
        shift  (pos? (bit-and code 4))
        alt    (pos? (bit-and code 8))
        ctrl   (pos? (bit-and code 16))
        button-code (bit-and code 0x43)]  ; bits 0-1 and 6
    {:button (case button-code
               0  button-left
               1  button-middle
               2  button-right
               64 button-wheel-up
               65 button-wheel-down
               66 button-wheel-left
               67 button-wheel-right
               128 button-back
               129 button-forward
               130 button-extra-10
               131 button-extra-11
               button-none)
     :shift shift
     :alt alt
     :ctrl ctrl
     :motion motion}))

(defn parse-sgr-mouse
  "Parse an SGR mouse sequence string.
   Input: full sequence including CSI
   Returns mouse event map or nil."
  [s]
  (when-let [match (re-find sgr-pattern s)]
    (let [[_ code-str x-str y-str final] match
          code (parse-long code-str)
          {:keys [button shift alt ctrl motion]} (parse-sgr-button code)
          x (parse-long x-str)
          y (parse-long y-str)
          released (= final "m")
          action (cond
                   motion action-motion
                   released action-release
                   :else action-press)]
      {:type :mouse
       :x x
       :y y
       :button (if released button-none button)
       :action action
       :shift shift
       :alt alt
       :ctrl ctrl})))

;; ---------------------------------------------------------------------------
;; Mouse Event Utilities
;; ---------------------------------------------------------------------------

(defn mouse-event?
  "Check if an event is a mouse event."
  [event]
  (= (:type event) :mouse))

(defn click?
  "Check if a mouse event is a click (press action)."
  [event]
  (and (mouse-event? event)
       (= (:action event) action-press)))

(defn release?
  "Check if a mouse event is a release."
  [event]
  (and (mouse-event? event)
       (= (:action event) action-release)))

(defn motion?
  "Check if a mouse event is motion."
  [event]
  (and (mouse-event? event)
       (= (:action event) action-motion)))

(defn wheel?
  "Check if a mouse event is a wheel event."
  [event]
  (and (mouse-event? event)
       (contains? #{button-wheel-up button-wheel-down
                    button-wheel-left button-wheel-right}
                  (:button event))))

(defn left-click?
  "Check if a mouse event is a left click."
  [event]
  (and (click? event) (= (:button event) button-left)))

(defn right-click?
  "Check if a mouse event is a right click."
  [event]
  (and (click? event) (= (:button event) button-right)))

(defn middle-click?
  "Check if a mouse event is a middle click."
  [event]
  (and (click? event) (= (:button event) button-middle)))

(defn wheel-up?
  "Check if a mouse event is a wheel up."
  [event]
  (and (wheel? event) (= (:button event) button-wheel-up)))

(defn wheel-down?
  "Check if a mouse event is a wheel down."
  [event]
  (and (wheel? event) (= (:button event) button-wheel-down)))
