(ns charm.components.help
  "Help component for displaying key bindings.

   Usage:
     (def bindings [{:key \"j/k\" :desc \"up/down\"}
                    {:key \"q\" :desc \"quit\"}])
     (def my-help (help bindings))

     ;; In view function:
     (help-view my-help)"
  (:require [charm.style.core :as style]
            [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Help Creation
;; ---------------------------------------------------------------------------

(defn help
  "Create a help component.

   Bindings is a sequence of maps with :key and :desc keys, or a sequence
   of [key desc] pairs.

   Options:
     :width          - Maximum width (0 = unlimited)
     :separator      - Separator between bindings (default \" • \")
     :show-all       - Show full help instead of short (default false)
     :key-style      - Style for key text
     :desc-style     - Style for description text
     :separator-style - Style for separator
     :bg             - Background color for the entire help bar
     :ellipsis       - Ellipsis when truncated (default \"…\")
     :id             - Unique ID"
  [bindings & {:keys [width separator show-all
                      key-style desc-style separator-style
                      bg ellipsis id]
               :or {width 0
                    separator " • "
                    show-all false
                    ellipsis "…"
                    id (rand-int 1000000)}}]
  {:type :help
   :id id
   :bindings (vec (map (fn [b]
                         (if (vector? b)
                           {:key (first b) :desc (second b)}
                           b))
                       bindings))
   :width width
   :separator separator
   :show-all show-all
   :bg bg
   :key-style (cond-> (or key-style (style/style :bold true))
                bg (assoc :bg bg))
   :desc-style (cond-> (or desc-style (style/style :fg 240))
                 bg (assoc :bg bg))
   :separator-style (cond-> (or separator-style (style/style :fg 240))
                      bg (assoc :bg bg))
   :ellipsis ellipsis})

;; ---------------------------------------------------------------------------
;; Help Accessors
;; ---------------------------------------------------------------------------

(defn bindings
  "Get the bindings."
  [hlp]
  (:bindings hlp))

(defn set-bindings
  "Set the bindings."
  [hlp bs]
  (assoc hlp :bindings (vec bs)))

(defn add-binding
  "Add a binding."
  [hlp key desc]
  (update hlp :bindings conj {:key key :desc desc}))

(defn set-width
  "Set the width constraint."
  [hlp w]
  (assoc hlp :width w))

(defn show-all?
  "Check if showing full help."
  [hlp]
  (:show-all hlp))

(defn set-show-all
  "Set whether to show full help."
  [hlp show?]
  (assoc hlp :show-all show?))

(defn toggle-show-all
  "Toggle between short and full help."
  [hlp]
  (update hlp :show-all not))

;; ---------------------------------------------------------------------------
;; Help View
;; ---------------------------------------------------------------------------

(defn- render-bg
  "Render text with the help background color, or return as-is if no bg."
  [bg text]
  (if bg
    (style/render (style/style :bg bg) text)
    text))

(defn- render-binding
  "Render a single binding."
  [hlp {key :key desc :desc}]
  (let [{:keys [bg key-style desc-style]} hlp]
    (str (style/render key-style key)
         (render-bg bg " ")
         (style/render desc-style desc))))

(defn short-help-view
  "Render short help (single line)."
  [hlp]
  (let [{:keys [bindings width separator separator-style ellipsis]} hlp
        sep (style/render separator-style separator)]
    (if (zero? width)
      ;; No width constraint
      (str/join sep (map #(render-binding hlp %) bindings))
      ;; With width constraint - truncate as needed
      (loop [result []
             remaining bindings
             current-width 0]
        (if (empty? remaining)
          (str/join sep result)
          (let [binding (first remaining)
                rendered (render-binding hlp binding)
                sep-width (if (empty? result) 0 (count separator))
                ;; Approximate width (ANSI codes make exact measurement complex)
                item-width (+ (count (:key binding))
                              1
                              (count (:desc binding)))
                new-width (+ current-width sep-width item-width)]
            (if (and (pos? width) (> new-width width) (seq result))
              ;; Would exceed width, add ellipsis and stop
              (str (str/join sep result) sep (render-bg (:bg hlp) ellipsis))
              (recur (conj result rendered)
                     (rest remaining)
                     new-width))))))))

(defn full-help-view
  "Render full help (multi-line grouped)."
  [hlp]
  (str/join "\n"
            (for [b (:bindings hlp)]
              (render-binding hlp b))))

;; ---------------------------------------------------------------------------
;; Help Update (stateless - typically just toggle show-all)
;; ---------------------------------------------------------------------------

(defn help-update
  "Update help state. Help components don't typically handle messages
   directly - use toggle-show-all for ? key handling in your update fn.
   Returns [help nil]."
  [hlp _msg]
  [hlp nil])

(defn help-init
  "Initialize help, returns [help cmd]."
  [hlp]
  [hlp nil])

;; ---------------------------------------------------------------------------
;; Convenience
;; ---------------------------------------------------------------------------

(defn from-pairs
  "Create bindings from pairs of [key desc] or interleaved key desc args.

   Examples:
     (from-pairs [\"j/k\" \"up/down\"] [\"q\" \"quit\"])
     (from-pairs \"j/k\" \"up/down\" \"q\" \"quit\")"
  [& args]
  (let [pairs (cond
                ;; Single arg that's a seq of seqs: [[k1 d1] [k2 d2]]
                (and (= 1 (count args))
                     (sequential? (first args))
                     (sequential? (first (first args))))
                (first args)

                ;; Multiple vector args: [k1 d1] [k2 d2]
                (and (seq args)
                     (every? sequential? args)
                     (every? #(= 2 (count %)) args))
                args

                ;; Interleaved strings: k1 d1 k2 d2
                :else
                (partition 2 args))]
    (mapv (fn [[k d]] {:key k :desc d}) pairs)))
