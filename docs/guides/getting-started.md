# Getting Started with charm.clj

This guide walks you through building your first terminal UI application with charm.clj.

## Installation

Add charm.clj to your `deps.edn`:

```clojure
{:deps {io.github.yourname/charm.clj {:git/tag "v0.1.0" :git/sha "..."}}}
```

Or for local development, add the path:

```clojure
{:deps {}
 :paths ["src" "path/to/charm.clj/src"]}
```

## Your First App

Let's build a simple counter application.

### Step 1: Basic Structure

Create `src/counter/core.clj`:

```clojure
(ns counter.core
  (:require
   [charm.message :as msg]
   [charm.program :as program]))

(defn init []
  [{:count 0} nil])

(defn update-fn [state msg]
  [state nil])

(defn view [state]
  (str "Count: " (:count state)))

(defn -main [& _args]
  (program/run {:init init
                :update update-fn
                :view view}))
```

Run it:

```bash
clj -M -m counter.core
```

You'll see "Count: 0" but can't interact with it yet.

### Step 2: Handle Input

Add key handling to the update function:

```clojure
(defn update-fn [state msg]
  (cond
    ;; Quit on 'q'
    (msg/key-match? msg "q")
    [state program/quit-cmd]

    ;; Increment on up arrow or 'k'
    (or (msg/key-match? msg :up)
        (msg/key-match? msg "k"))
    [(update state :count inc) nil]

    ;; Decrement on down arrow or 'j'
    (or (msg/key-match? msg :down)
        (msg/key-match? msg "j"))
    [(update state :count dec) nil]

    ;; Ignore other input
    :else
    [state nil]))
```

### Step 3: Improve the View

Add instructions and styling:

```clojure
(defn view [state]
  (str "Counter: " (:count state) "\n\n"
       "Controls:\n"
       "  Up/k   - Increment\n"
       "  Down/j - Decrement\n"
       "  q      - Quit"))
```

### Step 4: Add Styling

Make it visually appealing:

```clojure
(ns counter.core
  (:require
   [charm.message :as msg]
   [charm.program :as program]
   [charm.style.border :as border]
   [charm.style.core :as style]))

(def title-style
  (style/style :fg style/cyan :bold true))

(def count-style
  (style/style :fg style/yellow
               :bold true
               :padding [1 3]
               :border border/rounded))

(def help-style
  (style/style :fg 240))  ; Gray

(defn view [state]
  (str (style/render title-style "Counter App") "\n\n"
       (style/render count-style (str (:count state))) "\n\n"
       (style/render help-style "Up/k: +1  Down/j: -1  q: quit")))
```

### Step 5: Use Alternate Screen

For a cleaner experience, use the alternate screen buffer:

```clojure
(defn -main [& _args]
  (program/run {:init init
                :update update-fn
                :view view
                :alt-screen true}))
```

## Complete Counter App

```clojure
(ns counter.core
  (:require
   [charm.message :as msg]
   [charm.program :as program]
   [charm.style.border :as border]
   [charm.style.core :as style]))

(def title-style
  (style/style :fg style/cyan :bold true))

(def count-style
  (style/style :fg style/yellow
               :bold true
               :padding [1 3]
               :border border/rounded))

(def help-style
  (style/style :fg 240))

(defn init []
  [{:count 0} nil])

(defn update-fn [state msg]
  (cond
    (msg/key-match? msg "q")
    [state program/quit-cmd]

    (or (msg/key-match? msg :up) (msg/key-match? msg "k"))
    [(update state :count inc) nil]

    (or (msg/key-match? msg :down) (msg/key-match? msg "j"))
    [(update state :count dec) nil]

    :else
    [state nil]))

(defn view [state]
  (str (style/render title-style "Counter App") "\n\n"
       (style/render count-style (str (:count state))) "\n\n"
       (style/render help-style "Up/k: +1  Down/j: -1  q: quit")))

(defn -main [& _args]
  (program/run {:init init
                :update update-fn
                :view view
                :alt-screen true}))
```

## Understanding the Elm Architecture

charm.clj uses the Elm Architecture pattern:

```
┌─────────────────────────────────────────────────┐
│                                                 │
│  ┌─────┐    ┌────────┐    ┌──────┐    ┌─────┐  │
│  │ Msg │───▶│ Update │───▶│ View │───▶│ UI  │  │
│  └─────┘    └────────┘    └──────┘    └─────┘  │
│      ▲           │                        │     │
│      │           ▼                        │     │
│      │      ┌────────┐                    │     │
│      │      │ State  │                    │     │
│      │      └────────┘                    │     │
│      │                                    │     │
│      └────────────────────────────────────┘     │
│                  User Input                     │
└─────────────────────────────────────────────────┘
```

1. **State**: A Clojure map holding your application data
2. **Messages**: Events from user input or async operations
3. **Update**: Pure function `(state, msg) -> [new-state, cmd]`
4. **View**: Pure function `state -> string`
5. **Commands**: Async operations that produce messages

## Adding a Component

Let's add a spinner to show loading state.

```clojure
(ns loader.core
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

    ;; Pass spinner ticks to the spinner
    :else
    (let [[s cmd] (spinner/spinner-update (:spinner state) msg)]
      [(assoc state :spinner s) cmd])))

(defn view [state]
  (str (spinner/spinner-view (:spinner state))
       " Loading..."))

(defn -main [& _args]
  (program/run {:init init
                :update update-fn
                :view view
                :alt-screen true}))
```

## Next Steps

- [Component Composition](component-composition.md) - Combining multiple components
- [Styling Patterns](styling-patterns.md) - Common styling recipes
- [Components Reference](../components/overview.md) - All available components
- [API Reference](../api/program.md) - Full API documentation
