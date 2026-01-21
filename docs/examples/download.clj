(ns examples.download
  "Simulated download demonstrating all progress bar styles."
  (:require [charm.core :as charm]))

(def bar-style-names
  "All available progress bar styles."
  [:default :ascii :thin :thick :blocks :arrows :dots :brackets])

(def title-style
  (charm/style :fg charm/magenta :bold true))

(def label-style
  (charm/style :fg charm/cyan))

(def complete-style
  (charm/style :fg charm/green :bold true))

(def hint-style
  (charm/style :fg 240))

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
               [style-name (charm/progress-bar :width 30
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
  (every? charm/progress-complete? (vals bars)))

(defn update-fn [state msg]
  (cond
    ;; Quit
    (or (charm/key-match? msg "q")
        (charm/key-match? msg "ctrl+c")
        (charm/key-match? msg "esc"))
    [state charm/quit-cmd]

    ;; Space to start
    (and (not (:running state))
         (charm/key-match? msg " "))
    (start-download state)

    ;; R to reset
    (charm/key-match? msg "r")
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
                               (assoc bars style-name (charm/progress-increment bar increment))))
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
        label (name style-name)
        complete? (charm/progress-complete? bar)]
    (str (charm/render label-style (format "%-10s" label))
         " "
         (charm/progress-view bar)
         (when complete?
           (str " " (charm/render complete-style "Done!"))))))

(defn view [state]
  (let [{:keys [bars running]} state
        all-done? (all-complete? bars)]
    (str (charm/render title-style "Download Demo") "\n"
         (charm/render hint-style "Demonstrating all progress bar styles") "\n\n"
         (clojure.string/join "\n"
                              (map #(render-bar bars %) bar-style-names))
         "\n\n"
         (cond
           all-done?
           (str (charm/render complete-style "All downloads complete!") "\n\n"
                (charm/render hint-style "Press R to restart, Q to quit"))

           running
           (charm/render hint-style "Downloading... Press R to reset, Q to quit")

           :else
           (charm/render hint-style "Press Space to start download, Q to quit")))))

(defn -main [& _args]
  (charm/run {:init init
              :update update-fn
              :view view
              :alt-screen true}))
