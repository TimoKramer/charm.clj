(ns examples.spinner-demo
  "Demonstrates all 14 spinner animation types side by side."
  (:require
   [charm.components.spinner :as spinner]
   [charm.message :as msg]
   [charm.program :as program]
   [charm.style.core :as style]
   [clojure.string :as str]))

(def spinner-names
  "All available spinner types."
  [:line :dots :dot :jump :pulse :points :globe :moon
   :monkey :meter :hamburger :ellipsis :arrows :bouncing-bar :clock])

(defn create-spinners
  "Create a map of spinner-type -> spinner for all types."
  []
  (into {}
        (map (fn [t] [t (spinner/spinner t :id t)])
             spinner-names)))

(defn init-spinners
  "Initialize all spinners and collect their commands."
  [spinners]
  (reduce (fn [[spinners cmds] [k spinner]]
            (let [[s cmd] (spinner/spinner-init spinner)]
              [(assoc spinners k s)
               (if cmd (conj cmds cmd) cmds)]))
          [spinners []]
          spinners))

(defn init
  "Initialize app state with all spinners."
  []
  (let [spinners (create-spinners)
        [spinners cmds] (init-spinners spinners)]
    [{:spinners spinners}
     (when (seq cmds)
       (apply program/batch cmds))]))

(defn update-fn [state msg]
  (cond
    ;; Quit on q or Ctrl+C
    (or (msg/key-match? msg "q")
        (msg/key-match? msg "ctrl+c")
        (msg/key-match? msg "esc"))
    [state program/quit-cmd]

    ;; Handle spinner ticks - update the matching spinner
    (= :spinner-tick (:type msg))
    (let [spinner-id (:spinner-id msg)
          spinners (:spinners state)]
      (if-let [s (get spinners spinner-id)]
        (let [[new-spinner cmd] (spinner/spinner-update s msg)]
          [(assoc-in state [:spinners spinner-id] new-spinner) cmd])
        [state nil]))

    :else
    [state nil]))

(def title-style
  (style/style :fg style/magenta :bold true))

(def label-style
  (style/style :fg style/cyan))

(defn format-spinner-row
  "Format a single spinner with its label."
  [spinners spinner-type]
  (let [s (get spinners spinner-type)
        label (name spinner-type)
        frame (spinner/spinner-view s)]
    (str (style/render label-style (format "%-12s" label)) " " frame)))

(defn view [state]
  (let [spinners (:spinners state)
        ;; Split into two columns
        col1 (take 8 spinner-names)
        col2 (drop 8 spinner-names)
        rows (map (fn [t1 t2]
                    (str (format-spinner-row spinners t1)
                         "    "
                         (if t2
                           (format-spinner-row spinners t2)
                           "")))
                  col1
                  (concat col2 (repeat nil)))]
    (str (style/render title-style "Spinner Demo") "\n"
         (style/render (style/style :fg 240) "All 14 spinner animation types") "\n\n"
         (str/join "\n" rows)
         "\n\n"
         (style/render (style/style :fg 240) "Press q to quit"))))

(defn -main [& _args]
  (program/run {:init init
                :update update-fn
                :view view
                :alt-screen true}))
