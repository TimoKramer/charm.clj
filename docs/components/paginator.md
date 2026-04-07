# Paginator

Page navigation indicators with dots or numeric display.

## Quick Example

```clojure
(require '[charm.components.paginator :as paginator])

(def pager (paginator/paginator :total-pages 5))

;; In update function
(let [[pager cmd] (paginator/paginator-update pager msg)]
  ;; Handle navigation
  )

;; In view function
(paginator/paginator-view pager)  ; => "•○○○○"
```

## Creation Options

```clojure
(paginator/paginator & options)
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:total-pages` | int | `1` | Total number of pages |
| `:per-page` | int | `1` | Items per page (for calculations) |
| `:page` | int | `0` | Current page (0-indexed) |
| `:type` | keyword | `:dots` | `:dots` or `:arabic` |
| `:active-dot` | string | `"•"` | Character for current page |
| `:inactive-dot` | string | `"○"` | Character for other pages |
| `:arabic-format` | string | `"%d/%d"` | Format for arabic type |
| `:active-style` | style | bold | Style for active indicator |
| `:inactive-style` | style | `nil` | Style for inactive indicators |
| `:id` | any | random | Unique identifier |

## Display Types

### Dots (default)

```clojure
(paginator/paginator :total-pages 5 :type :dots)
;; Page 0: •○○○○
;; Page 2: ○○•○○
```

### Arabic

```clojure
(paginator/paginator :total-pages 5 :type :arabic)
;; Page 0: 1/5
;; Page 2: 3/5

;; Custom format
(paginator/paginator :total-pages 5
                     :type :arabic
                     :arabic-format "Page %d of %d")
;; => "Page 1 of 5"
```

## Key Bindings

| Action | Keys |
|--------|------|
| Next page | `Right`, `l`, `PgDn` |
| Previous page | `Left`, `h`, `PgUp` |

## Functions

### paginator-update

```clojure
(paginator/paginator-update pager msg) ; => [pager cmd]
```

Handle navigation messages.

### paginator-view

```clojure
(paginator/paginator-view pager) ; => "•○○○○"
```

Render the paginator as a string.

### Navigation

```clojure
(paginator/page pager)        ; Get current page (0-indexed)
(paginator/total-pages pager) ; Get total pages
(paginator/set-page pager 2)  ; Set current page

(paginator/next-page pager)   ; Go to next page
(paginator/prev-page pager)   ; Go to previous page
```

## Full Example

```clojure
(ns my-app
  (:require
   [charm.components.paginator :as paginator]
   [charm.message :as msg]
   [charm.program :as program]))

(def all-items (vec (range 1 51)))  ; 50 items
(def per-page 10)

(defn init []
  [{:pager (paginator/paginator :per-page per-page
                                :total-pages (int (Math/ceil (/ (count all-items) per-page))))
    :items all-items}
   nil])

(defn update-fn [state msg]
  (cond
    (msg/key-match? msg "q")
    [state program/quit-cmd]

    :else
    (let [[pager cmd] (paginator/paginator-update (:pager state) msg)]
      [(assoc state :pager pager) cmd])))

(defn view [state]
  (str "Items: " (clojure.string/join ", " (take per-page all-items)) "\n\n"
       (paginator/paginator-view (:pager state)) "\n\n"
       "Left/Right to navigate, q to quit"))

(program/run {:init init :update update-fn :view view})
```

## Custom Dots

```clojure
(paginator/paginator :total-pages 5
                     :active-dot "●"
                     :inactive-dot "○"
                     :active-style (style/style :fg style/cyan))
```
