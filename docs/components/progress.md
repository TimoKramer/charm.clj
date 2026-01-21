# Progress Bar

Progress indicator with 7 built-in bar styles.

## Quick Example

```clojure
(require '[charm.core :as charm])

(def my-bar (charm/progress-bar :width 40))

;; Update progress (0.0 to 1.0)
(def updated-bar (charm/progress-set-progress my-bar 0.5))

;; In view function
(charm/progress-view updated-bar)  ; => "████████████████████░░░░░░░░░░░░░░░░░░░░"
```

## Creation Options

```clojure
(charm/progress-bar & options)
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
(charm/progress-bar :bar-style {:full "▰"
                                :empty "▱"})

;; With brackets
(charm/progress-bar :bar-style {:full "="
                                :empty " "
                                :left "["
                                :right "]"})
```

## Functions

### progress-view

```clojure
(charm/progress-view bar) ; => "████████░░░░░░░░"
```

Render the progress bar as a string.

### progress-set-progress

```clojure
(charm/progress-set-progress bar 0.75) ; Set to 75%
```

Set progress as a float from 0.0 to 1.0.

### progress-set-progress-int

```clojure
(charm/progress-set-progress-int bar 75) ; Set to 75%
```

Set progress as an integer from 0 to 100.

### progress-increment / progress-decrement

```clojure
(charm/progress-increment bar)      ; +1%
(charm/progress-increment bar 0.05) ; +5%
(charm/progress-decrement bar 0.1)  ; -10%
```

### progress-percent

```clojure
(charm/progress-percent bar)     ; => 0.75 (float)
(charm/progress-percent-int bar) ; => 75 (int)
```

### progress-complete?

```clojure
(charm/progress-complete? bar) ; => true if >= 100%
```

### progress-reset

```clojure
(charm/progress-reset bar) ; Reset to 0%
```

## Full Example

```clojure
(ns my-app
  (:require [charm.core :as charm]))

(defn tick-cmd []
  {:type :cmd
   :fn (fn []
         (Thread/sleep 100)
         {:type :tick})})

(defn init []
  [{:bar (charm/progress-bar :width 50
                             :bar-style :default
                             :show-percent true)
    :running false}
   nil])

(defn update-fn [state msg]
  (cond
    (charm/key-match? msg "q")
    [state charm/quit-cmd]

    (charm/key-match? msg " ")
    [(assoc state :running (not (:running state)))
     (when-not (:running state) (tick-cmd))]

    (= :tick (:type msg))
    (let [bar (charm/progress-increment (:bar state) 0.02)]
      (if (charm/progress-complete? bar)
        [(assoc state :bar bar :running false) nil]
        [(assoc state :bar bar)
         (when (:running state) (tick-cmd))]))

    :else
    [state nil]))

(defn view [state]
  (str "Download Progress\n\n"
       (charm/progress-view (:bar state)) "\n\n"
       (if (charm/progress-complete? (:bar state))
         "Complete!"
         (if (:running state)
           "Downloading... (Space to pause)"
           "Press Space to start, Q to quit"))))

(charm/run {:init init :update update-fn :view view})
```

## Styled Progress Bar

```clojure
(charm/progress-bar :width 40
                    :bar-style :default
                    :show-percent true
                    :full-style (charm/style :fg charm/green)
                    :empty-style (charm/style :fg 240)
                    :percent-style (charm/style :fg charm/yellow :bold true))
```

## Multiple Progress Bars

```clojure
(defn view [state]
  (str "File 1: " (charm/progress-view (:bar1 state)) "\n"
       "File 2: " (charm/progress-view (:bar2 state)) "\n"
       "File 3: " (charm/progress-view (:bar3 state))))
```
