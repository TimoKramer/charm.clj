# Table

Rows in aligned columns, with an optional header and an optional cursor.

## Quick Example

```clojure
(require '[charm.components.table :as table])

(def tbl (table/table [{:title "Name" :width 20}
                       {:title "Role" :width 12}]
                      [["Ada Lovelace" "mathematician"]
                       ["Grace Hopper" "rear admiral"]]
                      :cursor 0))

;; In update function
(let [[tbl cmd] (table/table-update tbl msg)]
  ;; the cursor has moved if msg was a navigation key
  )

;; In view function
(table/table-view tbl)
;; Name                  Role
;; Ada Lovelace          mathematici…
;; Grace Hopper          rear admiral
```

## Creation Options

```clojure
(table/table columns rows & options)
```

`columns` is a vector of `{:title "…" :width n}`. `:width` is required — it is
what makes the columns line up. `rows` is a vector of row vectors, one value per
column; values are run through `str`, so they need not be strings.

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:cursor` | int or nil | `nil` | Selected row index; `nil` means not interactive |
| `:height` | int | `0` | Visible rows; `0` shows every row |
| `:header?` | boolean | `true` | Show the header row |
| `:header-style` | style | bold | Style for the header |
| `:row-style` | style | `nil` | Style for ordinary rows |
| `:cursor-style` | style | bold cyan | Style for the selected row |
| `:keys` | map | defaults | Custom key bindings |
| `:id` | any | counter | Unique identifier |

A cell wider than its column is truncated with `…`; a narrower one is padded.
Both are measured by display width, so CJK characters and emoji line up and
styled cells keep their styling.

Pass `:cursor nil` for a table that only displays. The navigation keys then do
nothing, and no row is highlighted.

## Key Bindings

| Action | Keys |
|--------|------|
| Cursor up | `Up`, `k` |
| Cursor down | `Down`, `j` |
| Page up | `PageUp`, `Ctrl+U` |
| Page down | `PageDown`, `Ctrl+D` |
| First row | `Home`, `g` |
| Last row | `End`, `G` |

Override any of them with `:keys`; what you pass is merged over the defaults.

## Functions

### table-update

```clojure
(table/table-update tbl msg) ; => [tbl cmd]
```

Move the cursor in response to a navigation key, scrolling the visible window
when `:height` is set. Returns the table unchanged for anything it does not
recognise.

### table-view

```clojure
(table/table-view tbl)                        ; => string
(table/table-view tbl {:separator " │ "})     ; custom column separator
```

The separator defaults to two spaces.

### Rows and cursor

```clojure
(table/table-rows tbl)              ; all rows
(table/table-set-rows tbl new-rows) ; replace them; the cursor is clamped and
                                    ; the visible window scrolls back to the top
(table/table-row-count tbl)

(table/table-cursor tbl)            ; row index, or nil
(table/table-selected-row tbl)      ; the row vector itself, or nil
(table/table-set-cursor tbl 3)      ; clamped to the row range

;; The same movements the key bindings drive
(table/cursor-up tbl)   (table/cursor-down tbl)
(table/page-up tbl)     (table/page-down tbl)
(table/go-to-start tbl) (table/go-to-end tbl)
```

## Full Example

```clojure
(ns my-app
  (:require
   [charm.components.table :as table]
   [charm.message :as msg]
   [charm.program :as program]))

(def columns [{:title "File" :width 30}
              {:title "Size" :width 10}])

(def rows [["project.clj" "1.2K"]
           ["deps.edn" "842B"]
           ["README.md" "4.1K"]])

(defn init []
  [{:tbl (table/table columns rows :cursor 0 :height 10)} nil])

(defn update-fn [state msg]
  (cond
    (msg/key-match? msg "q")
    [state program/quit-cmd]

    (msg/key-match? msg :enter)
    [(assoc state :chosen (table/table-selected-row (:tbl state))) nil]

    :else
    (let [[tbl cmd] (table/table-update (:tbl state) msg)]
      [(assoc state :tbl tbl) cmd])))

(defn view [state]
  (str (table/table-view (:tbl state)) "\n\n"
       (if-let [chosen (:chosen state)]
         (str "Chose: " (first chosen))
         "j/k to move, Enter to choose, q to quit")))

(defn -main [& _]
  (program/run {:init init :update update-fn :view view :alt-screen true}))
```

## Scrolling long tables

With `:height` set, the table shows that many rows and scrolls the window to keep
the cursor visible. Without it every row is rendered, and a table taller than the
terminal is trimmed by the renderer rather than scrolled — see
[`:overflow`](../api/program.md#views-taller-than-the-terminal).
