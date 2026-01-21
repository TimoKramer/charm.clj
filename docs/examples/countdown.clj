(ns examples.countdown
  "Countdown timer demonstrating timer component with start/stop controls."
  (:require [charm.core :as charm]))

(def initial-time (* 60 1000)) ; 60 seconds

(def title-style
  (charm/style :fg charm/magenta :bold true))

(def timer-style
  (charm/style :fg charm/cyan
               :bold true
               :padding [1 3]
               :border charm/rounded-border))

(def timer-stopped-style
  (charm/style :fg charm/yellow
               :bold true
               :padding [1 3]
               :border charm/rounded-border))

(def timer-done-style
  (charm/style :fg charm/red
               :bold true
               :padding [1 3]
               :border charm/rounded-border))

(def button-style
  (charm/style :fg charm/green :bold true))

(def button-inactive-style
  (charm/style :fg 240))

(def hint-style
  (charm/style :fg 240))

(defn init []
  (let [timer (charm/timer :timeout initial-time
                           :interval 100
                           :running false)
        [timer cmd] (charm/timer-init timer)]
    [{:timer timer
      :initial-time initial-time}
     cmd]))

(defn update-fn [state msg]
  (cond
    ;; Quit
    (or (charm/key-match? msg "q")
        (charm/key-match? msg "ctrl+c")
        (charm/key-match? msg "esc"))
    [state charm/quit-cmd]

    ;; Space to toggle start/stop
    (charm/key-match? msg " ")
    (let [[new-timer cmd] (charm/timer-toggle (:timer state))]
      [(assoc state :timer new-timer) cmd])

    ;; R to reset
    (charm/key-match? msg "r")
    (let [[new-timer cmd] (charm/timer-start
                           (charm/timer :timeout (:initial-time state)
                                        :interval 100
                                        :running true))]
      [(assoc state :timer new-timer) cmd])

    ;; Up/k to add 10 seconds
    (or (charm/key-match? msg :up)
        (charm/key-match? msg "k"))
    (let [timer (:timer state)
          new-timeout (+ (charm/timer-timeout timer) 10000)
          new-timer (assoc timer :timeout new-timeout)]
      [(assoc state :timer new-timer :initial-time new-timeout) nil])

    ;; Down/j to subtract 10 seconds (min 10 seconds)
    (or (charm/key-match? msg :down)
        (charm/key-match? msg "j"))
    (let [timer (:timer state)
          new-timeout (max 10000 (- (charm/timer-timeout timer) 10000))
          new-timer (assoc timer :timeout new-timeout)]
      [(assoc state :timer new-timer :initial-time new-timeout) nil])

    ;; Timer tick
    :else
    (let [[new-timer cmd] (charm/timer-update (:timer state) msg)]
      [(assoc state :timer new-timer) cmd])))

(defn format-time
  "Format milliseconds as MM:SS.s"
  [ms]
  (let [total-seconds (/ (Math/abs ms) 1000.0)
        minutes (int (/ total-seconds 60))
        seconds (mod total-seconds 60)]
    (format "%02d:%04.1f" minutes seconds)))

(defn view [state]
  (let [timer (:timer state)
        timeout (charm/timer-timeout timer)
        running? (charm/timer-running? timer)
        done? (charm/timer-timed-out? timer)
        time-str (format-time timeout)
        style (cond
                done? timer-done-style
                running? timer-style
                :else timer-stopped-style)
        status (cond
                 done? "Time's up!"
                 running? "Running"
                 :else "Paused")]
    (str (charm/render title-style "Countdown Timer") "\n\n"
         (charm/render style time-str) "\n\n"
         (charm/render (if running? button-style hint-style) status) "\n\n"
         (charm/render hint-style "Controls:") "\n"
         (charm/render (if done? button-inactive-style button-style) "  [Space]")
         " " (if running? "Pause" "Start") "\n"
         (charm/render button-style "  [R]")
         " Reset\n"
         (charm/render (if running? button-inactive-style button-style) "  [j/k]")
         " -/+ 10 seconds\n"
         (charm/render button-style "  [Q]")
         " Quit")))

(defn -main [& _args]
  (charm/run {:init init
              :update update-fn
              :view view
              :alt-screen true}))
