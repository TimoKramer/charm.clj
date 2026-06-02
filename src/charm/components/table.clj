(ns charm.components.table
  "Table component with aligned columns, optional borders, and row cursor.

   Usage:
     (def tbl (table [{:title \"Name\" :width 20} {:title \"Value\" :width 30}]
                     [[\"foo\" \"bar\"] [\"baz\" \"qux\"]]
                     :cursor 0))

     ;; In update function:
     (table-update tbl msg)

     ;; In view function:
     (table-view tbl)"
  (:require
   [charm.ansi.width :as w]
   [charm.message :as msg]
   [charm.style.core :as style]
   [clojure.string :as str]
   [clojure.walk :refer [stringify-keys]]))

;; ---------------------------------------------------------------------------
;; Key Bindings
;; ---------------------------------------------------------------------------

(def ^:private default-keys
  "Default key bindings for table navigation."
  {:cursor-up   ["up" "k"]
   :cursor-down ["down" "j"]
   :page-up     ["pgup" "ctrl+u"]
   :page-down   ["pgdown" "ctrl+d"]
   :go-to-start ["home" "g"]
   :go-to-end   ["end" "G"]})

(defn- matches-binding?
  "Check if a message matches any key in a binding."
  [m binding]
  (some #(msg/key-match? m %) binding))

;; ---------------------------------------------------------------------------
;; Table Creation
;; ---------------------------------------------------------------------------

(defn table-map->cols+rows
  "Convert a tabular map format to separate columns vector and rows vector (vec of vecs).

  * Args
    * tbl-map: a tabular map structure.
        ` [{:name \"John\" :family-name \"Doe\"} {:name \"Jane\" :family-name \"Dough\"}]`
  * Returns
    `[tbl-headers tbl-rows]`"
  [tbl-map]
  (let [tbl-headers (vec (keys (stringify-keys (first tbl-map))))
        tbl-rows (mapv #(vec (vals %)) tbl-map)]
    [tbl-headers tbl-rows]))

(defn- columnsv->charm-headers
  "Convert vector of strings that represent headers, to a headers format
  consumable by charm.clj tables."
  [headersv & {:keys [header-opts]}]
  {:pre [(and (sequential? headersv) (not (empty? headersv)))]}
  (let [header-default-width 20
        header-opts-str (stringify-keys header-opts)]
    (map (fn [header]
           (let [col-opts (get header-opts-str header)]
             (if (nil? col-opts)
               {:title header :width header-default-width}
               {:title (if (col-opts "title")
                         (col-opts "title")
                         header)
                :width (if (col-opts "width")
                         (col-opts "width")
                         header-default-width)})))
         headersv)))

(defn table
  "Create a table component.

  Two formats of input data is supported:
  1. Explicit columns and rows:
     Columns is a vector of column definitions:
     `[{:title \"Name\" :width 20} {:title \"Value\" :width 30}]`

     Rows is a vector of row vectors (each row has one value per column):
     `[[\"foo\" \"bar\"] [\"baz\" \"qux\"]]`
  2. Tabular map data. Key names will automatically become column titles, and their
     values will be row data.
     ```
     [{:name \"John\" :family-name \"Doe\"}
      {:name \"Jane\" :family-name \"Dough\"}]
     ```
     It is also recommended to pass `header-opts` with this type of invocation
     otherwise, defaults will be used.
     - `header-opts` is a map for specifying options like width and title of columns.
       ```
       {:name {:title \"Name\" :width 20}
        :family-name {:title \"Family Name\" :width 35}}
       ```

   Options:
     :cursor       - Row cursor position (nil = not interactive, int = selected row)
     :height       - Visible height in rows (0 = show all rows)
     :header?      - Show header row (default true)
     :header-style - Style for header text
     :header-opts? - Header options (for using with tabular map)
     :row-style    - Style for normal row text
     :cursor-style - Style for selected row text
     :keys         - Custom key bindings
     :id           - Unique ID"
  ([table-map]
   (let [[columnsv rowsv] (table-map->cols+rows table-map)
         charm-headers (columnsv->charm-headers columnsv :header-opts nil)]
     (table charm-headers rowsv)))
  ([table-map {:keys [header-opts] :as opts}]
   (cond
     (map? opts)
     (let [[columnsv rowsv] (table-map->cols+rows table-map)
           charm-headers (columnsv->charm-headers columnsv
                                                  :header-opts header-opts)]
       (table charm-headers rowsv opts))

     (vector? opts)
     (table table-map opts nil)

     :else
     (throw (IllegalArgumentException.
             (str "Expected second argument to be a map (tabular map options) "
                  "or a vector (explicit row data), got: " (pr-str opts))))))
  ([columns rows & {:keys [cursor height header?
                           header-style row-style cursor-style
                           keys id]
                    :or {height 0
                         header? true
                         id (rand-int 1000000)}}]
   {:type :table
    :id id
    :columns (vec columns)
    :rows (vec rows)
    :cursor cursor
    :offset 0
    :height height
    :header? header?
    :header-style (or header-style (style/style :bold true))
    :row-style row-style
    :cursor-style (or cursor-style (style/style :fg :cyan :bold true))
    :keys (merge default-keys keys)}))

;; ---------------------------------------------------------------------------
;; Table Accessors
;; ---------------------------------------------------------------------------

(defn table-rows
  "Get all rows."
  [tbl]
  (:rows tbl))

(defn table-set-rows
  "Set the rows, adjusting cursor if needed."
  [tbl new-rows]
  (let [new-rows (vec new-rows)
        cursor (:cursor tbl)
        new-cursor (when cursor
                     (if (empty? new-rows)
                       0
                       (min cursor (dec (count new-rows)))))]
    (-> tbl
        (assoc :rows new-rows)
        (assoc :cursor new-cursor)
        (assoc :offset 0))))

(defn table-cursor
  "Get the cursor position (row index or nil)."
  [tbl]
  (:cursor tbl))

(defn table-selected-row
  "Get the currently selected row data, or nil."
  [tbl]
  (when-let [cursor (:cursor tbl)]
    (get (:rows tbl) cursor)))

(defn table-set-cursor
  "Set the cursor position."
  [tbl idx]
  (let [count (count (:rows tbl))
        new-cursor (if (zero? count)
                     0
                     (max 0 (min idx (dec count))))]
    (assoc tbl :cursor new-cursor)))

(defn table-row-count
  "Get the number of rows."
  [tbl]
  (count (:rows tbl)))

;; ---------------------------------------------------------------------------
;; Table Navigation
;; ---------------------------------------------------------------------------

(defn- visible-height
  "Get the number of visible rows."
  [tbl]
  (let [h (:height tbl)
        total (table-row-count tbl)]
    (if (or (zero? h) (> total h))
      (if (zero? h) total h)
      total)))

(defn- update-offset
  "Update scroll offset to keep cursor visible."
  [tbl]
  (let [{:keys [cursor offset height]} tbl]
    (if (or (nil? cursor) (zero? height))
      tbl
      (let [visible (visible-height tbl)]
        (cond
          (< cursor offset)
          (assoc tbl :offset cursor)

          (>= cursor (+ offset visible))
          (assoc tbl :offset (- cursor visible -1))

          :else
          tbl)))))

(defn cursor-up
  "Move cursor up."
  [tbl]
  (when-let [cursor (:cursor tbl)]
    (-> tbl
        (assoc :cursor (max 0 (dec cursor)))
        update-offset)))

(defn cursor-down
  "Move cursor down."
  [tbl]
  (when-let [cursor (:cursor tbl)]
    (let [max-idx (max 0 (dec (table-row-count tbl)))]
      (-> tbl
          (assoc :cursor (min max-idx (inc cursor)))
          update-offset))))

(defn page-up
  "Move cursor up by one page."
  [tbl]
  (when-let [cursor (:cursor tbl)]
    (let [page-size (max 1 (visible-height tbl))]
      (-> tbl
          (assoc :cursor (max 0 (- cursor page-size)))
          update-offset))))

(defn page-down
  "Move cursor down by one page."
  [tbl]
  (when-let [cursor (:cursor tbl)]
    (let [page-size (max 1 (visible-height tbl))
          max-idx (max 0 (dec (table-row-count tbl)))]
      (-> tbl
          (assoc :cursor (min max-idx (+ cursor page-size)))
          update-offset))))

(defn go-to-start
  "Move cursor to start."
  [tbl]
  (when-let [_ (:cursor tbl)]
    (-> tbl
        (assoc :cursor 0)
        (assoc :offset 0))))

(defn go-to-end
  "Move cursor to end."
  [tbl]
  (when-let [_ (:cursor tbl)]
    (let [max-idx (max 0 (dec (table-row-count tbl)))]
      (-> tbl
          (assoc :cursor max-idx)
          update-offset))))

;; ---------------------------------------------------------------------------
;; Table Update
;; ---------------------------------------------------------------------------

(defn table-update
  "Update table state based on a message.
   Returns [new-table cmd] or [table nil] if message not handled."
  [tbl msg]
  (if (nil? (:cursor tbl))
    [tbl nil]
    (let [keys (:keys tbl)]
      (cond
        (matches-binding? msg (:cursor-up keys))
        [(cursor-up tbl) nil]

        (matches-binding? msg (:cursor-down keys))
        [(cursor-down tbl) nil]

        (matches-binding? msg (:page-up keys))
        [(page-up tbl) nil]

        (matches-binding? msg (:page-down keys))
        [(page-down tbl) nil]

        (matches-binding? msg (:go-to-start keys))
        [(go-to-start tbl) nil]

        (matches-binding? msg (:go-to-end keys))
        [(go-to-end tbl) nil]

        :else
        [tbl nil]))))

;; ---------------------------------------------------------------------------
;; Table View
;; ---------------------------------------------------------------------------

(defn- render-cell
  "Render a single cell, padded to column width."
  [value col-width cell-style]
  (let [text (str value)
        text (if (> (w/string-width text) col-width)
               (w/truncate text col-width :tail "…")
               (w/pad-right text col-width))]
    (if cell-style
      (style/render cell-style text)
      text)))

(defn- render-row
  "Render a row of cells."
  [columns values cell-style separator]
  (str/join separator
            (map (fn [col val]
                   (render-cell val (:width col) cell-style))
                 columns values)))

(defn table-view
  "Render the table to a string.

   Options can be passed to override the separator:
     :separator - String between columns (default \"  \")"
  ([tbl] (table-view tbl {}))
  ([tbl {:keys [separator] :or {separator "  "}}]
   (let [{:keys [columns rows cursor offset height
                 header? header-style row-style cursor-style]} tbl
         ;; Header
         header-line (when header?
                       (render-row columns (mapv :title columns) header-style separator))
         ;; Determine visible rows
         visible-h (visible-height tbl)
         visible-rows (if (zero? height)
                        (map-indexed vector rows)
                        (map-indexed
                         (fn [i row] [(+ offset i) row])
                         (subvec rows offset (min (count rows) (+ offset visible-h)))))
         ;; Render rows
         row-lines (map (fn [[idx row]]
                          (let [selected? (= idx cursor)
                                s (if selected? cursor-style row-style)]
                            (render-row columns row s separator)))
                        visible-rows)]
     (str/join "\n"
               (if header-line
                 (cons header-line row-lines)
                 row-lines)))))

;; ---------------------------------------------------------------------------
;; Table Init
;; ---------------------------------------------------------------------------

(defn table-init
  "Initialize the table, returns [table cmd]."
  [tbl]
  [tbl nil])
