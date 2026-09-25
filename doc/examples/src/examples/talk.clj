(ns examples.talk
  "10 mins lightning talk at babashka conf 2026"
  (:require
   [charm.components.help :as help]
   [charm.components.paginator :as paginator]
   [charm.components.progress :as progress]
   [charm.components.timer :as timer]
   [charm.message :as msg]
   [charm.program :as program]
   [charm.style.border :as border]
   [charm.style.color :as color]
   [charm.style.core :as style]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def items
  (->> (io/file (io/resource "talk"))
       .listFiles
       (filter #(.endsWith (.getName %) ".txt"))
       (sort-by #(.getName %))
       (mapv #(str/trimr (slurp %)))))

;; Every color is adaptive: the light variant is used on light terminal
;; backgrounds, the dark one on dark backgrounds. charm detects the background
;; once at startup and resolves these at render time.

(def accent
  "Cyan on dark, a deeper teal that stays readable on white."
  (style/adaptive "#0e7490" style/cyan))

(def title-style
  (style/style :fg (style/adaptive "#b91c1c" "#ff5f5f") :bold true))

(def item-style
  (style/style :fg nil))

(def pag-style
  (style/style :fg nil))

(def hint-style
  (style/style :faint true))

(def active-dot-style
  (style/style :fg accent :bold true))

(def inactive-dot-style
  (style/style :fg (style/adaptive 247 240)))

(def paused-style
  (style/style :fg (style/adaptive "#c2410c" (style/rgb 255 150 100)) :bold true))

(def footer-style
  (style/style :fg accent :faint true))

(def footer-text "github.com/timokramer/charm.clj")

;; Reserved rows: title (1) + paginator (1) + progress (1) + hint (1) + spacers (2) = 6
(def chrome-rows 6)

(def gradient-stops
  "green -> yellow -> red ramp, per background. The bar is mixed at render
   time from the progress value, so it picks its stops from the detected
   background rather than from style/adaptive."
  {true  {:start [100 220 140] :mid [255 200 100] :end [255 100 100]}
   false {:start [21 128 61]   :mid [180 110 0]   :end [185 28 28]}})

(defn gradient-color
  "Smooth green -> yellow -> red ramp based on progress.
   Dim variant for the empty side of the bar: darker on a dark background,
   washed out towards white on a light one."
  ([dark? p] (gradient-color dark? p false))
  ([dark? p dim?]
   (let [p (double p)
         {:keys [start mid end]} (gradient-stops (boolean dark?))
         ;; Two segments: 0.0..0.5 green->yellow, 0.5..1.0 yellow->red
         [[r1 g1 b1] [r2 g2 b2] t] (if (< p 0.5)
                                     [start mid (* p 2.0)]
                                     [mid end (* (- p 0.5) 2.0)])
         mix (fn [a b] (+ a (* t (- b a))))
         dim (fn [v] (cond
                       (not dim?) v
                       dark? (* v 0.4)
                       :else (+ v (* 0.75 (- 255 v)))))]
     (style/rgb (int (dim (mix r1 r2)))
                (int (dim (mix g1 g2)))
                (int (dim (mix b1 b2)))))))

(defn format-remaining
  "Format milliseconds as human-readable remaining time."
  [ms]
  (let [total-seconds (max 0 (quot ms 1000))
        minutes (quot total-seconds 60)
        seconds (rem total-seconds 60)]
    (format "%02d:%02d" minutes seconds)))

(defn view-timer
  "Full-width progress bar with remaining time, sized to the terminal."
  [state]
  (let [{:keys [timer phase total-ms term-w dark?]} state
        paused? (= phase :paused)
        done? (= phase :done)
        remaining (max 0 (timer/timeout timer))
        elapsed (- total-ms remaining)
        p (if (pos? total-ms) (/ elapsed total-ms) 0.0)
        color (gradient-color dark? p)
        time-text (format-remaining remaining)
        suffix (cond
                 done? "  TIME!"
                 paused? "  PAUSED"
                 :else "")
        ;; account for time text + 2 spaces gap + suffix
        bar-width (max 10 (- term-w (count time-text) (count suffix) 2))
        bar (progress/progress-bar :width bar-width
                                   :percent p
                                   :bar-style :thick
                                   :full-style (style/style :fg color)
                                   :empty-style (style/style :fg (gradient-color dark? p true)))]
    (str (progress/progress-view bar)
         "  "
         (style/render (style/style :fg color :bold true) time-text)
         (when (or paused? done?)
           (style/render paused-style suffix)))))

(defn card-style
  "Card sized to fill the terminal minus title and help."
  [{:keys [term-w term-h]}]
  (style/style :border border/double-border
               :border-fg accent
               :padding [1 2]
               :width  (- term-w 2)              ; -2 for left/right border
               :height (- term-h chrome-rows 2)))  ; -2 for top/bottom border

(defn parse-args
  "--light / --dark force the palette instead of trusting detection.
   Returns true for dark, false for light, nil to let charm decide."
  [args]
  (cond
    (some #{"--light"} args) false
    (some #{"--dark"} args) true
    :else nil))

(defn init
  ([] (init nil))
  ([forced-dark?]
   (let [talk-ms (* 10 60 1000)]
     [{:pager (paginator/paginator
               :total-pages (count items)
               :active-style active-dot-style
               :inactive-style inactive-dot-style)
       :arabic (paginator/paginator
                :total-pages (count items)
                :type :arabic
                :arabic-format "page %d of %d"
                :active-style pag-style)
       :term-w 80
       :term-h 24
       ;; :dark? comes from the :environment message charm sends at startup,
       ;; unless --light/--dark pinned it.
       :dark? (if (some? forced-dark?) forced-dark? true)
       :forced-dark? forced-dark?
       :talk-ms talk-ms
       :total-ms talk-ms
       :phase :paused
       :timer (timer/timer :timeout talk-ms
                           :interval 100
                           :running false)
       :help (help/help (help/from-pairs "←/→ or h/l" "navigate"
                                         "p" "pause/resume"
                                         "q" "quit"))}
      nil])))

(defn update-fn [state msg]
  (let [{:keys [phase]} state]
    (cond
      (msg/window-size? msg)
      [(assoc state :term-w (:width msg) :term-h (:height msg)) nil]

      (msg/environment? msg)
      [(cond-> state
         (nil? (:forced-dark? state)) (assoc :dark? (:dark-background? msg)))
       nil]

      (or (msg/key-match? msg "q")
          (msg/key-match? msg "ctrl+c")
          (msg/key-match? msg "esc"))
      [state program/quit-cmd]

      (msg/key-match? msg "p")
      (cond
        (= phase :running)
        (let [[new-timer _] (timer/stop (:timer state))]
          [(assoc state :phase :paused :timer new-timer) nil])

        (= phase :done)
        [state nil]

        :else
        (let [[new-timer cmd] (timer/start (:timer state))]
          [(assoc state :phase :running :timer new-timer) cmd]))

      (timer/tick-msg? msg)
      (let [[new-timer cmd] (timer/timer-update (:timer state) msg)
            new-state (assoc state :timer new-timer)]
        (if (timer/timed-out? new-timer)
          [(assoc new-state :phase :done) nil]
          [new-state cmd]))

      :else
      (let [[pager _] (paginator/paginator-update (:pager state) msg)
            [arabic _] (paginator/paginator-update (:arabic state) msg)]
        [(assoc state :pager pager :arabic arabic) nil]))))

(defn page-items
  "Slice items for the current page."
  [pager]
  (let [[start end] (paginator/slice-bounds pager (count items))]
    (subvec items start end)))

(defn view [state]
  (tap> state)
  ;; Adaptive colors resolve against the background charm detected at startup.
  ;; Rebinding it from state is what lets --light/--dark reach them, the same
  ;; way :dark? reaches the hand-mixed progress gradient.
  (binding [color/*dark-background?* (boolean (:dark? state))]
    (let [{:keys [pager arabic term-w term-h help]} state
          raw-items (page-items pager)
          rows (->> raw-items
                    (map #(style/render item-style %))
                    (str/join "\n"))
          ;; Card content height = card visual - 2 borders - 2 vertical padding.
          ;; The card auto-expands horizontally to fit its widest line, so use
          ;; that as the alignment target rather than the configured :width.
          content-rows (max 4 (- term-h chrome-rows 4))
          max-slide-w (->> raw-items
                           (mapcat str/split-lines)
                           (map count)
                           (apply max 0))
          content-cols (max (- term-w 6) max-slide-w)
          slide-lines (inc (count (filter #{\newline} rows)))
          pad-lines (max 1 (- content-rows slide-lines))
          footer-pad (apply str (repeat (max 0 (- content-cols (count footer-text))) " "))
          body (str rows
                    (apply str (repeat pad-lines "\n"))
                    footer-pad
                    (style/render footer-style footer-text))
          paginator (str (paginator/paginator-view pager) "   "
                         (paginator/paginator-view arabic))]
      (str (style/render title-style "babashka conf 2026 Amsterdam") "\n"
           (style/render (card-style state) body) "\n"
           (style/render pag-style paginator) "\n\n"
           (view-timer state) "\n\n"
           (style/render hint-style (help/short-help-view help))))))

(defn -main
  "Run the talk. Pass --light or --dark to override background detection."
  [& args]
  (let [forced-dark? (parse-args args)]
    (program/run {:init #(init forced-dark?)
                  :update update-fn
                  :view view
                  :alt-screen true})))

(comment
  (def app (program/run-async {:init init
                               :update #'update-fn
                               :view #'view
                               :alt-screen true}))

  ((:quit! app))

  (def help
      [{:key "←/→ or h/l", :desc "navigate"}
       {:key "p", :desc "pause/resume"} 
       {:key "q", :desc "quit"}]));; No width constraint
