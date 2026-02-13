(ns examples.cheatsheet
  "Clojure Cheatsheet TUI — single filterable view.

   All sections are shown at once. Typing filters inline, showing only
   matching functions. Enter opens an overlay with documentation.

   Run: cd docs/examples && clj -M -m examples.cheatsheet"
  (:require
   [charm.core :as charm]
   [charm.ansi.width :as ansi-width]
   [charm.style.border :as border]
   [charm.style.overlay :as overlay]
   [examples.cheatsheet.data :as data]
   [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Colors — Clojure brand palette
;; ---------------------------------------------------------------------------

(def clj-green (charm/hex "#63B132"))
(def clj-blue (charm/hex "#5881D8"))
(def clj-light-blue (charm/hex "#8FB5FE"))
(def clj-light-green (charm/hex "#91DC47"))

;; ---------------------------------------------------------------------------
;; Styles
;; ---------------------------------------------------------------------------

(def title-style
  (charm/style :fg clj-green :bold true))

(def section-rule-style
  (charm/style :fg 240))

(def subsection-title-style
  (charm/style :fg clj-blue :bold true))

(def group-label-style
  (charm/style :fg 240))

(def fn-selected-style
  (charm/style :fg :black :bg clj-green :bold true))

(def overlay-title-style
  (charm/style :fg clj-blue :bold true))

(def overlay-arglists-style
  (charm/style :fg clj-green))

(def overlay-section-rule-style
  (charm/style :fg 240))

(def overlay-seealso-style
  (charm/style :fg clj-blue))

(def dim-style
  (charm/style :fg 240))

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn- word-wrap
  "Wrap text to fit within width columns."
  [text width]
  (if (<= (count text) width)
    [text]
    (loop [remaining text
           lines []]
      (if (or (str/blank? remaining) (<= (count remaining) width))
        (if (str/blank? remaining) lines (conj lines remaining))
        (let [chunk (subs remaining 0 width)
              ;; Find last space in chunk for clean break
              last-space (str/last-index-of chunk " ")]
          (if (and last-space (pos? last-space))
            (recur (subs remaining (inc (int last-space)))
                   (conj lines (subs remaining 0 (int last-space))))
            ;; No space found, break at width
            (recur (subs remaining width)
                   (conj lines chunk))))))))

(defn- section-rule
  "Render a section header as ── Name ─────────────"
  [section-name width]
  (let [prefix (str "── " section-name " ")
        prefix-width (ansi-width/string-width prefix)
        fill-width (max 0 (- width prefix-width))
        fill (apply str (repeat fill-width "─"))]
    (charm/render section-rule-style (str prefix fill))))

(defn- overlay-section-rule
  "Render an overlay section divider ── Title ─────"
  [title width]
  (let [prefix (str "── " title " ")
        prefix-width (count prefix)
        fill-width (max 0 (- width prefix-width))
        fill (apply str (repeat fill-width "─"))]
    (charm/render overlay-section-rule-style (str prefix fill))))

;; ---------------------------------------------------------------------------
;; State Initialization
;; ---------------------------------------------------------------------------

(defn- make-help [mode filter-focused]
  (if (= mode :overlay)
    (charm/help-from-pairs
     "↑↓" "scroll" "ctrl+d/u" "page" "n/p" "see-also" "esc" "close")
    (if filter-focused
      (charm/help-from-pairs
       "↑↓" "navigate" "enter" "details" "esc" "clear" "ctrl+c" "quit")
      (charm/help-from-pairs
       "↑↓" "navigate" "enter" "details" "/" "filter" "q" "quit"))))

(defn init []
  (let [state {:mode :browse
               :filter-input (charm/text-input :prompt ""
                                               :placeholder "filter..."
                                               :focused false)
               :filter-focused false
               :cursor 0
               :scroll-offset 0
               :overlay {:fn-sym nil
                         :viewport (charm/viewport "" :height 20)
                         :see-alsos []
                         :see-also-idx 0}
               :help (charm/help (make-help :browse false) :width 70)
               :width 80
               :height 24}]
    [state (charm/cmd data/load-docs!)]))

;; ---------------------------------------------------------------------------
;; Computed Values
;; ---------------------------------------------------------------------------

(defn- current-query [state]
  (charm/text-input-value (:filter-input state)))

(defn- filtered-sections [state]
  (data/filter-sections data/sections (current-query state)))

(defn- current-flat-fns [state]
  (data/flat-fns (filtered-sections state)))

;; ---------------------------------------------------------------------------
;; Cursor Helpers
;; ---------------------------------------------------------------------------

(defn- clamp-cursor [state]
  (let [fns (current-flat-fns state)
        max-idx (max 0 (dec (count fns)))]
    (update state :cursor #(max 0 (min % max-idx)))))

(defn- cursor-sym [state]
  (let [fns (current-flat-fns state)]
    (when (and (seq fns) (< (:cursor state) (count fns)))
      (:sym (nth fns (:cursor state))))))

;; ---------------------------------------------------------------------------
;; Overlay Mode
;; ---------------------------------------------------------------------------

(defn- build-overlay-content
  "Build beautifully formatted overlay text for a function symbol."
  [fn-sym content-width]
  (let [doc-data (data/lookup fn-sym)
        n (name fn-sym)]
    (str/join
     "\n"
     (concat
      ;; Arglists
      (when-let [arglists (:arglists doc-data)]
        (concat
         (for [args arglists]
           (charm/render overlay-arglists-style
                         (str "  (" n " " args ")")))
         [""]))
      ;; Docstring
      (when-let [doc (:doc doc-data)]
        (let [wrap-width (max 20 (- content-width 2))]
          (concat
           (mapcat #(word-wrap % wrap-width) (str/split-lines doc))
           [""])))
      ;; Examples
      (when-let [examples (:examples doc-data)]
        (concat
         [(overlay-section-rule "Examples" content-width)
          ""]
         (mapcat (fn [ex]
                   (concat
                    (str/split-lines (str/trim (str ex)))
                    [""]))
                 (take 3 examples))))
      ;; See-alsos
      (when-let [see-alsos (:see-alsos doc-data)]
        [(overlay-section-rule "See also" content-width)
         ""
         (str/join "  "
                   (map (fn [kw]
                          (charm/render overlay-seealso-style (name kw)))
                        (take 12 see-alsos)))])
      ;; Fallback
      (when (nil? doc-data)
        ["No documentation found."
         ""
         "ClojureDocs data may still be loading."])))))

(defn- extract-see-alsos [fn-sym]
  (when-let [doc-data (data/lookup fn-sym)]
    (when-let [see-alsos (:see-alsos doc-data)]
      (vec (map (fn [kw] (symbol (namespace kw) (name kw))) see-alsos)))))

(defn- open-overlay [state fn-sym]
  (let [overlay-width (max 40 (min 60 (- (:width state) 10)))
        content-width (- overlay-width 4)
        content (build-overlay-content fn-sym content-width)
        overlay-height (max 5 (min (- (:height state) 6) 20))
        see-alsos (or (extract-see-alsos fn-sym) [])]
    (-> state
        (assoc :mode :overlay)
        (assoc :overlay {:fn-sym fn-sym
                         :viewport (charm/viewport content
                                                   :height overlay-height
                                                   :width content-width)
                         :see-alsos see-alsos
                         :see-also-idx 0})
        (assoc :help (charm/help (make-help :overlay false) :width 70)))))

(defn- close-overlay [state]
  (-> state
      (assoc :mode :browse)
      (assoc :help (charm/help (make-help :browse (:filter-focused state)) :width 70))))

(defn- next-see-also [state]
  (let [see-alsos (get-in state [:overlay :see-alsos])
        idx (get-in state [:overlay :see-also-idx] 0)]
    (if (and (seq see-alsos) (< (inc idx) (count see-alsos)))
      (open-overlay state (nth see-alsos (inc idx)))
      state)))

(defn- prev-see-also [state]
  (let [see-alsos (get-in state [:overlay :see-alsos])
        idx (get-in state [:overlay :see-also-idx] 0)]
    (if (and (seq see-alsos) (pos? idx))
      (open-overlay state (nth see-alsos (dec idx)))
      state)))

;; ---------------------------------------------------------------------------
;; Scroll Management
;; ---------------------------------------------------------------------------

(defn- ensure-cursor-visible
  "Adjust scroll-offset so the cursor line is visible.
   cursor-line is the line index within the content area where the
   cursor function appears. content-height is the visible area height."
  [state cursor-line content-height]
  (let [offset (:scroll-offset state)]
    (cond
      ;; Cursor above visible area
      (< cursor-line offset)
      (assoc state :scroll-offset cursor-line)
      ;; Cursor below visible area
      (>= cursor-line (+ offset content-height))
      (assoc state :scroll-offset (- cursor-line content-height -1))
      ;; Already visible
      :else state)))

;; ---------------------------------------------------------------------------
;; Update
;; ---------------------------------------------------------------------------

(defn- focus-filter [state]
  (-> state
      (assoc :filter-focused true)
      (update :filter-input charm/text-input-focus)
      (assoc :help (charm/help (make-help :browse true) :width 70))))

(defn- blur-filter [state]
  (-> state
      (assoc :filter-focused false)
      (update :filter-input charm/text-input-blur)
      (assoc :help (charm/help (make-help :browse false) :width 70))))

(defn- clear-and-blur-filter [state]
  (-> state
      (update :filter-input charm/text-input-reset)
      (blur-filter)
      (assoc :cursor 0)
      (assoc :scroll-offset 0)))

(defn- move-cursor [state delta]
  (let [fns (current-flat-fns state)
        max-idx (max 0 (dec (count fns)))
        new-cursor (max 0 (min max-idx (+ (:cursor state) delta)))]
    (assoc state :cursor new-cursor)))

(defn update-fn [state msg]
  (cond
    ;; Window resize
    (charm/window-size? msg)
    [(-> state
         (assoc :width (:width msg))
         (assoc :height (:height msg)))
     nil]

    ;; Global quit
    (charm/key-match? msg "ctrl+c")
    [state charm/quit-cmd]

    :else
    (case (:mode state)
      ;; ----- Browse mode -----
      :browse
      (cond
        ;; Navigation keys work in both focused and blurred
        (or (charm/key-match? msg "down") (and (not (:filter-focused state)) (charm/key-match? msg "j")))
        [(move-cursor state 1) nil]

        (or (charm/key-match? msg "up") (and (not (:filter-focused state)) (charm/key-match? msg "k")))
        [(move-cursor state -1) nil]

        ;; Enter opens overlay
        (charm/key-match? msg "enter")
        (if-let [sym (cursor-sym state)]
          [(open-overlay state sym) nil]
          [state nil])

        ;; Escape clears filter and blurs
        (charm/key-match? msg "escape")
        (if (:filter-focused state)
          [(clear-and-blur-filter state) nil]
          [state nil])

        ;; / focuses filter (when blurred)
        (and (not (:filter-focused state)) (charm/key-match? msg "/"))
        [(focus-filter state) nil]

        ;; q quits (when blurred)
        (and (not (:filter-focused state)) (charm/key-match? msg "q"))
        [state charm/quit-cmd]

        ;; When focused, pass typing to text-input
        (:filter-focused state)
        (let [[new-input cmd] (charm/text-input-update (:filter-input state) msg)
              state (assoc state :filter-input new-input)
              ;; Reset cursor when filter changes
              state (-> state (assoc :cursor 0) (assoc :scroll-offset 0))
              state (clamp-cursor state)]
          [state cmd])

        :else
        [state nil])

      ;; ----- Overlay mode -----
      :overlay
      (cond
        (charm/key-match? msg "escape")
        [(close-overlay state) nil]

        (or (charm/key-match? msg "down") (charm/key-match? msg "j")
            (charm/key-match? msg "up") (charm/key-match? msg "k")
            (charm/key-match? msg "ctrl+d") (charm/key-match? msg "ctrl+u"))
        (let [[vp _] (charm/viewport-update (get-in state [:overlay :viewport]) msg)]
          [(assoc-in state [:overlay :viewport] vp) nil])

        (charm/key-match? msg "n")
        [(next-see-also state) nil]

        (charm/key-match? msg "p")
        [(prev-see-also state) nil]

        :else
        [state nil]))))

;; ---------------------------------------------------------------------------
;; View: Content Rendering
;; ---------------------------------------------------------------------------

(defn- render-content
  "Render the full content area. Returns {:text string :cursor-line int}
   where cursor-line is the line index of the cursor function."
  [state]
  (let [sections (filtered-sections state)
        cursor-idx (:cursor state)
        ;; Account for thick border (2 cols) + some inner margin
        width (max 40 (- (:width state) 6))
        lines (atom [])
        cursor-line (atom 0)
        ;; Track remaining cursor position through the flat list
        ;; to handle duplicate symbols correctly
        cursor-remaining (atom cursor-idx)]
    (doseq [[si section] (map-indexed vector sections)]
      (when (pos? si)
        (swap! lines conj ""))
      (swap! lines conj (section-rule (:name section) width))
      (swap! lines conj "")
      (doseq [[ssi subsection] (map-indexed vector (:subsections section))]
        (when (pos? ssi)
          (swap! lines conj ""))
        (swap! lines conj (str "  " (charm/render subsection-title-style (:name subsection))))
        (let [label-width (reduce max 1 (map #(count (:label %)) (:groups subsection)))]
          (doseq [group (:groups subsection)]
            (let [fns-str (str/join " "
                                    (map (fn [sym]
                                           (let [remaining @cursor-remaining
                                                 selected? (zero? remaining)]
                                             (swap! cursor-remaining dec)
                                             (when selected?
                                               (reset! cursor-line (count @lines)))
                                             (if selected?
                                               (charm/render fn-selected-style
                                                             (str " " (name sym) " "))
                                               (name sym))))
                                         (:fns group)))
                  label-str (charm/render group-label-style
                                          (ansi-width/pad-right (:label group) label-width))]
              (swap! lines conj (str "    " label-str "  " fns-str)))))))
    (if (empty? @lines)
      {:text (charm/render dim-style "  No matching functions") :cursor-line 0}
      {:text (str/join "\n" @lines) :cursor-line @cursor-line})))

(def filter-field-width 30)

(defn- render-filter-bar [state]
  (let [input-view (charm/text-input-view (:filter-input state))
        field-content (str "🔍 " input-view)]
    (charm/render (charm/style :bg clj-light-blue
                               :width filter-field-width
                               :padding [1 1])
                  field-content)))

(defn- render-title-bar [state]
  (let [title (charm/render title-style "Clojure Cheatsheet")
        filter-bar (render-filter-bar state)
        filter-lines (str/split-lines filter-bar)
        ;; Filter field width including padding
        filter-rendered-width (reduce max 0 (map ansi-width/string-width filter-lines))
        title-width (ansi-width/string-width title)
        ;; Account for thick border (2 cols) + left indent (2)
        gap (max 2 (- (:width state) title-width filter-rendered-width 6))
        gap-str (apply str (repeat gap " "))
        ;; Vertically center the title next to the 3-line filter field
        ;; Line 0: gap + filter top padding
        ;; Line 1: title + gap + filter content
        ;; Line 2: gap + filter bottom padding
        result-lines (map-indexed
                      (fn [i filter-line]
                        (if (= i 1)
                          (str "  " title gap-str filter-line)
                          (let [left-pad (apply str (repeat (+ 2 title-width gap) " "))]
                            (str left-pad filter-line))))
                      filter-lines)]
    (str/join "\n" result-lines)))

(defn- render-help-bar [state]
  (let [help-content (charm/help-view (:help state))]
    (charm/render (charm/style :bg clj-light-green
                               :border border/outer-half-block
                               :border-fg clj-light-green
                               :padding [0 1])
                  help-content)))

;; ---------------------------------------------------------------------------
;; View: Overlay Panel
;; ---------------------------------------------------------------------------

(defn- render-overlay-panel [state]
  (let [{:keys [fn-sym viewport]} (:overlay state)
        overlay-width (max 40 (min 60 (- (:width state) 10)))
        title (str " " (namespace fn-sym) "/" (name fn-sym) " ")
        content (charm/viewport-view viewport)
        bordered (charm/render (charm/style :border border/rounded
                                            :border-fg clj-blue
                                            :padding [0 1])
                               content)
        ;; Replace top border with title
        bordered-lines (str/split-lines bordered)
        top-line (first bordered-lines)
        top-width (ansi-width/string-width top-line)
        ;; Build new top line with title embedded
        title-styled (charm/render overlay-title-style title)
        corner-l "╭─"
        corner-r (let [title-area-width (ansi-width/string-width (str corner-l title))
                       remaining (max 0 (- top-width title-area-width 1))]
                   (str (apply str (repeat remaining "─")) "╮"))
        new-top (str (charm/render (charm/style :fg clj-blue) corner-l)
                     title-styled
                     (charm/render (charm/style :fg clj-blue) corner-r))
        final-lines (assoc (vec bordered-lines) 0 new-top)]
    (str/join "\n" final-lines)))

;; ---------------------------------------------------------------------------
;; View: Main
;; ---------------------------------------------------------------------------

(defn view [state]
  (let [width (:width state)
        height (:height state)
        ;; Border takes 2 rows + 2 cols, blank line on top takes 1 row
        ;; Inner: blank(1) + title(3) + blank(1) + content + blank(1) + help(3) = inner-height
        inner-height (max 10 (- height 2))
        content-height (max 1 (- inner-height 9))
        ;; Title bar (includes filter)
        title-bar (render-title-bar state)
        ;; Help bar
        help-bar (render-help-bar state)
        ;; Render content
        {:keys [text cursor-line]} (render-content state)
        content-lines (str/split-lines text)
        total-lines (count content-lines)
        ;; Ensure cursor is visible
        state (ensure-cursor-visible state cursor-line content-height)
        scroll-offset (:scroll-offset state)
        ;; Clip content to viewport
        visible-lines (if (<= total-lines content-height)
                        content-lines
                        (->> content-lines
                             (drop scroll-offset)
                             (take content-height)
                             vec))
        ;; Pad to fill height
        visible-lines (if (< (count visible-lines) content-height)
                        (into (vec visible-lines)
                              (repeat (- content-height (count visible-lines)) ""))
                        visible-lines)
        content-str (str/join "\n" visible-lines)
        ;; Compose inner content (blank line on top for prominence)
        inner-view (str "\n" title-bar "\n\n"
                        content-str "\n\n"
                        help-bar)
        ;; Wrap in thick border
        base-view (charm/render (charm/style :border border/thick
                                             :border-fg clj-blue)
                                inner-view)]
    ;; Apply overlay if in overlay mode
    (if (= :overlay (:mode state))
      (let [overlay-panel (render-overlay-panel state)
            base-lines (str/split-lines base-view)
            overlay-lines (str/split-lines overlay-panel)
            base-width (reduce max 1 (map ansi-width/string-width base-lines))
            overlay-width (reduce max 1 (map ansi-width/string-width overlay-lines))
            x (max 0 (quot (- base-width overlay-width) 2))
            y (max 2 (quot (- (count base-lines) (count overlay-lines)) 2))]
        (overlay/place-overlay base-view overlay-panel x y))
      base-view)))

;; ---------------------------------------------------------------------------
;; Main
;; ---------------------------------------------------------------------------

(comment
  ; "Start the cheatsheet app in the background. Returns a handle:
  ;    {:quit!  (fn [] ...) - stop the app
  ;     :result (promise)   - deref to get the final state}"
  ; []
  (def app (charm/run-async {:init init
                             :update update-fn
                             :view view
                             :alt-screen true}))
  ((:quit! app)))

(defn -main [& _args]
  (charm/run {:init init
              :update update-fn
              :view view
              :alt-screen true}))
