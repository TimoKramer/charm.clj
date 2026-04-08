(ns examples.timer
  "Simple CLI timer - 'sleep with progress'.

   Usage:
     clj -M:timer 5m           # 5 minute timer
     clj -M:timer 30s          # 30 seconds
     clj -M:timer 1h30m        # 1 hour 30 minutes
     clj -M:timer 90           # 90 seconds (default unit)
     clj -M:timer -n \"Tea\" 3m  # Named timer"
  (:require
   [charm.components.progress :as progress]
   [charm.components.timer :as timer]
   [charm.message :as msg]
   [charm.program :as program]
   [charm.style.core :as style]
   [clojure.string :as str])
  (:gen-class))

;; ---------------------------------------------------------------------------
;; Duration Parsing
;; ---------------------------------------------------------------------------

(defn parse-duration
  "Parse duration string to milliseconds.
   Supports: '5m', '30s', '1h30m', '1h30m45s', '90' (seconds)"
  [s]
  (when (and s (not (str/blank? s)))
    (let [s (str/lower-case (str/trim s))]
      (if (re-matches #"\d+" s)
        ;; Plain number = seconds
        (* (parse-long s) 1000)
        ;; Parse h/m/s components
        (let [hours   (when-let [[_ h] (re-find #"(\d+)h" s)] (parse-long h))
              minutes (when-let [[_ m] (re-find #"(\d+)m" s)] (parse-long m))
              seconds (when-let [[_ s] (re-find #"(\d+)s" s)] (parse-long s))]
          (when (or hours minutes seconds)
            (+ (* (or hours 0) 3600000)
               (* (or minutes 0) 60000)
               (* (or seconds 0) 1000))))))))

;; ---------------------------------------------------------------------------
;; Argument Parsing
;; ---------------------------------------------------------------------------

(defn parse-args
  "Parse command line arguments.
   Returns {:name nil|string, :duration-ms number} or {:error string}"
  [args]
  (loop [remaining args
         result {:name nil :duration-ms nil}]
    (if (empty? remaining)
      (if (:duration-ms result)
        result
        {:error "No duration specified"})
      (let [[arg & rest] remaining]
        (cond
          ;; -n NAME flag
          (= "-n" arg)
          (if (seq rest)
            (recur (next rest) (assoc result :name (first rest)))
            {:error "Missing name after -n"})

          ;; Duration argument
          :else
          (if-let [ms (parse-duration arg)]
            (recur rest (assoc result :duration-ms ms))
            {:error (str "Invalid duration: " arg)}))))))

;; ---------------------------------------------------------------------------
;; Time Formatting
;; ---------------------------------------------------------------------------

(defn format-remaining
  "Format milliseconds as human-readable remaining time."
  [ms]
  (let [total-seconds (max 0 (quot ms 1000))
        hours   (quot total-seconds 3600)
        minutes (quot (rem total-seconds 3600) 60)
        seconds (rem total-seconds 60)]
    (cond
      (pos? hours)
      (format "%d:%02d:%02d remaining" hours minutes seconds)

      (pos? minutes)
      (format "%d:%02d remaining" minutes seconds)

      :else
      (format "%d seconds remaining" seconds))))

;; ---------------------------------------------------------------------------
;; View
;; ---------------------------------------------------------------------------

(def name-style
  (style/style :fg (style/rgb 180 140 255) :bold true))

(def time-style
  (style/style :fg (style/rgb 140 140 140)))

(defn gradient-color
  "Interpolate from cyan to magenta based on progress.
   When dim? is true, returns a darker version."
  ([p] (gradient-color p false))
  ([p dim?]
   (let [p (double p)
         ;; Start: cyan (0, 220, 255) -> End: magenta (255, 100, 255)
         r (+ 0 (* p 255))
         g (- 220 (* p 120))
         b 255
         ;; Apply dimming factor (0.3 = 30% brightness)
         factor (if dim? 0.8 1.0)]
     (style/rgb (int (* r factor))
                (int (* g factor))
                (int (* b factor))))))

(defn view [state]
  (let [{:keys [name timer total-ms]} state
        remaining (max 0 (timer/timeout timer))
        elapsed (- total-ms remaining)
        p (if (pos? total-ms) (/ elapsed total-ms) 0.0)
        bar (progress/progress-bar :width 30
                                   :percent p
                                   :bar-style :thick
                                   :full-style (style/style :fg (gradient-color p))
                                   :empty-style (style/style :fg (gradient-color p true)))]
    (str (when name
           (str (style/render name-style name) "\n"))
         (progress/progress-view bar)
         "  "
         (style/render time-style (format-remaining remaining)))))

;; ---------------------------------------------------------------------------
;; Update
;; ---------------------------------------------------------------------------

(defn update-fn [state msg]
  (cond
    ;; Quit on q or Ctrl+C
    (or (msg/key-match? msg "q")
        (msg/key-match? msg "ctrl+c")
        (msg/key-match? msg "esc"))
    [state program/quit-cmd]

    ;; Timer tick
    :else
    (let [[new-timer cmd] (timer/timer-update (:timer state) msg)
          new-state (assoc state :timer new-timer)]
      ;; Auto-quit when timer completes
      (if (timer/timed-out? new-timer)
        [new-state program/quit-cmd]
        [new-state cmd]))))

;; ---------------------------------------------------------------------------
;; Init
;; ---------------------------------------------------------------------------

(defn init [opts]
  (fn []
    (let [t (timer/timer :timeout (:duration-ms opts)
                         :interval 100
                         :running true)
          [t cmd] (timer/timer-init t)]
      [{:name (:name opts)
        :timer t
        :total-ms (:duration-ms opts)}
       cmd])))

;; ---------------------------------------------------------------------------
;; Main
;; ---------------------------------------------------------------------------

(defn usage []
  (println "Usage: timer [OPTIONS] DURATION")
  (println)
  (println "A simple CLI timer - 'sleep with progress'")
  (println)
  (println "Options:")
  (println "  -n NAME    Give the timer a name")
  (println)
  (println "Duration examples:")
  (println "  5m         5 minutes")
  (println "  30s        30 seconds")
  (println "  1h30m      1 hour 30 minutes")
  (println "  90         90 seconds (default unit)")
  (println)
  (println "Press q or Ctrl+C to quit early"))

(defn -main [& args]
  (if (or (empty? args)
          (some #{"-h" "--help"} args))
    (usage)
    (let [opts (parse-args args)]
      (if (:error opts)
        (do
          (println "Error:" (:error opts))
          (println)
          (usage)
          (System/exit 1))
        (do
          (program/run {:init (init opts)
                        :update update-fn
                        :view view
                        :alt-screen false
                        :hide-cursor true})
          (println))))))  ; Newline after progress bar
