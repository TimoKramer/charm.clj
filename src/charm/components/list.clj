(ns charm.components.list
  "Scrollable list component with item selection.

   Usage:
     (def my-list (item-list [\"Apple\" \"Banana\" \"Cherry\"]))

     ;; In update function:
     (list-update my-list msg)

     ;; In view function:
     (list-view my-list)"
  (:require [charm.components.id :as id]
            [charm.style.core :as style]
            [charm.message :as msg]
            [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Key Bindings
;; ---------------------------------------------------------------------------

(def ^:private default-keys
  "Default key bindings for list navigation."
  {:cursor-up     ["up" "k" "ctrl+p"]
   :cursor-down   ["down" "j" "ctrl+n"]
   :page-up       ["page-up" "ctrl+u"]
   :page-down     ["page-down" "ctrl+d"]
   :go-to-start   ["home" "g"]
   :go-to-end     ["end" "G"]})

(defn- matches-binding?
  "Check if a message matches any key in a binding."
  [m binding]
  (some #(msg/key-match? m %) binding))

;; ---------------------------------------------------------------------------
;; Item Protocol
;; ---------------------------------------------------------------------------

(defprotocol ListItem
  "Protocol for items in a list."
  (item-title [item] "Get the display title for the item.")
  (item-description [item] "Get an optional description for the item."))

;; Default implementations for common types
(extend-protocol ListItem
  String
  (item-title [s] s)
  (item-description [_] nil)

  clojure.lang.IPersistentMap
  (item-title [m] (or (:title m) (:name m) (str m)))
  (item-description [m] (or (:description m) (:desc m))))

;; ---------------------------------------------------------------------------
;; List Creation
;; ---------------------------------------------------------------------------

(defn item-list
  "Create a list component.

   Items can be strings, maps with :title/:description, or any type
   implementing the ListItem protocol.

   Options:
     :height           - Visible height in lines (0 = show all)
     :width            - Width constraint (0 = unlimited)
     :cursor           - Initial cursor position (default 0)
     :title            - Optional list title
     :show-title       - Show title (default true if title provided)
     :cursor-style     - Style for selected item
     :item-style       - Style for unselected items
     :title-style      - Style for title
     :cursor-prefix    - Prefix for selected item (default \"> \")
     :item-prefix      - Prefix for unselected items (default \"  \")
     :show-descriptions - Show item descriptions (default false)
     :infinite-scroll  - Wrap around at ends (default false)
     :id               - Unique ID"
  [items & {:keys [height width cursor title show-title
                   cursor-style item-style title-style
                   cursor-prefix item-prefix
                   show-descriptions infinite-scroll id]
            :or {height 0
                 width 0
                 cursor 0
                 show-title true
                 cursor-prefix "> "
                 item-prefix "  "
                 show-descriptions false
                 infinite-scroll false
                 id (id/next-id)}}]
  {:type :list
   :id id
   :items (vec items)
   :cursor (min cursor (max 0 (dec (count items))))
   :offset 0  ; Scroll offset
   :height height
   :width width
   :title title
   :show-title (and show-title title)
   :cursor-style (or cursor-style
                     (style/style :fg :cyan :bold true))
   :item-style item-style
   :title-style (or title-style
                    (style/style :bold true))
   :cursor-prefix cursor-prefix
   :item-prefix item-prefix
   :show-descriptions show-descriptions
   :infinite-scroll infinite-scroll
   :keys default-keys})

;; ---------------------------------------------------------------------------
;; List Accessors
;; ---------------------------------------------------------------------------

(defn items
  "Get all items in the list."
  [lst]
  (:items lst))

(defn item-count
  "Get the number of items."
  [lst]
  (count (:items lst)))

(defn selected-index
  "Get the currently selected index."
  [lst]
  (:cursor lst))

(defn selected-item
  "Get the currently selected item, or nil if list is empty."
  [lst]
  (get (:items lst) (:cursor lst)))

(defn set-items
  "Set the items, adjusting cursor if needed."
  [lst new-items]
  (let [new-items (vec new-items)
        new-cursor (min (:cursor lst) (max 0 (dec (count new-items))))]
    (-> lst
        (assoc :items new-items)
        (assoc :cursor new-cursor)
        (assoc :offset 0))))

(defn select
  "Select an item by index."
  [lst index]
  (let [count (item-count lst)
        new-cursor (if (zero? count)
                     0
                     (max 0 (min index (dec count))))]
    (assoc lst :cursor new-cursor)))

(defn set-height
  "Set the visible height."
  [lst height]
  (assoc lst :height height))

;; ---------------------------------------------------------------------------
;; List Navigation
;; ---------------------------------------------------------------------------

(defn- visible-height
  "Get the number of visible items."
  [lst]
  (let [h (:height lst)
        total (item-count lst)]
    (if (or (zero? h) (> total h))
      (if (zero? h) total h)
      total)))

(defn- update-offset
  "Update scroll offset to keep cursor visible."
  [lst]
  (let [{:keys [cursor offset height]} lst
        visible (visible-height lst)]
    (cond
      ;; No scrolling needed
      (zero? height)
      lst

      ;; Cursor above visible area
      (< cursor offset)
      (assoc lst :offset cursor)

      ;; Cursor below visible area
      (>= cursor (+ offset visible))
      (assoc lst :offset (- cursor visible -1))

      :else
      lst)))

(defn cursor-up
  "Move cursor up."
  [lst]
  (let [{:keys [cursor infinite-scroll]} lst
        count (item-count lst)]
    (if (zero? count)
      lst
      (let [new-cursor (dec cursor)
            new-cursor (if (neg? new-cursor)
                         (if infinite-scroll (dec count) 0)
                         new-cursor)]
        (-> lst
            (assoc :cursor new-cursor)
            update-offset)))))

(defn cursor-down
  "Move cursor down."
  [lst]
  (let [{:keys [cursor infinite-scroll]} lst
        count (item-count lst)]
    (if (zero? count)
      lst
      (let [new-cursor (inc cursor)
            new-cursor (if (>= new-cursor count)
                         (if infinite-scroll 0 (dec count))
                         new-cursor)]
        (-> lst
            (assoc :cursor new-cursor)
            update-offset)))))

(defn page-up
  "Move cursor up by one page."
  [lst]
  (let [page-size (max 1 (visible-height lst))
        new-cursor (max 0 (- (:cursor lst) page-size))]
    (-> lst
        (assoc :cursor new-cursor)
        update-offset)))

(defn page-down
  "Move cursor down by one page."
  [lst]
  (let [page-size (max 1 (visible-height lst))
        count (item-count lst)
        new-cursor (min (dec count) (+ (:cursor lst) page-size))]
    (-> lst
        (assoc :cursor (max 0 new-cursor))
        update-offset)))

(defn go-to-start
  "Move cursor to start."
  [lst]
  (-> lst
      (assoc :cursor 0)
      (assoc :offset 0)))

(defn go-to-end
  "Move cursor to end."
  [lst]
  (let [count (item-count lst)]
    (-> lst
        (assoc :cursor (max 0 (dec count)))
        update-offset)))

;; ---------------------------------------------------------------------------
;; List Update
;; ---------------------------------------------------------------------------

(defn list-update
  "Update list state based on a message.
   Returns [new-list cmd] or [list nil] if message not handled."
  [lst msg]
  (let [keys (:keys lst)]
    (cond
      (matches-binding? msg (:cursor-up keys))
      [(cursor-up lst) nil]

      (matches-binding? msg (:cursor-down keys))
      [(cursor-down lst) nil]

      (matches-binding? msg (:page-up keys))
      [(page-up lst) nil]

      (matches-binding? msg (:page-down keys))
      [(page-down lst) nil]

      (matches-binding? msg (:go-to-start keys))
      [(go-to-start lst) nil]

      (matches-binding? msg (:go-to-end keys))
      [(go-to-end lst) nil]

      :else
      [lst nil])))

;; ---------------------------------------------------------------------------
;; List View
;; ---------------------------------------------------------------------------

(defn- render-item
  "Render a single list item."
  [lst index item selected?]
  (let [{:keys [cursor-style item-style cursor-prefix item-prefix
                show-descriptions width]} lst
        prefix (if selected? cursor-prefix item-prefix)
        title (item-title item)
        desc (when show-descriptions (item-description item))
        styled-title (if selected?
                       (style/render cursor-style title)
                       (if item-style
                         (style/render item-style title)
                         title))
        line (str prefix styled-title)]
    (if desc
      (str line "\n" (str/join "" (repeat (count prefix) " "))
           (style/render (style/style :fg 240) desc))
      line)))

(defn list-view
  "Render the list to a string."
  [lst]
  (let [{:keys [items cursor offset height title show-title title-style]} lst
        visible-h (visible-height lst)
        visible-items (if (zero? height)
                        items
                        (subvec items offset (min (count items) (+ offset visible-h))))
        title-str (when show-title
                    (str (if title-style
                           (style/render title-style title)
                           title)
                         "\n"))
        item-lines (map-indexed
                    (fn [i item]
                      (let [actual-index (+ offset i)
                            selected? (= actual-index cursor)]
                        (render-item lst actual-index item selected?)))
                    visible-items)]
    (str title-str (str/join "\n" item-lines))))

;; ---------------------------------------------------------------------------
;; List Init
;; ---------------------------------------------------------------------------

(defn list-init
  "Initialize the list, returns [list cmd].
   Currently just returns the list with no command."
  [lst]
  [lst nil])

;; ---------------------------------------------------------------------------
;; Convenience Functions
;; ---------------------------------------------------------------------------

(defn filter-items
  "Filter items by a predicate function.
   Returns a new list with only matching items."
  [lst pred]
  (let [filtered (filterv pred (:items lst))]
    (set-items lst filtered)))

(defn find-item
  "Find the first item matching a predicate.
   Returns the index or nil."
  [lst pred]
  (first (keep-indexed
          (fn [i item]
            (when (pred item) i))
          (:items lst))))

(defn select-first-match
  "Select the first item matching a predicate."
  [lst pred]
  (if-let [idx (find-item lst pred)]
    (select lst idx)
    lst))
