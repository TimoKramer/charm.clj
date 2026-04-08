# Progress Bar

Progress indicator with 7 built-in bar styles.

## Quick Example

```clojure
(require '[charm.components.progress :as progress])

(def my-bar (progress/progress-bar :width 40))

;; Update progress (0.0 to 1.0)
(def updated-bar (progress/set-progress my-bar 0.5))

;; In view function
(progress/progress-view updated-bar)  ; => "████████████████████░░░░░░░░░░░░░░░░░░░░"
```

## Creation Options

```clojure
(progress/progress-bar & options)
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:width` | int | `40` | Total width in characters |
| `:percent` | float | `0.0` | Initial progress (0.0-1.0) |
| `:bar-style` | keyword/map | `:default` | Bar style name or custom map |
| `:show-percent` | boolean | `false` | Show percentage text |
| `:full-style` | style | cyan | Style for filled portion |
| `:empty-style` | style | `nil` | Style for empty portion |
| `:percent-style` | style | `nil` | Style for percentage text |
| `:id` | any | random | Unique identifier |

## Bar Styles

| Style | Full | Empty | Preview |
|-------|------|-------|---------|
| `:default` | █ | ░ | `████░░░░` |
| `:ascii` | # | - | `####----` |
| `:thin` | ━ | ─ | `━━━━────` |
| `:thick` | █ | ▒ | `████▒▒▒▒` |
| `:blocks` | ▓ | ░ | `▓▓▓▓░░░░` |
| `:arrows` | > | (space) | `>>>>    ` |
| `:dots` | ● | ○ | `●●●●○○○○` |
| `:brackets` | = | (space) | `[====    ]` |

## Custom Bar Style

```clojure
(progress/progress-bar :bar-style {:full "▰"
                                   :empty "▱"})

;; With brackets
(progress/progress-bar :bar-style {:full "="
                                   :empty " "
                                   :left "["
                                   :right "]"})
```

## Functions

### progress-view

```clojure
(progress/progress-view bar) ; => "████████░░░░░░░░"
```

Render the progress bar as a string.

### set-progress

```clojure
(progress/set-progress bar 0.75) ; Set to 75%
```

Set progress as a float from 0.0 to 1.0.

### set-progress-int

```clojure
(progress/set-progress-int bar 75) ; Set to 75%
```

Set progress as an integer from 0 to 100.

### increment / decrement

```clojure
(progress/increment bar)      ; +1%
(progress/increment bar 0.05) ; +5%
(progress/decrement bar 0.1)  ; -10%
```

### percent

```clojure
(progress/percent bar)     ; => 0.75 (float)
(progress/percent-int bar) ; => 75 (int)
```

### complete?

```clojure
(progress/complete? bar) ; => true if >= 100%
```

### reset

```clojure
(progress/reset bar) ; Reset to 0%
```

## Full Example

```clojure
(ns my-app
  (:require
   [charm.components.progress :as progress]
   [charm.message :as msg]
   [charm.program :as program]))

(defn tick-cmd []
  {:type :cmd
   :fn (fn []
         (Thread/sleep 100)
         {:type :tick})})

(defn init []
  [{:bar (progress/progress-bar :width 50
                                :bar-style :default
                                :show-percent true)
    :running false}
   nil])

(defn update-fn [state msg]
  (cond
    (msg/key-match? msg "q")
    [state program/quit-cmd]

    (msg/key-match? msg " ")
    [(assoc state :running (not (:running state)))
     (when-not (:running state) (tick-cmd))]

    (= :tick (:type msg))
    (let [bar (progress/increment (:bar state) 0.02)]
      (if (progress/complete? bar)
        [(assoc state :bar bar :running false) nil]
        [(assoc state :bar bar)
         (when (:running state) (tick-cmd))]))

    :else
    [state nil]))

(defn view [state]
  (str "Download Progress\n\n"
       (progress/progress-view (:bar state)) "\n\n"
       (if (progress/complete? (:bar state))
         "Complete!"
         (if (:running state)
           "Downloading... (Space to pause)"
           "Press Space to start, Q to quit"))))

(program/run {:init init :update update-fn :view view})
```

## Styled Progress Bar

```clojure
(progress/progress-bar :width 40
                       :bar-style :default
                       :show-percent true
                       :full-style (style/style :fg style/green)
                       :empty-style (style/style :fg 240)
                       :percent-style (style/style :fg style/yellow :bold true))
```

## Multiple Progress Bars

```clojure
(defn view [state]
  (str "File 1: " (progress/progress-view (:bar1 state)) "\n"
       "File 2: " (progress/progress-view (:bar2 state)) "\n"
       "File 3: " (progress/progress-view (:bar3 state))))
```
