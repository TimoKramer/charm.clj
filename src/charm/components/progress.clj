(ns charm.components.progress
  "Progress bar component.

   Usage:
     (def my-progress (progress-bar :width 40))

     ;; Update progress (0.0 to 1.0):
     (set-progress my-progress 0.5)

     ;; In view function:
     (progress-view my-progress)"
  (:require [charm.components.id :as id]
            [charm.style.core :as style]))

;; ---------------------------------------------------------------------------
;; Progress Bar Styles
;; ---------------------------------------------------------------------------

(def bar-styles
  "Predefined progress bar styles."
  {:default    {:full "█" :empty "░"}
   :ascii      {:full "#" :empty "-"}
   :thin       {:full "━" :empty "─"}
   :thick      {:full "█" :empty "▒"}
   :blocks     {:full "▓" :empty "░"}
   :arrows     {:full ">" :empty " "}
   :dots       {:full "●" :empty "○"}
   :brackets   {:full "=" :empty " " :left "[" :right "]"}})

;; ---------------------------------------------------------------------------
;; Progress Bar Creation
;; ---------------------------------------------------------------------------

(defn progress-bar
  "Create a progress bar component.

   Options:
     :width           - Total width in characters (default 40)
     :percent         - Initial progress 0.0-1.0 (default 0)
     :bar-style       - Keyword from bar-styles or custom map (default :default)
     :show-percent    - Show percentage text (default false)
     :full-style      - Style for filled portion
     :empty-style     - Style for empty portion
     :percent-style   - Style for percentage text
     :id              - Unique ID"
  [& {:keys [width percent bar-style show-percent
             full-style empty-style percent-style id]
      :or {width 40
           percent 0.0
           bar-style :default
           show-percent false
           id (id/next-id)}}]
  (let [style-map (if (keyword? bar-style)
                    (get bar-styles bar-style (:default bar-styles))
                    bar-style)]
    {:type :progress
     :id id
     :width width
     :percent (max 0.0 (min 1.0 percent))
     :bar-style style-map
     :show-percent show-percent
     :full-style (or full-style (style/style :fg :cyan))
     :empty-style empty-style
     :percent-style percent-style}))

;; ---------------------------------------------------------------------------
;; Progress Bar Accessors
;; ---------------------------------------------------------------------------

(defn percent
  "Get current progress as 0.0-1.0."
  [bar]
  (:percent bar))

(defn percent-int
  "Get current progress as 0-100 integer."
  [bar]
  (int (* 100 (:percent bar))))

(defn set-progress
  "Set progress (0.0 to 1.0)."
  [bar p]
  (assoc bar :percent (max 0.0 (min 1.0 (double p)))))

(defn set-progress-int
  "Set progress as 0-100 integer."
  [bar p]
  (set-progress bar (/ p 100.0)))

(defn increment
  "Increment progress by amount (default 0.01)."
  ([bar] (increment bar 0.01))
  ([bar amount]
   (set-progress bar (+ (:percent bar) amount))))

(defn decrement
  "Decrement progress by amount (default 0.01)."
  ([bar] (decrement bar 0.01))
  ([bar amount]
   (set-progress bar (- (:percent bar) amount))))

(defn complete?
  "Check if progress is complete (100%)."
  [bar]
  (>= (:percent bar) 1.0))

(defn reset
  "Reset progress to 0."
  [bar]
  (assoc bar :percent 0.0))

;; ---------------------------------------------------------------------------
;; Progress Bar View
;; ---------------------------------------------------------------------------

(defn progress-view
  "Render the progress bar to a string."
  [bar]
  (let [{:keys [width percent bar-style show-percent
                full-style empty-style percent-style]} bar
        {:keys [full empty left right]
         :or {left "" right ""}} bar-style

        ;; Calculate bar width accounting for brackets and percent text
        percent-text (when show-percent (format " %3d%%" (int (* 100 percent))))
        bracket-width (+ (count left) (count right))
        percent-width (if show-percent (count percent-text) 0)
        bar-width (- width bracket-width percent-width)

        ;; Calculate filled/empty portions
        filled-count (int (* bar-width percent))
        empty-count (- bar-width filled-count)

        ;; Build bar parts
        filled-str (apply str (repeat filled-count full))
        empty-str (apply str (repeat empty-count empty))

        ;; Apply styles
        styled-filled (if full-style
                        (style/render full-style filled-str)
                        filled-str)
        styled-empty (if empty-style
                       (style/render empty-style empty-str)
                       empty-str)
        styled-percent (when show-percent
                         (if percent-style
                           (style/render percent-style percent-text)
                           percent-text))]

    (str left styled-filled styled-empty right styled-percent)))

;; ---------------------------------------------------------------------------
;; Progress Bar Update (stateless - no messages to handle)
;; ---------------------------------------------------------------------------

(defn progress-update
  "Update progress bar state. Progress bars are typically updated
   directly via set-progress rather than through messages.
   Returns [bar nil]."
  [bar _msg]
  [bar nil])

(defn progress-init
  "Initialize progress bar, returns [bar cmd]."
  [bar]
  [bar nil])
