# Paginator

Page navigation indicators with dots or numeric display.

## Quick Example

```clojure
(require '[charm.core :as charm])

(def pager (charm/paginator :total-pages 5))

;; In update function
(let [[pager cmd] (charm/paginator-update pager msg)]
  ;; Handle navigation
  )

;; In view function
(charm/paginator-view pager)  ; => "•○○○○"
```

## Creation Options

```clojure
(charm/paginator & options)
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
(charm/paginator :total-pages 5 :type :dots)
;; Page 0: •○○○○
;; Page 2: ○○•○○
```

### Arabic

```clojure
(charm/paginator :total-pages 5 :type :arabic)
;; Page 0: 1/5
;; Page 2: 3/5

;; Custom format
(charm/paginator :total-pages 5
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
(charm/paginator-update pager msg) ; => [pager cmd]
```

Handle navigation messages.

### paginator-view

```clojure
(charm/paginator-view pager) ; => "•○○○○"
```

Render the paginator as a string.

### Navigation

```clojure
(charm/paginator-page pager)        ; Get current page (0-indexed)
(charm/paginator-total-pages pager) ; Get total pages
(charm/paginator-set-page pager 2)  ; Set current page

(charm/paginator-next-page pager)   ; Go to next page
(charm/paginator-prev-page pager)   ; Go to previous page
(charm/paginator-go-to-first pager) ; Go to first page
(charm/paginator-go-to-last pager)  ; Go to last page
```

### Bounds Checking

```clojure
(charm/paginator-on-first-page? pager) ; => true/false
(charm/paginator-on-last-page? pager)  ; => true/false
```

### Slice Bounds

For paginating data:

```clojure
(def pager (charm/paginator :total-pages 10 :per-page 5))

;; Get [start end] for slicing
(charm/paginator-slice-bounds pager 47) ; => [0 5] for page 0

;; Calculate from item count
(charm/paginator-set-total-items pager 47) ; Sets total-pages to 10
```

## Full Example

```clojure
(ns my-app
  (:require [charm.core :as charm]))

(def all-items (vec (range 1 51)))  ; 50 items
(def per-page 10)

(defn init []
  [{:pager (charm/paginator :per-page per-page
                            :total-pages (int (Math/ceil (/ (count all-items) per-page))))
    :items all-items}
   nil])

(defn get-page-items [state]
  (let [[start end] (charm/paginator-slice-bounds (:pager state) (count all-items))]
    (subvec all-items start end)))

(defn update-fn [state msg]
  (cond
    (charm/key-match? msg "q")
    [state charm/quit-cmd]

    :else
    (let [[pager cmd] (charm/paginator-update (:pager state) msg)]
      [(assoc state :pager pager) cmd])))

(defn view [state]
  (let [items (get-page-items state)]
    (str "Items: " (clojure.string/join ", " items) "\n\n"
         (charm/paginator-view (:pager state)) "\n\n"
         "Left/Right to navigate, q to quit")))

(charm/run {:init init :update update-fn :view view})
```

## Custom Dots

```clojure
(charm/paginator :total-pages 5
                 :active-dot "●"
                 :inactive-dot "○"
                 :active-style (charm/style :fg charm/cyan))
```
