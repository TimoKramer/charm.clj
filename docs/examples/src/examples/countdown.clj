(ns examples.countdown
  "Countdown timer demonstrating timer component with start/stop controls."
  (:require
   [charm.components.timer :as timer]
   [charm.message :as msg]
   [charm.program :as program]
   [charm.style.border :as border]
   [charm.style.core :as style]))

(def initial-time (* 60 1000)) ; 60 seconds

(def title-style
  (style/style :fg style/magenta :bold true))

(def timer-style
  (style/style :fg style/cyan
               :bold true
               :padding [1 3]
               :border border/rounded))

(def timer-stopped-style
  (style/style :fg style/yellow
               :bold true
               :padding [1 3]
               :border border/rounded))

(def timer-done-style
  (style/style :fg style/red
               :bold true
               :padding [1 3]
               :border border/rounded))

(def button-style
  (style/style :fg style/green :bold true))

(def button-inactive-style
  (style/style :fg 240))

(def hint-style
  (style/style :fg 240))

(defn init []
  (let [t (timer/timer :timeout initial-time
                       :interval 100
                       :running false)
        [t cmd] (timer/timer-init t)]
    [{:timer t
      :initial-time initial-time}
     cmd]))

(defn update-fn [state msg]
  (cond
    ;; Quit
    (or (msg/key-match? msg "q")
        (msg/key-match? msg "ctrl+c")
        (msg/key-match? msg "esc"))
    [state program/quit-cmd]

    ;; Space to toggle start/stop
    (msg/key-match? msg " ")
    (let [[new-timer cmd] (timer/toggle (:timer state))]
      [(assoc state :timer new-timer) cmd])

    ;; R to reset
    (msg/key-match? msg "r")
    (let [[new-timer cmd] (timer/start
                           (timer/timer :timeout (:initial-time state)
                                        :interval 100
                                        :running true))]
      [(assoc state :timer new-timer) cmd])

    ;; Up/k to add 10 seconds
    (or (msg/key-match? msg :up)
        (msg/key-match? msg "k"))
    (let [t (:timer state)
          new-timeout (+ (timer/timeout t) 10000)
          new-timer (assoc t :timeout new-timeout)]
      [(assoc state :timer new-timer :initial-time new-timeout) nil])

    ;; Down/j to subtract 10 seconds (min 10 seconds)
    (or (msg/key-match? msg :down)
        (msg/key-match? msg "j"))
    (let [t (:timer state)
          new-timeout (max 10000 (- (timer/timeout t) 10000))
          new-timer (assoc t :timeout new-timeout)]
      [(assoc state :timer new-timer :initial-time new-timeout) nil])

    ;; Timer tick
    :else
    (let [[new-timer cmd] (timer/timer-update (:timer state) msg)]
      [(assoc state :timer new-timer) cmd])))

(defn format-time
  "Format milliseconds as MM:SS.s"
  [ms]
  (let [total-seconds (/ (Math/abs ms) 1000.0)
        minutes (int (/ total-seconds 60))
        seconds (mod total-seconds 60)]
    (format "%02d:%04.1f" minutes seconds)))

(defn view [state]
  (let [t (:timer state)
        timeout (timer/timeout t)
        running? (timer/running? t)
        done? (timer/timed-out? t)
        time-str (format-time timeout)
        s (cond
            done? timer-done-style
            running? timer-style
            :else timer-stopped-style)
        status (cond
                 done? "Time's up!"
                 running? "Running"
                 :else "Paused")]
    (str (style/render title-style "Countdown Timer") "\n\n"
         (style/render s time-str) "\n\n"
         (style/render (if running? button-style hint-style) status) "\n\n"
         (style/render hint-style "Controls:") "\n"
         (style/render (if done? button-inactive-style button-style) "  [Space]")
         " " (if running? "Pause" "Start") "\n"
         (style/render button-style "  [R]")
         " Reset\n"
         (style/render (if running? button-inactive-style button-style) "  [j/k]")
         " -/+ 10 seconds\n"
         (style/render button-style "  [Q]")
         " Quit")))

(defn -main [& _args]
  (program/run {:init init
                :update update-fn
                :view view
                :alt-screen true}))
