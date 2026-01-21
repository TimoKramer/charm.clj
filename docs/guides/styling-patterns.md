# Styling Patterns

Common styling recipes and patterns for charm.clj applications.

## Defining Style Constants

Create reusable styles at the top of your namespace:

```clojure
(ns my-app.core
  (:require [charm.core :as charm]))

;; Text styles
(def title-style
  (charm/style :fg charm/cyan :bold true))

(def subtitle-style
  (charm/style :fg charm/white :italic true))

(def error-style
  (charm/style :fg charm/red :bold true))

(def success-style
  (charm/style :fg charm/green))

(def muted-style
  (charm/style :fg 240))  ; Gray

;; Box styles
(def panel-style
  (charm/style :border charm/rounded-border
               :padding [1 2]))

(def highlight-box
  (charm/style :border charm/double-border
               :border-fg charm/yellow
               :padding [0 1]))
```

## Color Palettes

### Minimal (ANSI 16)

Works in any terminal:

```clojure
(def primary charm/cyan)
(def secondary charm/white)
(def accent charm/yellow)
(def danger charm/red)
(def success charm/green)
(def muted (charm/ansi 8))  ; bright-black/gray
```

### Extended (ANSI 256)

Grayscale and more colors:

```clojure
;; Grayscale
(def gray-dark (charm/ansi256 236))
(def gray (charm/ansi256 240))
(def gray-light (charm/ansi256 245))
(def gray-lighter (charm/ansi256 250))

;; Extended colors
(def orange (charm/ansi256 208))
(def pink (charm/ansi256 205))
(def teal (charm/ansi256 43))
```

### True Color (24-bit)

Full color control:

```clojure
(def brand-primary (charm/hex "#6366f1"))  ; Indigo
(def brand-secondary (charm/hex "#8b5cf6")) ; Purple
(def brand-accent (charm/hex "#f59e0b"))   ; Amber

(def bg-dark (charm/rgb 24 24 27))
(def bg-light (charm/rgb 250 250 250))
```

## Common UI Patterns

### Headers

```clojure
(defn header [title]
  (charm/render
    (charm/style :fg charm/cyan
                 :bold true
                 :padding [0 0 1 0])  ; bottom padding
    title))

(defn header-with-border [title]
  (charm/render
    (charm/style :fg charm/white
                 :bg charm/blue
                 :bold true
                 :width 60
                 :align :center
                 :padding [0 1])
    title))
```

### Status Indicators

```clojure
(defn status-badge [status]
  (let [[text style] (case status
                       :success ["✓ Success" (charm/style :fg charm/green)]
                       :warning ["⚠ Warning" (charm/style :fg charm/yellow)]
                       :error ["✗ Error" (charm/style :fg charm/red)]
                       :info ["ℹ Info" (charm/style :fg charm/cyan)]
                       ["?" (charm/style :fg 240)])]
    (charm/render style text)))

(defn loading-indicator [spinner text]
  (str (charm/spinner-view spinner) " " text))
```

### Lists with Selection

```clojure
(defn render-list-item [item selected?]
  (let [prefix (if selected? "▸ " "  ")
        style (if selected?
                (charm/style :fg charm/cyan :bold true)
                (charm/style :fg charm/white))]
    (str prefix (charm/render style (:title item)))))

(defn render-list [items selected-idx]
  (str/join "\n"
    (map-indexed
      (fn [i item]
        (render-list-item item (= i selected-idx)))
      items)))
```

### Input Fields

```clojure
(defn labeled-input [label input focused?]
  (let [label-style (if focused?
                      (charm/style :fg charm/cyan :bold true)
                      (charm/style :fg 240))]
    (str (charm/render label-style (str label ": "))
         (charm/text-input-view input))))

(defn form-field [label input error]
  (str (labeled-input label input true)
       (when error
         (str "\n" (charm/render (charm/style :fg charm/red) error)))))
```

### Progress Display

```clojure
(defn task-progress [label progress]
  (let [bar (charm/progress-bar :width 30 :percent progress :show-percent true)]
    (str (charm/render (charm/style :fg charm/white) label) "\n"
         (charm/progress-view bar))))

(defn multi-progress [tasks]
  (str/join "\n\n"
    (for [{:keys [name progress status]} tasks]
      (str (charm/render
             (charm/style :fg (if (= status :done) charm/green charm/white))
             name)
           "\n"
           (charm/progress-view
             (charm/progress-bar :width 40 :percent progress))))))
```

## Layout Patterns

### Two-Column Layout

```clojure
(defn two-columns [left right]
  (charm/join-horizontal :top
    (charm/render (charm/style :width 30) left)
    "  "
    (charm/render (charm/style :width 40) right)))
```

### Sidebar Layout

```clojure
(defn sidebar-layout [sidebar main]
  (charm/join-horizontal :top
    (charm/render
      (charm/style :border charm/normal-border
                   :width 25
                   :height 20)
      sidebar)
    " "
    (charm/render
      (charm/style :border charm/rounded-border
                   :width 50
                   :height 20
                   :padding [0 1])
      main)))
```

### Card Layout

```clojure
(defn card [title content]
  (charm/render
    (charm/style :border charm/rounded-border
                 :padding [0 1])
    (str (charm/render (charm/style :bold true) title) "\n"
         content)))

(defn card-grid [cards]
  (charm/join-vertical :left
    (for [row (partition-all 2 cards)]
      (apply charm/join-horizontal :top
        (interpose "  " row)))))
```

### Centered Content

```clojure
(defn centered-box [content width]
  (charm/render
    (charm/style :width width
                 :align :center
                 :border charm/rounded-border
                 :padding [1 2])
    content))

(defn modal [title content]
  (charm/render
    (charm/style :width 50
                 :align :center
                 :border charm/double-border
                 :border-fg charm/cyan
                 :padding [1 2])
    (str (charm/render (charm/style :bold true :align :center) title)
         "\n\n"
         content)))
```

## Help and Hints

### Inline Help

```clojure
(defn help-line [bindings]
  (let [help (charm/help bindings :separator "  ")]
    (charm/help-view help)))

;; Usage
(help-line [["j/k" "move"] ["Enter" "select"] ["q" "quit"]])
```

### Hint Text

```clojure
(defn hint [text]
  (charm/render (charm/style :fg 240 :italic true) text))

(defn keyboard-hint [key description]
  (str (charm/render (charm/style :fg charm/cyan :bold true) key)
       " "
       (charm/render (charm/style :fg 240) description)))
```

## Conditional Styling

```clojure
(defn value-color [value]
  (cond
    (pos? value) charm/green
    (neg? value) charm/red
    :else charm/white))

(defn render-value [value]
  (charm/render
    (charm/style :fg (value-color value) :bold (not (zero? value)))
    (str value)))

(defn render-status [status]
  (charm/render
    (charm/style :fg (case status
                       :active charm/green
                       :pending charm/yellow
                       :error charm/red
                       charm/white))
    (name status)))
```

## Responsive Width

Adapt to terminal width:

```clojure
(defn responsive-box [content {:keys [width]}]
  (let [box-width (min 60 (- width 4))]
    (charm/render
      (charm/style :width box-width
                   :border charm/rounded-border)
      content)))

(defn view [state]
  (let [width (get state :terminal-width 80)]
    (responsive-box "Content" {:width width})))

;; Handle window resize in update
(defn update-fn [state msg]
  (if (charm/window-size? msg)
    [(assoc state :terminal-width (:width msg)) nil]
    [state nil]))
```

## Complete Theme Example

```clojure
(ns my-app.theme
  (:require [charm.core :as charm]))

;; Colors
(def colors
  {:primary charm/cyan
   :secondary charm/white
   :accent charm/yellow
   :success charm/green
   :warning charm/yellow
   :danger charm/red
   :muted (charm/ansi256 240)
   :bg (charm/ansi256 235)})

;; Typography
(def typography
  {:title (charm/style :fg (:primary colors) :bold true)
   :subtitle (charm/style :fg (:secondary colors) :italic true)
   :body (charm/style :fg (:secondary colors))
   :caption (charm/style :fg (:muted colors))
   :code (charm/style :fg (:accent colors))})

;; Components
(def components
  {:panel (charm/style :border charm/rounded-border :padding [0 1])
   :card (charm/style :border charm/normal-border :padding [1 2])
   :modal (charm/style :border charm/double-border
                       :border-fg (:primary colors)
                       :padding [1 2])
   :button (charm/style :bold true :padding [0 1])})

;; Usage
(defn render-title [text]
  (charm/render (:title typography) text))

(defn render-panel [content]
  (charm/render (:panel components) content))
```
