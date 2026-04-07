# Program API

The program module provides the main event loop and command system for TUI applications.

## Running a Program

### run

```clojure
(program/run options)
```

Run a TUI program with the Elm Architecture pattern.

**Options:**

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:init` | fn/value | required | Initial state or `(fn [] [state cmd])` |
| `:update` | fn | required | `(fn [state msg] [new-state cmd])` |
| `:view` | fn | required | `(fn [state] string)` |
| `:alt-screen` | boolean | `false` | Use alternate screen buffer |
| `:mouse` | keyword | `nil` | Mouse mode: `nil`, `:normal`, `:cell`, `:all` |
| `:focus-reporting` | boolean | `false` | Report focus in/out events |
| `:fps` | int | `60` | Frames per second |
| `:hide-cursor` | boolean | `true` | Hide terminal cursor |

**Example:**

```clojure
(program/run
  {:init {:count 0}
   :update (fn [state msg]
             (cond
               (msg/key-match? msg "q") [state program/quit-cmd]
               (msg/key-match? msg "up") [(update state :count inc) nil]
               :else [state nil]))
   :view (fn [state]
           (str "Count: " (:count state) "\nPress q to quit"))
   :alt-screen true})
```

### run-async

```clojure
(program/run-async options)
```

Run a TUI program in the background. Accepts the same options as `run` but returns immediately with a handle instead of blocking. Mainly for testing in the REPL.

**Returns** a map with:

| Key | Type | Description |
|-----|------|-------------|
| `:quit!` | fn | Call to stop the program |
| `:result` | promise | Deref to get the final state |

**Example:**

```clojure
;; Start the app in the background
(def app (program/run-async {:init init
                             :update update-fn
                             :view view
                             :alt-screen true}))

;; Stop it from another thread / REPL
((:quit! app))

;; Get the final state (blocks until the app has stopped)
@(:result app)
```

## Commands

Commands are asynchronous operations that produce messages. They're returned from `init` and `update` functions.

### cmd

```clojure
(program/cmd f)
```

Create a command from a function that returns a message.

```clojure
;; Command that sends a message after 1 second
(program/cmd (fn []
               (Thread/sleep 1000)
               {:type :timer-done}))
```

### batch

```clojure
(program/batch & cmds)
```

Combine multiple commands into one. All commands run in parallel.

```clojure
(program/batch
  (program/cmd #(do-thing-1))
  (program/cmd #(do-thing-2))
  (program/cmd #(do-thing-3)))
```

### sequence-cmds

```clojure
(program/sequence-cmds & cmds)
```

Run commands in sequence (each waits for the previous to complete).

```clojure
(program/sequence-cmds
  (program/cmd #(step-1))
  (program/cmd #(step-2))
  (program/cmd #(step-3)))
```

### quit-cmd

```clojure
program/quit-cmd
```

A pre-built command that exits the program.

```clojure
(defn update-fn [state msg]
  (if (msg/key-match? msg "q")
    [state program/quit-cmd]
    [state nil]))
```

## Init Function

The `init` option can be:

1. **A value** - used as initial state, no startup command

```clojure
{:init {:count 0}}
```

2. **A function** returning `[state cmd]`

```clojure
{:init (fn []
         (let [[t cmd] (timer/timer-init (timer/timer :timeout 5000))]
           [{:timer t} cmd]))}
```

3. **A function** returning just `state`

```clojure
{:init (fn [] {:count 0})}
```

## Update Function

The update function receives the current state and a message, returning `[new-state cmd]`.

```clojure
(defn update-fn [state msg]
  (cond
    ;; Handle quit
    (msg/key-match? msg "q")
    [state program/quit-cmd]

    ;; Handle key press
    (msg/key-match? msg "up")
    [(update state :count inc) nil]

    ;; Handle custom message
    (= :data-loaded (:type msg))
    [(assoc state :data (:data msg)) nil]

    ;; Ignore unhandled messages
    :else
    [state nil]))
```

## View Function

The view function receives state and returns a string to display.

```clojure
(defn view [state]
  (str "Count: " (:count state) "\n"
       "Press up/down to change, q to quit"))
```

## Mouse Modes

| Mode | Description |
|------|-------------|
| `nil` | No mouse support |
| `:normal` | Basic click events |
| `:cell` | Click and drag events |
| `:all` | All mouse events including motion |

```clojure
(program/run {:init init
              :update update-fn
              :view view
              :mouse :normal})
```

## Focus Reporting

When enabled, focus events are sent when the terminal gains/loses focus.

```clojure
(program/run {:init init
              :update update-fn
              :view view
              :focus-reporting true})

;; In update function
(defn update-fn [state msg]
  (cond
    (msg/focus? msg)
    [(assoc state :focused true) nil]

    (msg/blur? msg)
    [(assoc state :focused false) nil]

    :else
    [state nil]))
```

## Complete Example

```clojure
(ns my-app
  (:require
   [charm.message :as msg]
   [charm.program :as program]))

(defn fetch-data-cmd []
  (program/cmd (fn []
                 ;; Simulate async data fetch
                 (Thread/sleep 1000)
                 {:type :data-loaded
                  :data ["Item 1" "Item 2" "Item 3"]})))

(defn init []
  [{:loading true :data nil}
   (fetch-data-cmd)])

(defn update-fn [state msg]
  (cond
    (msg/key-match? msg "q")
    [state program/quit-cmd]

    (= :data-loaded (:type msg))
    [(assoc state :loading false :data (:data msg)) nil]

    :else
    [state nil]))

(defn view [state]
  (if (:loading state)
    "Loading..."
    (str "Data:\n" (clojure.string/join "\n" (:data state))
         "\n\nPress q to quit")))

(defn -main [& _args]
  (program/run {:init init
                :update update-fn
                :view view
                :alt-screen true}))
```
