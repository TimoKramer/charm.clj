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
(require '[charm.components.spinner :as spinner]
         '[charm.program :as program])

(defn init []
  [{:spinner (spinner/spinner :dots)}
   nil])

(defn update-fn [state msg]
  (let [[new-spinner cmd] (spinner/spinner-update (:spinner state) msg)]
    [(assoc state :spinner new-spinner) cmd]))

(defn view [state]
  (str "Loading " (spinner/spinner-view (:spinner state))))

(program/run {:init init
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
(let [[s cmd] (spinner/spinner-init my-spinner)]
  ;; cmd will trigger a :spinner-tick message after the interval
  )
```

## Composing Components

Multiple components can be combined in a single application:

```clojure
(defn init []
  [{:input (text-input/text-input :prompt "Search: ")
    :list (item-list/item-list items)
    :help (help/help bindings)}
   nil])

(defn update-fn [state msg]
  (cond
    ;; Route to text-input when focused
    (:focused (:input state))
    (let [[input cmd] (text-input/text-input-update (:input state) msg)]
      [(assoc state :input input) cmd])

    ;; Otherwise route to list
    :else
    (let [[list cmd] (item-list/list-update (:list state) msg)]
      [(assoc state :list list) cmd])))

(defn view [state]
  (str (text-input/text-input-view (:input state)) "\n"
       (item-list/list-view (:list state)) "\n"
       (help/short-help-view (:help state))))
```

## Styling Components

Most components accept style options:

```clojure
(spinner/spinner :dots
                 :style (style/style :fg style/cyan))

(text-input/text-input :prompt "Name: "
                       :prompt-style (style/style :fg style/green :bold true)
                       :text-style (style/style :fg style/white)
                       :cursor-style (style/style :reverse true))

(item-list/item-list items
                     :cursor-style (style/style :fg style/yellow :bold true)
                     :item-style (style/style :fg 240))
```

See [styling](../api/styling.md) for full styling documentation.
