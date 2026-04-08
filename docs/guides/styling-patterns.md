# Styling Patterns

Common styling recipes and patterns for charm.clj applications.

## Defining Style Constants

Create reusable styles at the top of your namespace:

```clojure
(ns my-app.core
  (:require
   [charm.style.border :as border]
   [charm.style.core :as style]))

;; Text styles
(def title-style
  (style/style :fg style/cyan :bold true))

(def subtitle-style
  (style/style :fg style/white :italic true))

(def error-style
  (style/style :fg style/red :bold true))

(def success-style
  (style/style :fg style/green))

(def muted-style
  (style/style :fg 240))  ; Gray

;; Box styles
(def panel-style
  (style/style :border border/rounded
               :padding [1 2]))

(def highlight-box
  (style/style :border border/double-border
               :border-fg style/yellow
               :padding [0 1]))
```

## Color Palettes

### Minimal (ANSI 16)

Works in any terminal:

```clojure
(def primary style/cyan)
(def secondary style/white)
(def accent style/yellow)
(def danger style/red)
(def success style/green)
(def muted (style/ansi 8))  ; bright-black/gray
```

### Extended (ANSI 256)

Grayscale and more colors:

```clojure
;; Grayscale
(def gray-dark (style/ansi256 236))
(def gray (style/ansi256 240))
(def gray-light (style/ansi256 245))
(def gray-lighter (style/ansi256 250))

;; Extended colors
(def orange (style/ansi256 208))
(def pink (style/ansi256 205))
(def teal (style/ansi256 43))
```

### True Color (24-bit)

Full color control:

```clojure
(def brand-primary (style/hex "#6366f1"))  ; Indigo
(def brand-secondary (style/hex "#8b5cf6")) ; Purple
(def brand-accent (style/hex "#f59e0b"))   ; Amber

(def bg-dark (style/rgb 24 24 27))
(def bg-light (style/rgb 250 250 250))
```

## Common UI Patterns

### Headers

```clojure
(defn header [title]
  (style/render
    (style/style :fg style/cyan
                 :bold true
                 :padding [0 0 1 0])  ; bottom padding
    title))

(defn header-with-border [title]
  (style/render
    (style/style :fg style/white
                 :bg style/blue
                 :bold true
                 :width 60
                 :align :center
                 :padding [0 1])
    title))
```

### Status Indicators

```clojure
(defn status-badge [status]
  (let [[text s] (case status
                   :success ["✓ Success" (style/style :fg style/green)]
                   :warning ["⚠ Warning" (style/style :fg style/yellow)]
                   :error ["✗ Error" (style/style :fg style/red)]
                   :info ["ℹ Info" (style/style :fg style/cyan)]
                   ["?" (style/style :fg 240)])]
    (style/render s text)))

(defn loading-indicator [spinner text]
  (str (spinner/spinner-view spinner) " " text))
```

### Lists with Selection

```clojure
(defn render-list-item [item selected?]
  (let [prefix (if selected? "▸ " "  ")
        s (if selected?
            (style/style :fg style/cyan :bold true)
            (style/style :fg style/white))]
    (str prefix (style/render s (:title item)))))

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
                      (style/style :fg style/cyan :bold true)
                      (style/style :fg 240))]
    (str (style/render label-style (str label ": "))
         (text-input/text-input-view input))))

(defn form-field [label input error]
  (str (labeled-input label input true)
       (when error
         (str "\n" (style/render (style/style :fg style/red) error)))))
```

### Progress Display

```clojure
(defn task-progress [label p]
  (let [bar (progress/progress-bar :width 30 :percent p :show-percent true)]
    (str (style/render (style/style :fg style/white) label) "\n"
         (progress/progress-view bar))))

(defn multi-progress [tasks]
  (str/join "\n\n"
    (for [{:keys [name progress status]} tasks]
      (str (style/render
             (style/style :fg (if (= status :done) style/green style/white))
             name)
           "\n"
           (progress/progress-view
             (progress/progress-bar :width 40 :percent progress))))))
```

## Layout Patterns

### Two-Column Layout

```clojure
(defn two-columns [left right]
  (style/join-horizontal :top
    (style/render (style/style :width 30) left)
    "  "
    (style/render (style/style :width 40) right)))
```

### Sidebar Layout

```clojure
(defn sidebar-layout [sidebar main]
  (style/join-horizontal :top
    (style/render
      (style/style :border border/normal
                   :width 25
                   :height 20)
      sidebar)
    " "
    (style/render
      (style/style :border border/rounded
                   :width 50
                   :height 20
                   :padding [0 1])
      main)))
```

### Card Layout

```clojure
(defn card [title content]
  (style/render
    (style/style :border border/rounded
                 :padding [0 1])
    (str (style/render (style/style :bold true) title) "\n"
         content)))

(defn card-grid [cards]
  (style/join-vertical :left
    (for [row (partition-all 2 cards)]
      (apply style/join-horizontal :top
        (interpose "  " row)))))
```

### Centered Content

```clojure
(defn centered-box [content width]
  (style/render
    (style/style :width width
                 :align :center
                 :border border/rounded
                 :padding [1 2])
    content))

(defn modal [title content]
  (style/render
    (style/style :width 50
                 :align :center
                 :border border/double-border
                 :border-fg style/cyan
                 :padding [1 2])
    (str (style/render (style/style :bold true :align :center) title)
         "\n\n"
         content)))
```

## Help and Hints

### Inline Help

```clojure
(defn help-line [bindings]
  (let [h (help/help bindings :separator "  ")]
    (help/short-help-view h)))

;; Usage
(help-line [["j/k" "move"] ["Enter" "select"] ["q" "quit"]])
```

### Hint Text

```clojure
(defn hint [text]
  (style/render (style/style :fg 240 :italic true) text))

(defn keyboard-hint [key description]
  (str (style/render (style/style :fg style/cyan :bold true) key)
       " "
       (style/render (style/style :fg 240) description)))
```

## Conditional Styling

```clojure
(defn value-color [value]
  (cond
    (pos? value) style/green
    (neg? value) style/red
    :else style/white))

(defn render-value [value]
  (style/render
    (style/style :fg (value-color value) :bold (not (zero? value)))
    (str value)))

(defn render-status [status]
  (style/render
    (style/style :fg (case status
                       :active style/green
                       :pending style/yellow
                       :error style/red
                       style/white))
    (name status)))
```

## Responsive Width

Adapt to terminal width:

```clojure
(defn responsive-box [content {:keys [width]}]
  (let [box-width (min 60 (- width 4))]
    (style/render
      (style/style :width box-width
                   :border border/rounded)
      content)))

(defn view [state]
  (let [width (get state :terminal-width 80)]
    (responsive-box "Content" {:width width})))

;; Handle window resize in update
(defn update-fn [state msg]
  (if (msg/window-size? msg)
    [(assoc state :terminal-width (:width msg)) nil]
    [state nil]))
```

## Complete Theme Example

```clojure
(ns my-app.theme
  (:require
   [charm.style.border :as border]
   [charm.style.core :as style]))

;; Colors
(def colors
  {:primary style/cyan
   :secondary style/white
   :accent style/yellow
   :success style/green
   :warning style/yellow
   :danger style/red
   :muted (style/ansi256 240)
   :bg (style/ansi256 235)})

;; Typography
(def typography
  {:title (style/style :fg (:primary colors) :bold true)
   :subtitle (style/style :fg (:secondary colors) :italic true)
   :body (style/style :fg (:secondary colors))
   :caption (style/style :fg (:muted colors))
   :code (style/style :fg (:accent colors))})

;; Components
(def components
  {:panel (style/style :border border/rounded :padding [0 1])
   :card (style/style :border border/normal :padding [1 2])
   :modal (style/style :border border/double-border
                       :border-fg (:primary colors)
                       :padding [1 2])
   :button (style/style :bold true :padding [0 1])})

;; Usage
(defn render-title [text]
  (style/render (:title typography) text))

(defn render-panel [content]
  (style/render (:panel components) content))
```
