# List

Scrollable list component with keyboard navigation and item selection.

## Quick Example

```clojure
(require '[charm.core :as charm])

(def my-list (charm/item-list ["Apple" "Banana" "Cherry"]))

;; In update function
(let [[list cmd] (charm/list-update my-list msg)]
  ;; Handle navigation
  )

;; In view function
(charm/list-view my-list)
;; > Apple
;;   Banana
;;   Cherry
```

## Creation Options

```clojure
(charm/item-list items & options)
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `items` | vector | required | Items to display |
| `:height` | int | `0` | Visible height (0 = show all) |
| `:width` | int | `0` | Width constraint (0 = unlimited) |
| `:cursor` | int | `0` | Initial selected index |
| `:title` | string | `nil` | Optional list title |
| `:show-title` | boolean | `true` | Show title if provided |
| `:cursor-prefix` | string | `"> "` | Prefix for selected item |
| `:item-prefix` | string | `"  "` | Prefix for unselected items |
| `:show-descriptions` | boolean | `false` | Show item descriptions |
| `:infinite-scroll` | boolean | `false` | Wrap around at ends |
| `:cursor-style` | style | cyan+bold | Style for selected item |
| `:item-style` | style | `nil` | Style for unselected items |
| `:title-style` | style | bold | Style for title |
| `:id` | any | random | Unique identifier |

## Item Formats

Items can be strings or maps:

```clojure
;; Simple strings
(charm/item-list ["Apple" "Banana" "Cherry"])

;; Maps with title and description
(charm/item-list [{:title "Apple" :description "A red fruit"}
                  {:title "Banana" :description "A yellow fruit"}]
                 :show-descriptions true)

;; Maps with custom keys
(charm/item-list [{:name "Alice" :role "Admin"}
                  {:name "Bob" :role "User"}])
;; Uses :name as title, :role as description
```

## Key Bindings

| Action | Keys |
|--------|------|
| Move up | `Up`, `k`, `Ctrl+P` |
| Move down | `Down`, `j`, `Ctrl+N` |
| Page up | `PgUp`, `Ctrl+U` |
| Page down | `PgDn`, `Ctrl+D` |
| Go to start | `Home`, `g` |
| Go to end | `End`, `G` |

## Functions

### list-update

```clojure
(charm/list-update list msg) ; => [list cmd]
```

Handle navigation messages.

### list-view

```clojure
(charm/list-view list) ; => "> Apple\n  Banana\n  Cherry"
```

Render the list as a string.

### list-selected-item

```clojure
(charm/list-selected-item list) ; => "Apple" or {:title "Apple" ...}
```

Get the currently selected item.

### list-selected-index

```clojure
(charm/list-selected-index list) ; => 0
```

Get the index of the selected item.

### list-set-items

```clojure
(charm/list-set-items list new-items)
```

Replace all items, adjusting cursor if needed.

### list-select

```clojure
(charm/list-select list 2)  ; Select item at index 2
```

### Navigation Functions

```clojure
(charm/list-cursor-up list)
(charm/list-cursor-down list)
(charm/list-page-up list)
(charm/list-page-down list)
(charm/list-go-to-start list)
(charm/list-go-to-end list)
```

## Full Example

```clojure
(ns my-app
  (:require [charm.core :as charm]))

(def items
  [{:title "New File" :description "Create a new file"}
   {:title "Open File" :description "Open an existing file"}
   {:title "Save" :description "Save current file"}
   {:title "Exit" :description "Quit the application"}])

(defn init []
  [{:menu (charm/item-list items
                           :height 10
                           :show-descriptions true
                           :cursor-style (charm/style :fg charm/yellow :bold true))}
   nil])

(defn update-fn [state msg]
  (cond
    (charm/key-match? msg "enter")
    (let [selected (charm/list-selected-item (:menu state))]
      (println "Selected:" (:title selected))
      (if (= "Exit" (:title selected))
        [state charm/quit-cmd]
        [state nil]))

    (charm/key-match? msg "q")
    [state charm/quit-cmd]

    :else
    (let [[menu cmd] (charm/list-update (:menu state) msg)]
      [(assoc state :menu menu) cmd])))

(defn view [state]
  (str "Select an option:\n\n"
       (charm/list-view (:menu state))
       "\n\nEnter to select, q to quit"))

(charm/run {:init init :update update-fn :view view})
```

## Scrolling

When `:height` is set, the list scrolls to keep the cursor visible:

```clojure
(charm/item-list (range 100)
                 :height 10)  ; Shows 10 items, scrolls with cursor
```

## Filtering

```clojure
;; Filter items by predicate
(charm/list-filter-items list #(str/includes? (:title %) "search"))

;; Find and select first match
(charm/list-select-first-match list #(= "target" (:title %)))
```
