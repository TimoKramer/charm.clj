# Component Overview

charm.clj provides reusable UI components that follow the Elm Architecture pattern. Each component has a consistent API with init, update, and view functions.

## Component Pattern

All components follow this structure:

```clojure
;; 1. Create a component
(def my-component (component-name options...))

;; 2. Initialize (returns [component cmd])
(let [[component cmd] (component-init my-component)]
  ;; Use component and execute cmd if non-nil
  )

;; 3. Update (returns [new-component cmd])
(let [[new-component cmd] (component-update component msg)]
  ;; Use new-component and execute cmd if non-nil
  )

;; 4. View (returns string)
(component-view component)
```

## The Elm Architecture

Components integrate with charm's program loop which handles:

- **State management**: Your app state contains component instances
- **Message passing**: User input and component events become messages
- **Command execution**: Async operations like timers return commands
- **Rendering**: The view function renders state to strings

```clojure
(require '[charm.core :as charm])

(defn init []
  [{:spinner (charm/spinner :dots)}
   nil])

(defn update-fn [state msg]
  (let [[new-spinner cmd] (charm/spinner-update (:spinner state) msg)]
    [(assoc state :spinner new-spinner) cmd]))

(defn view [state]
  (str "Loading " (charm/spinner-view (:spinner state))))

(charm/run {:init init
            :update update-fn
            :view view})
```

## Available Components

| Component | Description | Tick-based |
|-----------|-------------|------------|
| [spinner](spinner.md) | Animated loading indicators | Yes |
| [text-input](text-input.md) | Text entry with cursor editing | No |
| [list](list.md) | Scrollable item selection | No |
| [paginator](paginator.md) | Page navigation indicators | No |
| [timer](timer.md) | Countdown/count-up timer | Yes |
| [progress](progress.md) | Progress bar display | No |
| [help](help.md) | Keyboard shortcut display | No |

## Tick-based Components

Components like `spinner` and `timer` use asynchronous tick commands to animate. These components:

1. Return a command from `init` that starts the tick loop
2. Return commands from `update` to continue the animation
3. Use tags to handle stale tick messages correctly

```clojure
;; Spinner sends tick messages to itself
(let [[spinner cmd] (charm/spinner-init my-spinner)]
  ;; cmd will trigger a :spinner-tick message after the interval
  )
```

## Composing Components

Multiple components can be combined in a single application:

```clojure
(defn init []
  [{:input (charm/text-input :prompt "Search: ")
    :list (charm/item-list items)
    :help (charm/help bindings)}
   nil])

(defn update-fn [state msg]
  (cond
    ;; Route to text-input when focused
    (:focused (:input state))
    (let [[input cmd] (charm/text-input-update (:input state) msg)]
      [(assoc state :input input) cmd])

    ;; Otherwise route to list
    :else
    (let [[list cmd] (charm/list-update (:list state) msg)]
      [(assoc state :list list) cmd])))

(defn view [state]
  (str (charm/text-input-view (:input state)) "\n"
       (charm/list-view (:list state)) "\n"
       (charm/short-help-view (:help state))))
```

## Styling Components

Most components accept style options:

```clojure
(charm/spinner :dots
               :style (charm/style :fg charm/cyan))

(charm/text-input :prompt "Name: "
                  :prompt-style (charm/style :fg charm/green :bold true)
                  :text-style (charm/style :fg charm/white)
                  :cursor-style (charm/style :reverse true))

(charm/item-list items
                 :cursor-style (charm/style :fg charm/yellow :bold true)
                 :item-style (charm/style :fg 240))
```

See [styling](../api/styling.md) for full styling documentation.
