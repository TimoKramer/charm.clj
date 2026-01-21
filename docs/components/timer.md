# Timer

Countdown timer component with start/stop controls and formatted display.

## Quick Example

```clojure
(require '[charm.core :as charm])

(def my-timer (charm/timer :timeout 60000))  ; 60 seconds

;; Initialize to start countdown
(let [[timer cmd] (charm/timer-init my-timer)]
  ;; timer is ready, cmd starts the tick loop
  )

;; In update function
(let [[timer cmd] (charm/timer-update timer msg)]
  ;; Handle timer ticks
  )

;; In view function
(charm/timer-view timer)  ; => "1:00"
```

## Creation Options

```clojure
(charm/timer & options)
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
(charm/timer-init timer) ; => [timer cmd]
```

Initialize the timer. Returns command to start ticking if `:running` is true.

### timer-update

```clojure
(charm/timer-update timer msg) ; => [timer cmd]
```

Handle tick messages. Decrements timeout and continues ticking until timeout reaches 0.

### timer-view

```clojure
(charm/timer-view timer) ; => "1:00"
```

Render the timer as formatted duration:
- `5s` for seconds only
- `1:30` for minutes and seconds
- `1:30:00` for hours, minutes, and seconds

### Control Functions

```clojure
;; Start the timer, returns [timer cmd]
(charm/timer-start timer)

;; Stop the timer, returns [timer nil]
(charm/timer-stop timer)

;; Toggle running state, returns [timer cmd-or-nil]
(charm/timer-toggle timer)

;; Reset to a timeout value, returns [timer cmd]
(charm/timer-reset timer 60000)
```

### Accessors

```clojure
(charm/timer-timeout timer)   ; Get remaining ms
(charm/timer-interval timer)  ; Get tick interval
(charm/timer-running? timer)  ; Check if running
(charm/timer-timed-out? timer) ; Check if <= 0
```

## Full Example

```clojure
(ns my-app
  (:require [charm.core :as charm]))

(def initial-time 30000)  ; 30 seconds

(defn init []
  (let [[timer cmd] (charm/timer-init
                     (charm/timer :timeout initial-time
                                  :interval 100
                                  :running false))]
    [{:timer timer
      :initial-time initial-time}
     cmd]))

(defn update-fn [state msg]
  (cond
    (charm/key-match? msg "q")
    [state charm/quit-cmd]

    ;; Space to toggle
    (charm/key-match? msg " ")
    (let [[timer cmd] (charm/timer-toggle (:timer state))]
      [(assoc state :timer timer) cmd])

    ;; R to reset
    (charm/key-match? msg "r")
    (let [[timer cmd] (charm/timer-reset (:timer state) (:initial-time state))]
      [(assoc state :timer timer) cmd])

    ;; Handle timer ticks
    :else
    (let [[timer cmd] (charm/timer-update (:timer state) msg)]
      [(assoc state :timer timer) cmd])))

(defn view [state]
  (let [timer (:timer state)
        status (cond
                 (charm/timer-timed-out? timer) "Time's up!"
                 (charm/timer-running? timer) "Running"
                 :else "Paused")]
    (str "Timer: " (charm/timer-view timer) "\n"
         "Status: " status "\n\n"
         "Space: start/stop  R: reset  Q: quit")))

(charm/run {:init init :update update-fn :view view :alt-screen true})
```

## Styled Timer

```clojure
(charm/timer :timeout 60000
             :style (charm/style :fg charm/cyan
                                 :bold true
                                 :padding [1 2]
                                 :border charm/rounded-border))
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
(charm/for-timer? timer msg) ; => boolean
```
