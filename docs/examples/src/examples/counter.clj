(ns examples.counter
  (:require
   [charm.message :as msg]
   [charm.program :as program]
   [charm.style.border :as border]
   [charm.style.core :as style]))

(def state (atom 0))

(def title-style
  (style/style :fg style/magenta :bold true))

(def count-style
  (style/style :fg style/cyan
               :padding [0 1]
               :border border/rounded))

(defn update-fn [state msg]
  (cond
    ;; Quit on q or Ctrl+C
    (or (msg/key-match? msg "q")
        (msg/key-match? msg "ctrl+c"))
    [state program/quit-cmd]

    ;; Increment on k or up arrow
    (or (msg/key-match? msg "k")
        (msg/key-match? msg :up))
    [(update state :count inc) nil]

    ;; Decrement on j or down arrow
    (or (msg/key-match? msg "j")
        (msg/key-match? msg :down))
    [(update state :count dec) nil]

    ;; Ignore other messages
    :else
    [state nil]))

(defn view [state]
  (str (style/render title-style "Counter App") "\n\n"
       (style/render count-style (str (:count state))) "\n\n"
       "j/k or arrows to change\n"
       "q to quit"))

(defn -main [& args]
  (program/run {:init {:count 0}
                :update update-fn
                :view view
                :alt-screen true}))
