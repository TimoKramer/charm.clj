# Spinner

Animated loading indicators with 15 built-in animation styles.

## Quick Example

```clojure
(require '[charm.core :as charm])

(def my-spinner (charm/spinner :dots))

;; Initialize to start animation
(let [[spinner cmd] (charm/spinner-init my-spinner)]
  ;; spinner is ready, cmd starts the tick loop
  )

;; In update function
(let [[spinner cmd] (charm/spinner-update spinner msg)]
  ;; Handle spinner tick messages
  )

;; In view function
(charm/spinner-view spinner)  ; => "⠋" (animates)
```

## Creation Options

```clojure
(charm/spinner type & options)
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `type` | keyword/map | `:dots` | Spinner type or custom `{:frames [...] :interval ms}` |
| `:style` | style | `nil` | Style to apply to spinner frames |
| `:id` | any | random | Unique identifier for tick messages |

## Spinner Types

| Type | Frames | Interval | Preview |
|------|--------|----------|---------|
| `:line` | `\| / - \` | 100ms | Classic line spinner |
| `:dots` | Braille dots | 80ms | Smooth dot animation |
| `:dot` | Braille patterns | 100ms | Rotating dot |
| `:jump` | Braille jump | 100ms | Jumping dot |
| `:pulse` | `█ ▓ ▒ ░` | 125ms | Pulsing block |
| `:points` | `∙∙∙ ●∙∙ ∙●∙ ∙∙●` | 140ms | Moving point |
| `:globe` | 🌍 🌎 🌏 | 250ms | Rotating globe |
| `:moon` | Moon phases | 125ms | Moon cycle |
| `:monkey` | 🙈 🙉 🙊 | 300ms | Three monkeys |
| `:meter` | `▱▱▱` to `▰▰▰` | 140ms | Loading meter |
| `:hamburger` | ☱ ☲ ☴ | 300ms | Trigram animation |
| `:ellipsis` | `. .. ...` | 300ms | Growing dots |
| `:arrows` | ← ↖ ↑ ↗ → ↘ ↓ ↙ | 100ms | Rotating arrow |
| `:bouncing-bar` | `[=   ]` | 100ms | Bouncing bar |
| `:clock` | Clock faces | 100ms | Clock animation |

## Custom Spinner

```clojure
(charm/spinner {:frames ["◐" "◓" "◑" "◒"]
                :interval 150})
```

## Functions

### spinner-init

```clojure
(charm/spinner-init spinner) ; => [spinner cmd]
```

Initialize the spinner and start the tick loop. Returns `[spinner cmd]` where `cmd` triggers the first tick.

### spinner-update

```clojure
(charm/spinner-update spinner msg) ; => [spinner cmd]
```

Handle tick messages. Returns `[spinner cmd]` to continue animation, or `[spinner nil]` if message not handled.

### spinner-view

```clojure
(charm/spinner-view spinner) ; => "⠋"
```

Render current frame as a string.

### spinning?

```clojure
(charm/spinning? spinner msg) ; => boolean
```

Check if a message is a tick for this spinner.

## Full Example

```clojure
(ns my-app
  (:require [charm.core :as charm]))

(defn init []
  (let [[spinner cmd] (charm/spinner-init (charm/spinner :dots))]
    [{:spinner spinner
      :loading true}
     cmd]))

(defn update-fn [state msg]
  (cond
    (charm/key-match? msg "q")
    [state charm/quit-cmd]

    :else
    (let [[spinner cmd] (charm/spinner-update (:spinner state) msg)]
      [(assoc state :spinner spinner) cmd])))

(defn view [state]
  (str (charm/spinner-view (:spinner state))
       " Loading data..."))

(charm/run {:init init
            :update update-fn
            :view view})
```

## Styled Spinner

```clojure
(charm/spinner :dots
               :style (charm/style :fg charm/cyan :bold true))
```
