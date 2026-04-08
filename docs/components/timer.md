# Timer

Countdown timer component with start/stop controls and formatted display.

## Quick Example

```clojure
(require '[charm.components.timer :as timer])

(def my-timer (timer/timer :timeout 60000))  ; 60 seconds

;; Initialize to start countdown
(let [[t cmd] (timer/timer-init my-timer)]
  ;; t is ready, cmd starts the tick loop
  )

;; In update function
(let [[t cmd] (timer/timer-update t msg)]
  ;; Handle timer ticks
  )

;; In view function
(timer/timer-view t)  ; => "1:00"
```

## Creation Options

```clojure
(timer/timer & options)
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:timeout` | int | `0` | Time in milliseconds |
| `:interval` | int | `1000` | Tick interval in milliseconds |
| `:running` | boolean | `true` | Start running immediately |
| `:style` | style | `nil` | Style for timer display |
| `:id` | any | random | Unique identifier |

## Functions

### timer-init

```clojure
(timer/timer-init t) ; => [timer cmd]
```

Initialize the timer. Returns command to start ticking if `:running` is true.

### timer-update

```clojure
(timer/timer-update t msg) ; => [timer cmd]
```

Handle tick messages. Decrements timeout and continues ticking until timeout reaches 0.

### timer-view

```clojure
(timer/timer-view t) ; => "1:00"
```

Render the timer as formatted duration:
- `5s` for seconds only
- `1:30` for minutes and seconds
- `1:30:00` for hours, minutes, and seconds

### Control Functions

```clojure
;; Start the timer, returns [timer cmd]
(timer/start t)

;; Stop the timer, returns [timer nil]
(timer/stop t)

;; Toggle running state, returns [timer cmd-or-nil]
(timer/toggle t)

;; Reset to a timeout value, returns [timer cmd]
(timer/reset t 60000)
```

### Accessors

```clojure
(timer/timeout t)   ; Get remaining ms
(timer/interval t)  ; Get tick interval
(timer/running? t)  ; Check if running
(timer/timed-out? t) ; Check if <= 0
```

## Full Example

```clojure
(ns my-app
  (:require
   [charm.components.timer :as timer]
   [charm.message :as msg]
   [charm.program :as program]))

(def initial-time 30000)  ; 30 seconds

(defn init []
  (let [[t cmd] (timer/timer-init
                 (timer/timer :timeout initial-time
                              :interval 100
                              :running false))]
    [{:timer t
      :initial-time initial-time}
     cmd]))

(defn update-fn [state msg]
  (cond
    (msg/key-match? msg "q")
    [state program/quit-cmd]

    ;; Space to toggle
    (msg/key-match? msg " ")
    (let [[t cmd] (timer/toggle (:timer state))]
      [(assoc state :timer t) cmd])

    ;; R to reset
    (msg/key-match? msg "r")
    (let [[t cmd] (timer/reset (:timer state) (:initial-time state))]
      [(assoc state :timer t) cmd])

    ;; Handle timer ticks
    :else
    (let [[t cmd] (timer/timer-update (:timer state) msg)]
      [(assoc state :timer t) cmd])))

(defn view [state]
  (let [t (:timer state)
        status (cond
                 (timer/timed-out? t) "Time's up!"
                 (timer/running? t) "Running"
                 :else "Paused")]
    (str "Timer: " (timer/timer-view t) "\n"
         "Status: " status "\n\n"
         "Space: start/stop  R: reset  Q: quit")))

(program/run {:init init :update update-fn :view view :alt-screen true})
```

## Styled Timer

```clojure
(timer/timer :timeout 60000
             :style (style/style :fg style/cyan
                                 :bold true
                                 :padding [1 2]
                                 :border border/rounded))
```

## Timer Events

The timer sends these message types:

```clojure
;; Tick message (every interval)
{:type :timer-tick
 :timer-id <id>
 :tag <tag>}

;; Timeout message (when reaching 0)
{:type :timer-timeout
 :timer-id <id>}
```

Check if a message is for a specific timer:

```clojure
(timer/for-timer? t msg) ; => boolean
```
