(ns examples.download
  "Simulated download demonstrating all progress bar styles."
  (:require
   [charm.components.progress :as progress]
   [charm.message :as msg]
   [charm.program :as program]
   [charm.style.core :as style]
   [clojure.string :as str]))

(def bar-style-names
  "All available progress bar styles."
  [:default :ascii :thin :thick :blocks :arrows :dots :brackets])

(def title-style
  (style/style :fg style/magenta :bold true))

(def label-style
  (style/style :fg style/cyan))

(def complete-style
  (style/style :fg style/green :bold true))

(def hint-style
  (style/style :fg 240))

(defn tick-msg
  "Create a progress tick message."
  [tag]
  {:type :progress-tick :tag tag})

(defn tick-msg?
  "Check if msg is a progress tick."
  [msg]
  (= :progress-tick (:type msg)))

(defn tick-cmd
  "Create a command that sends a tick after delay."
  [tag]
  {:type :cmd
   :fn (fn []
         (Thread/sleep 50)
         (tick-msg tag))})

(defn create-bars
  "Create progress bars for all styles."
  []
  (into {}
        (map (fn [style-name]
               [style-name (progress/progress-bar :width 30
                                                  :bar-style style-name
                                                  :show-percent true)])
             bar-style-names)))

(defn init []
  [{:bars (create-bars)
    :running false
    :tag 0}
   nil])

(defn start-download
  "Start the simulated download."
  [state]
  (let [new-tag (inc (:tag state))]
    [(-> state
         (assoc :running true)
         (assoc :tag new-tag)
         (assoc :bars (create-bars)))
     (tick-cmd new-tag)]))

(defn reset-download
  "Reset all progress bars."
  [state]
  [(-> state
       (assoc :running false)
       (update :tag inc)
       (assoc :bars (create-bars)))
   nil])

(defn all-complete?
  "Check if all bars are complete."
  [bars]
  (every? progress/complete? (vals bars)))

(defn update-fn [state msg]
  (cond
    ;; Quit
    (or (msg/key-match? msg "q")
        (msg/key-match? msg "ctrl+c")
        (msg/key-match? msg "esc"))
    [state program/quit-cmd]

    ;; Space to start
    (and (not (:running state))
         (msg/key-match? msg " "))
    (start-download state)

    ;; R to reset
    (msg/key-match? msg "r")
    (reset-download state)

    ;; Progress tick - increment all bars with random variation
    (and (tick-msg? msg)
         (= (:tag msg) (:tag state))
         (:running state))
    (let [bars (:bars state)
          ;; Increment each bar by a random amount (simulating different speeds)
          new-bars (reduce (fn [bars style-name]
                             (let [bar (get bars style-name)
                                   increment (+ 0.005 (* 0.015 (rand)))]
                               (assoc bars style-name (progress/increment bar increment))))
                           bars
                           bar-style-names)
          all-done? (all-complete? new-bars)
          new-state (-> state
                        (assoc :bars new-bars)
                        (assoc :running (not all-done?)))]
      (if all-done?
        [new-state nil]
        [new-state (tick-cmd (:tag state))]))

    :else
    [state nil]))

(defn render-bar
  "Render a single progress bar with label."
  [bars style-name]
  (let [bar (get bars style-name)
        complete? (progress/complete? bar)]
    (str (style/render label-style (format "%-10s" (name style-name)))
         " "
         (progress/progress-view bar)
         (when complete?
           (str " " (style/render complete-style "Done!"))))))

(defn view [state]
  (let [{:keys [bars running]} state
        all-done? (all-complete? bars)]
    (str (style/render title-style "Download Demo") "\n"
         (style/render hint-style "Demonstrating all progress bar styles") "\n\n"
         (str/join "\n" (map #(render-bar bars %) bar-style-names))
         "\n\n"
         (cond
           all-done?
           (str (style/render complete-style "All downloads complete!") "\n\n"
                (style/render hint-style "Press R to restart, Q to quit"))

           running
           (style/render hint-style "Downloading... Press R to reset, Q to quit")

           :else
           (style/render hint-style "Press Space to start download, Q to quit")))))

(defn -main [& _args]
  (program/run {:init init
                :update update-fn
                :view view
                :alt-screen true}))
