# Spinner

Animated loading indicators with 15 built-in animation styles.

## Quick Example

```clojure
(require '[charm.components.spinner :as spinner])

(def my-spinner (spinner/spinner :dots))

;; Initialize to start animation
(let [[s cmd] (spinner/spinner-init my-spinner)]
  ;; s is ready, cmd starts the tick loop
  )

;; In update function
(let [[s cmd] (spinner/spinner-update s msg)]
  ;; Handle spinner tick messages
  )

;; In view function
(spinner/spinner-view s)  ; => "⠋" (animates)
```

## Creation Options

```clojure
(spinner/spinner type & options)
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
(spinner/spinner {:frames ["◐" "◓" "◑" "◒"]
                  :interval 150})
```

## Functions

### spinner-init

```clojure
(spinner/spinner-init s) ; => [spinner cmd]
```

Initialize the spinner and start the tick loop. Returns `[spinner cmd]` where `cmd` triggers the first tick.

### spinner-update

```clojure
(spinner/spinner-update s msg) ; => [spinner cmd]
```

Handle tick messages. Returns `[spinner cmd]` to continue animation, or `[spinner nil]` if message not handled.

### spinner-view

```clojure
(spinner/spinner-view s) ; => "⠋"
```

Render current frame as a string.

### spinning?

```clojure
(spinner/spinning? s msg) ; => boolean
```

Check if a message is a tick for this spinner.

## Full Example

```clojure
(ns my-app
  (:require
   [charm.components.spinner :as spinner]
   [charm.message :as msg]
   [charm.program :as program]))

(defn init []
  (let [[s cmd] (spinner/spinner-init (spinner/spinner :dots))]
    [{:spinner s
      :loading true}
     cmd]))

(defn update-fn [state msg]
  (cond
    (msg/key-match? msg "q")
    [state program/quit-cmd]

    :else
    (let [[s cmd] (spinner/spinner-update (:spinner state) msg)]
      [(assoc state :spinner s) cmd])))

(defn view [state]
  (str (spinner/spinner-view (:spinner state))
       " Loading data..."))

(program/run {:init init
              :update update-fn
              :view view})
```

## Styled Spinner

```clojure
(spinner/spinner :dots
                 :style (style/style :fg style/cyan :bold true))
```
