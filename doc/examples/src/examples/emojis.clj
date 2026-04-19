(ns examples.emojis
  "Demonstrates grapheme cluster width handling with emoji in tables and borders.

   Shows that ZWJ sequences, flags, and skin-tone emoji are measured correctly
   when JLine 4 Mode 2027 is active (see ADR 007)."
  (:require
   [charm.components.table :as table]
   [charm.message :as msg]
   [charm.program :as program]
   [charm.style.core :as style]))

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
  (style/style :bold true :fg style/magenta))

(def subtitle-style
  (style/style :fg style/cyan :italic true))

(def box-style
  (style/style :border style/rounded-border
               :padding [0 1]
               :border-fg style/cyan))

(def header-style
  (style/style :bold true :fg style/yellow))

(def cursor-style
  (style/style :bold true :fg style/green))

;; ---------------------------------------------------------------------------
;; Init / Update / View
;; ---------------------------------------------------------------------------

(defn init []
  (let [tbl (table/table [{:title "Emoji" :width 6}
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
    (or (msg/key-match? msg "q")
        (msg/key-match? msg "ctrl+c"))
    [tbl program/quit-cmd]

    :else
    (table/table-update tbl msg)))

(defn view [tbl]
  (let [rows (mapv (fn [[emoji name desc]]
                     [emoji name desc "2" (str (style/string-width emoji))])
                   emoji-rows)
        tbl (assoc tbl :rows rows)]
    (str (style/render title-style "Grapheme Cluster Width Demo") "\n"
         (style/render subtitle-style "Emoji should align neatly if Mode 2027 is active") "\n\n"
         (style/render box-style (table/table-view tbl {:separator " │ "}))
         "\n\n"
         "j/k navigate  q quit")))

;; ---------------------------------------------------------------------------
;; Main
;; ---------------------------------------------------------------------------

(defn -main [& _args]
  (program/run {:init init
                :update update-fn
                :view view
                :alt-screen true}))
