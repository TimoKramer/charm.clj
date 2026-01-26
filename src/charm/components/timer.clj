(ns charm.components.timer
  "Countdown timer component.

   Usage:
     (def my-timer (timer :timeout 60000))  ; 60 seconds

     ;; In update function:
     (timer-update my-timer msg)

     ;; In view function:
     (timer-view my-timer)"
  (:require [charm.style.core :as style]))

;; ---------------------------------------------------------------------------
;; Timer Messages
;; ---------------------------------------------------------------------------

(defn tick-msg
  "Create a timer tick message."
  [timer-id tag]
  {:type :timer-tick
   :timer-id timer-id
   :tag tag})

(defn tick-msg?
  "Check if a message is a timer tick."
  [msg]
  (= :timer-tick (:type msg)))

(defn timeout-msg
  "Create a timer timeout message."
  [timer-id]
  {:type :timer-timeout
   :timer-id timer-id})

(defn timeout-msg?
  "Check if a message is a timer timeout."
  [msg]
  (= :timer-timeout (:type msg)))

(defn start-stop-msg
  "Create a start/stop message."
  [timer-id running?]
  {:type :timer-start-stop
   :timer-id timer-id
   :running running?})

;; ---------------------------------------------------------------------------
;; Timer Creation
;; ---------------------------------------------------------------------------

(defn timer
  "Create a timer component.

   Options:
     :timeout   - Time in milliseconds (default 0, counts up if 0)
     :interval  - Tick interval in milliseconds (default 1000)
     :running   - Start running (default true)
     :style     - Style for timer display
     :id        - Unique ID"
  [& {:keys [timeout interval running style id]
      :or {timeout 0
           interval 1000
           running true
           id (rand-int 1000000)}}]
  {:type :timer
   :id id
   :timeout timeout      ; remaining time in ms
   :interval interval    ; tick interval in ms
   :running running
   :tag 0
   :style style})

;; ---------------------------------------------------------------------------
;; Timer Accessors
;; ---------------------------------------------------------------------------

(defn timeout
  "Get remaining timeout in milliseconds."
  [tmr]
  (:timeout tmr))

(defn interval
  "Get tick interval in milliseconds."
  [tmr]
  (:interval tmr))

(defn running?
  "Check if timer is running."
  [tmr]
  (and (:running tmr) (pos? (:timeout tmr))))

(defn timed-out?
  "Check if timer has timed out."
  [tmr]
  (<= (:timeout tmr) 0))

(defn set-timeout
  "Set the timeout in milliseconds."
  [tmr ms]
  (assoc tmr :timeout ms))

(defn set-interval
  "Set the tick interval in milliseconds."
  [tmr ms]
  (assoc tmr :interval (max 1 ms)))

;; ---------------------------------------------------------------------------
;; Timer Commands
;; ---------------------------------------------------------------------------

(defn- tick-cmd
  "Create a command that sends a tick message after the interval."
  [tmr]
  (let [{:keys [id tag interval]} tmr
        interval-ms (long interval)]
    {:type :cmd
     :fn (fn []
           (Thread/sleep interval-ms)
           (tick-msg id tag))}))

;; ---------------------------------------------------------------------------
;; Timer Control
;; ---------------------------------------------------------------------------

(defn start
  "Start the timer, returns [timer cmd]."
  [tmr]
  (let [new-tmr (assoc tmr :running true)]
    [new-tmr (tick-cmd new-tmr)]))

(defn stop
  "Stop the timer."
  [tmr]
  [(-> tmr
       (assoc :running false)
       (update :tag inc))
   nil])

(defn toggle
  "Toggle timer running state, returns [timer cmd]."
  [tmr]
  (if (:running tmr)
    (stop tmr)
    (start tmr)))

(defn reset
  "Reset timer to initial timeout, returns [timer cmd]."
  [tmr initial-timeout]
  (let [new-tmr (-> tmr
                    (assoc :timeout initial-timeout)
                    (assoc :running true)
                    (update :tag inc))]
    [new-tmr (tick-cmd new-tmr)]))

;; ---------------------------------------------------------------------------
;; Timer Init
;; ---------------------------------------------------------------------------

(defn timer-init
  "Initialize the timer, returns [timer cmd].
   Call this to start the timer."
  [tmr]
  (if (:running tmr)
    [tmr (tick-cmd tmr)]
    [tmr nil]))

;; ---------------------------------------------------------------------------
;; Timer Update
;; ---------------------------------------------------------------------------

(defn timer-update
  "Update timer state based on a message.
   Returns [new-timer cmd] or [timer nil] if message not handled."
  [tmr msg]
  (cond
    ;; Handle tick message
    (tick-msg? msg)
    (let [{msg-id :timer-id msg-tag :tag} msg
          {:keys [id tag interval running]} tmr]
      (if (and (= msg-id id)
               (= msg-tag tag)
               running)
        (let [new-timeout (- (:timeout tmr) interval)
              new-tmr (-> tmr
                          (assoc :timeout new-timeout)
                          (update :tag inc))]
          (if (pos? new-timeout)
            [new-tmr (tick-cmd new-tmr)]
            ;; Timed out - stop and no more ticks
            [(assoc new-tmr :running false) nil]))
        [tmr nil]))

    ;; Handle start/stop message
    (= :timer-start-stop (:type msg))
    (let [{msg-id :timer-id msg-running :running} msg]
      (if (= msg-id (:id tmr))
        (if msg-running
          (start tmr)
          (stop tmr))
        [tmr nil]))

    ;; Not handled
    :else
    [tmr nil]))

;; ---------------------------------------------------------------------------
;; Timer View
;; ---------------------------------------------------------------------------

(defn- format-duration
  "Format milliseconds as a human-readable duration."
  [ms]
  (let [total-seconds (quot (Math/abs (long ms)) 1000)
        hours (quot total-seconds 3600)
        minutes (quot (rem total-seconds 3600) 60)
        seconds (rem total-seconds 60)
        negative? (neg? ms)]
    (str (when negative? "-")
         (cond
           (pos? hours)
           (format "%d:%02d:%02d" hours minutes seconds)

           (pos? minutes)
           (format "%d:%02d" minutes seconds)

           :else
           (format "%ds" seconds)))))

(defn timer-view
  "Render the timer to a string."
  [tmr]
  (let [text (format-duration (:timeout tmr))]
    (if-let [s (:style tmr)]
      (style/render s text)
      text)))

;; ---------------------------------------------------------------------------
;; Convenience
;; ---------------------------------------------------------------------------

(defn for-timer?
  "Check if a message is for this timer."
  [tmr msg]
  (and (or (tick-msg? msg) (timeout-msg? msg))
       (= (:id tmr) (:timer-id msg))))
