(ns examples.sketch
  "A mouse-driven drawing pad.
   Click/drag to draw, right-click to erase, scroll to change brush.
   Demonstrates mouse interaction in charm.clj."
  (:require
   [charm.message :as msg]
   [charm.program :as program]
   [charm.style.core :as style]
   [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Brushes
;; ---------------------------------------------------------------------------

(def brushes ["█" "░" "▒" "▓" "●" "◆" "★" "♦" "╳" "○"])

;; ---------------------------------------------------------------------------
;; Styles
;; ---------------------------------------------------------------------------

(def title-style
  (style/style :fg style/magenta :bold true))

(def help-style
  (style/style :fg style/white :faint true))

(def brush-style
  (style/style :fg style/cyan :bold true))

;; ---------------------------------------------------------------------------
;; Init
;; ---------------------------------------------------------------------------

(defn init []
  [{:canvas {}
    :brush-idx 0
    :width 80
    :height 24
    :mouse-pos nil}
   nil])

;; ---------------------------------------------------------------------------
;; Update
;; ---------------------------------------------------------------------------

(defn- draw [canvas x y brush]
  (assoc canvas [x y] brush))

(defn update-fn [state msg]
  (cond
    (or (msg/key-match? msg "q")
        (msg/key-match? msg "ctrl+c"))
    [state program/quit-cmd]

    (msg/key-match? msg "c")
    [(assoc state :canvas {}) nil]

    (msg/window-size? msg)
    [(assoc state
            :width (:width msg)
            :height (:height msg))
     nil]

    ;; Left click or drag: draw
    (or (msg/left-click? msg)
        (and (msg/motion? msg) (= :left (:button msg))))
    (let [brush (nth brushes (:brush-idx state))]
      [(-> state
           (update :canvas draw (:x msg) (:y msg) brush)
           (assoc :mouse-pos [(:x msg) (:y msg)]))
       nil])

    ;; Right click or drag: erase
    (or (msg/right-click? msg)
        (and (msg/motion? msg) (= :right (:button msg))))
    [(-> state
         (update :canvas dissoc [(:x msg) (:y msg)])
         (assoc :mouse-pos [(:x msg) (:y msg)]))
     nil]

    ;; Track mouse position on any motion
    (msg/motion? msg)
    [(assoc state :mouse-pos [(:x msg) (:y msg)]) nil]

    ;; Scroll wheel: cycle brush
    (msg/wheel-up? msg)
    [(update state :brush-idx #(mod (dec %) (count brushes))) nil]

    (msg/wheel-down? msg)
    [(update state :brush-idx #(mod (inc %) (count brushes))) nil]

    :else
    [state nil]))

;; ---------------------------------------------------------------------------
;; View
;; ---------------------------------------------------------------------------

(defn view [state]
  (let [{:keys [canvas brush-idx width height mouse-pos]} state
        brush (nth brushes brush-idx)
        ;; Reserve 2 lines for status bar
        canvas-height (- height 2)]
    (str
     ;; Canvas area
     (str/join
      "\n"
      (for [y (range 1 (inc canvas-height))]
        (apply str
               (for [x (range 1 (inc width))]
                 (get canvas [x y] " ")))))
     "\n"
     ;; Status bar
     (style/render title-style "Sketch")
     " "
     (style/render brush-style (str "Brush: " brush))
     (when mouse-pos
       (str " " (style/styled (str "(" (first mouse-pos) "," (second mouse-pos) ")")
                              :faint true)))
     "\n"
     (style/render help-style "click:draw  right-click:erase  scroll:brush  c:clear  q:quit"))))

(defn -main [& _args]
  (program/run {:init init
                :update update-fn
                :view view
                :alt-screen true
                :mouse :cell}))
