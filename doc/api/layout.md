# Layout API

The layout API provides functions for combining and aligning text blocks.

## Joining Text Blocks

### join-horizontal

```clojure
(style/join-horizontal position & texts)
```

Join multiple text blocks side by side.

**Position** specifies vertical alignment: `:top`, `:center`, or `:bottom`.

```clojure
(style/join-horizontal :top "Left" "Right")
; LeftRight

(style/join-horizontal :top
  "Line 1\nLine 2\nLine 3"
  "  |  "
  "A\nB")
; Line 1  |  A
; Line 2  |  B
; Line 3  |
```

**With styled boxes:**

```clojure
(def box1
  (style/render
    (style/style :border border/rounded :padding [0 1])
    "Box 1"))

(def box2
  (style/render
    (style/style :border border/rounded :padding [0 1])
    "Box 2"))

(style/join-horizontal :top box1 "  " box2)
; ╭───────╮  ╭───────╮
; │ Box 1 │  │ Box 2 │
; ╰───────╯  ╰───────╯
```

**Vertical alignment:**

```clojure
;; Top aligned (default)
(style/join-horizontal :top
  "Short"
  "Tall\ntext\nhere")
; ShortTall
;      text
;      here

;; Center aligned
(style/join-horizontal :center
  "Short"
  "Tall\ntext\nhere")
;      Tall
; Shorttext
;      here

;; Bottom aligned
(style/join-horizontal :bottom
  "Short"
  "Tall\ntext\nhere")
;      Tall
;      text
; Shorthere
```

### join-vertical

```clojure
(style/join-vertical position & texts)
```

Join multiple text blocks vertically (stacked).

**Position** specifies horizontal alignment: `:left`, `:center`, or `:right`.

```clojure
(style/join-vertical :left "Top" "Bottom")
; Top
; Bottom

(style/join-vertical :center
  "Short"
  "Longer text"
  "Medium")
;   Short
; Longer text
;   Medium

(style/join-vertical :right
  "Short"
  "Longer text"
  "Medium")
;       Short
; Longer text
;      Medium
```

**With styled boxes:**

```clojure
(def header
  (style/render
    (style/style :border border/rounded :width 30 :align :center)
    "Header"))

(def content
  (style/render
    (style/style :border border/normal :width 30)
    "Content goes here"))

(style/join-vertical :left header content)
; ╭──────────────────────────────╮
; │           Header             │
; ╰──────────────────────────────╯
; ┌──────────────────────────────┐
; │Content goes here             │
; └──────────────────────────────┘
```

## Building Layouts

### Two-Column Layout

```clojure
(defn two-column [left-content right-content]
  (style/join-horizontal :top
    (style/render
      (style/style :width 30 :border border/normal)
      left-content)
    "  "
    (style/render
      (style/style :width 30 :border border/normal)
      right-content)))
```

### Header/Content/Footer Layout

```clojure
(defn page-layout [header content footer]
  (style/join-vertical :left
    (style/render
      (style/style :width 60 :align :center :border border/rounded)
      header)
    (style/render
      (style/style :width 60 :border border/normal :padding [1 2])
      content)
    (style/render
      (style/style :width 60 :align :center :fg 240)
      footer)))
```

### Sidebar Layout

```clojure
(defn sidebar-layout [sidebar main-content]
  (style/join-horizontal :top
    (style/render
      (style/style :width 20 :border border/normal)
      sidebar)
    (style/render
      (style/style :width 50 :border border/normal :padding [0 1])
      main-content)))
```

## Complete Example

```clojure
(ns my-app
  (:require
   [charm.style.border :as border]
   [charm.style.core :as style]))

(def title-style
  (style/style :fg style/cyan :bold true :align :center))

(def box-style
  (style/style :border border/rounded :padding [0 1]))

(def dim-style
  (style/style :fg 240))

(defn render-sidebar [items selected]
  (style/render
    (style/style :border border/normal :width 20)
    (clojure.string/join "\n"
      (map-indexed
        (fn [i item]
          (if (= i selected)
            (style/render (style/style :fg style/cyan :bold true)
                         (str "> " item))
            (str "  " item)))
        items))))

(defn render-content [text]
  (style/render
    (style/style :border border/rounded
                 :padding [1 2]
                 :width 40)
    text))

(defn render-help []
  (style/render dim-style "j/k: navigate  q: quit"))

(defn view [state]
  (let [sidebar (render-sidebar (:items state) (:selected state))
        content (render-content (nth (:items state) (:selected state)))
        help (render-help)]
    (style/join-vertical :left
      (style/render title-style "My Application")
      ""
      (style/join-horizontal :top sidebar "  " content)
      ""
      help)))
```

## Tips

### Spacing Between Elements

Use empty strings for spacing:

```clojure
;; Horizontal spacing
(style/join-horizontal :top box1 "   " box2)  ; 3 spaces

;; Vertical spacing
(style/join-vertical :left header "" "" content)  ; 2 blank lines
```

### Consistent Widths

Set explicit widths for alignment:

```clojure
(style/join-vertical :left
  (style/render (style/style :width 40) "Row 1")
  (style/render (style/style :width 40) "Row 2")
  (style/render (style/style :width 40) "Row 3"))
```

### Nesting Layouts

Layouts can be nested:

```clojure
(style/join-vertical :center
  header
  (style/join-horizontal :top
    left-panel
    (style/join-vertical :left
      top-right
      bottom-right))
  footer)
```
