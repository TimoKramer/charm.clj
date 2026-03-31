(ns examples.emoji-width
  "Demonstrates grapheme cluster width handling with emoji in tables and borders.

   Shows that ZWJ sequences, flags, and skin-tone emoji are measured correctly
   when JLine 4 Mode 2027 is active (see ADR 006)."
  (:require
   [charm.core :as charm]
   [charm.ansi.width :as w]))

;; ---------------------------------------------------------------------------
;; Data
;; ---------------------------------------------------------------------------

(def emoji-rows
  [;; ZWJ sequences
   ["👨‍👩‍👧"      "Family (ZWJ)"         "3 codepoints joined by ZWJ"]
   ["👩‍💻"      "Woman Technologist"    "ZWJ sequence"]
   ["🏳️‍🌈"      "Rainbow Flag"         "Flag + VS16 + ZWJ + Rainbow"]
   ;; Flags (regional indicators)
   ["🇩🇪"      "Germany"              "2 regional indicator symbols"]
   ["🇯🇵"      "Japan"                "2 regional indicator symbols"]
   ["🇧🇷"      "Brazil"               "2 regional indicator symbols"]
   ;; Skin tone modifiers
   ["👋🏽"      "Wave (medium skin)"   "Base + Fitzpatrick modifier"]
   ["👍🏿"      "Thumbs Up (dark)"     "Base + Fitzpatrick modifier"]
   ["🧑🏻‍🔬"      "Scientist (light)"    "Skin tone + ZWJ + microscope"]
   ;; Simple wide emoji
   ["🎉"      "Party Popper"         "Single codepoint, width 2"]
   ["🦀"      "Crab"                 "Ferris! Single codepoint"]
   ["⚡"      "Lightning"            "Misc symbol, width 1 or 2"]])

;; ---------------------------------------------------------------------------
;; Styles
;; ---------------------------------------------------------------------------

(def title-style
  (charm/style :bold true :fg charm/magenta))

(def subtitle-style
  (charm/style :fg charm/cyan :italic true))

(def box-style
  (charm/style :border charm/rounded-border
               :padding [0 1]
               :border-fg charm/cyan))

(def header-style
  (charm/style :bold true :fg charm/yellow))

(def cursor-style
  (charm/style :bold true :fg charm/green))

;; ---------------------------------------------------------------------------
;; Init / Update / View
;; ---------------------------------------------------------------------------

(defn init []
  (let [tbl (charm/table [{:title "Emoji" :width 6}
                          {:title "Name" :width 22}
                          {:title "Type" :width 34}
                          {:title "Expect" :width 6}
                          {:title "Actual" :width 6}]
                         (mapv (fn [[emoji name desc]]
                                 [emoji name desc "2" "-"])
                               emoji-rows)
                         :cursor 0
                         :header-style header-style
                         :cursor-style cursor-style)]
    [tbl nil]))

(defn update-fn [tbl msg]
  (cond
    (or (charm/key-match? msg "q")
        (charm/key-match? msg "ctrl+c"))
    [tbl charm/quit-cmd]

    :else
    (charm/table-update tbl msg)))

(defn view [tbl]
  (let [rows (mapv (fn [[emoji name desc]]
                     [emoji name desc "2" (str (w/string-width emoji))])
                   emoji-rows)
        tbl (assoc tbl :rows rows)]
    (str (charm/render title-style "Grapheme Cluster Width Demo") "\n"
         (charm/render subtitle-style "Emoji should align neatly if Mode 2027 is active") "\n\n"
         (charm/render box-style (charm/table-view tbl {:separator " │ "}))
         "\n\n"
         "j/k navigate  q quit")))

;; ---------------------------------------------------------------------------
;; Main
;; ---------------------------------------------------------------------------

(defn -main [& _args]
  (charm/run {:init init
              :update update-fn
              :view view
              :alt-screen true}))
