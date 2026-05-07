(ns examples.talk
  "10 mins lightning talk at babashka conf 2026"
  (:require
   [charm.components.paginator :as paginator]
   [charm.message :as msg]
   [charm.program :as program]
   [charm.style.border :as border]
   [charm.style.core :as style]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def items
  (->> (io/file (io/resource "talk"))
       .listFiles
       (filter #(.endsWith (.getName %) ".txt"))
       (sort-by #(.getName %))
       (mapv slurp)))

(def title-style
  (style/style :fg style/red :bold true))

(def item-style
  (style/style :fg style/black))

(def pag-style
  (style/style :fg nil))

(def hint-style
  (style/style :faint true))

(def active-dot-style
  (style/style :fg style/cyan :bold true))

(def inactive-dot-style
  (style/style :fg 240))

;; Reserved rows: title (1) + blank (1) + blank (1) + help (1) = 4
(def chrome-rows 4)

(defn card-style
  "Card sized to fill the terminal minus title and help."
  [{:keys [term-w term-h]}]
  (style/style :border border/double-border
               :border-fg style/cyan
               :padding [1 2]
               :width  (- term-w 2)              ; -2 for left/right border
               :height (- term-h chrome-rows 2)  ; -2 for top/bottom border
               #_#_:valign :center
               #_#_:align :center))

(defn init []
  [{:pager (paginator/paginator
            :total-pages (count items)
            :active-style active-dot-style
            :inactive-style inactive-dot-style)
    :arabic (paginator/paginator
             :total-pages (count items)
             :type :arabic
             :arabic-format "PAGE %d of %d"
             :active-style pag-style)
    :term-w 80
    :term-h 24}
   nil])

(defn update-fn [state msg]
  (tap> state)
  (cond
    (msg/window-size? msg)
    [(assoc state :term-w (:width msg) :term-h (:height msg)) nil]

    (or (msg/key-match? msg "q")
        (msg/key-match? msg "ctrl+c")
        (msg/key-match? msg "esc"))
    [state program/quit-cmd]

    :else
    (let [[pager _] (paginator/paginator-update (:pager state) msg)
          [arabic _] (paginator/paginator-update (:arabic state) msg)]
      [(assoc state :pager pager :arabic arabic) nil])))

(defn page-items
  "Slice items for the current page."
  [pager]
  (let [[start end] (paginator/slice-bounds pager (count items))]
    (subvec items start end)))

(defn view [state]
  (let [{:keys [pager arabic]} state
        rows (->> (page-items pager)
                  (map (fn [i]
                         (style/render item-style i)))
                  (str/join "\n"))
        body (str rows
                  "\n\n")
        paginator (str (paginator/paginator-view pager) "   "
                       (paginator/paginator-view arabic))]
    (str (style/render title-style "babashka conf 2026 Amsterdam city") "\n"
         (style/render (card-style state) body) "\n"
         (style/render pag-style paginator) "\n\n"
         (style/render hint-style "←/→ or h/l to navigate, q to quit"))))

(defn -main [& _args]
  (program/run-async {:init init
                      :update update-fn
                      :view view
                      :alt-screen true}))

(comment
  (def app (program/run-async {:init init
                               :update #'update-fn
                               :view #'view
                               :alt-screen true}))

  ((:quit! app)))
