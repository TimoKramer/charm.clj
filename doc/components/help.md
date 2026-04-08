# Help

Keyboard shortcut display component with short and expanded modes.

## Quick Example

```clojure
(require '[charm.components.help :as help])

(def bindings [{:key "j/k" :desc "up/down"}
               {:key "q" :desc "quit"}])

(def my-help (help/help bindings))

;; In view function
(help/short-help-view my-help)  ; => "j/k up/down • q quit"
```

## Creation Options

```clojure
(help/help bindings & options)
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `bindings` | vector | required | Key binding definitions |
| `:width` | int | `0` | Max width (0 = unlimited) |
| `:separator` | string | `" • "` | Separator between bindings |
| `:show-all` | boolean | `false` | Show full multi-line help |
| `:ellipsis` | string | `"…"` | Shown when truncated |
| `:key-style` | style | bold | Style for key text |
| `:desc-style` | style | gray | Style for description |
| `:separator-style` | style | gray | Style for separator |
| `:id` | any | random | Unique identifier |

## Binding Formats

```clojure
;; Maps with :key and :desc
(help/help [{:key "j/k" :desc "up/down"}
            {:key "q" :desc "quit"}])

;; Pairs (vectors)
(help/help [["j/k" "up/down"]
            ["q" "quit"]])

;; Using from-pairs helper
(help/help (help/from-pairs
            "j/k" "up/down"
            "q" "quit"))

;; Or with vectors
(help/help (help/from-pairs
            ["j/k" "up/down"]
            ["q" "quit"]))
```

## Display Modes

### Short Mode (default)

Single line with separator:

```clojure
(help/help bindings)
;; => "j/k up/down • q quit • ? help"
```

With width constraint:

```clojure
(help/help bindings :width 30)
;; => "j/k up/down • q quit • …"
```

### Full Mode

Multi-line with aligned columns:

```clojure
(help/help bindings :show-all true)
;; j/k        up/down
;; q          quit
;; ?          help
```

## Functions

### short-help-view / full-help-view

```clojure
(help/short-help-view h) ; => "j/k up/down • q quit"
(help/full-help-view h)  ; => multi-line aligned view
```

Render help as a string.

### toggle-show-all

```clojure
(help/toggle-show-all h)
```

Toggle between short and full display modes.

### set-show-all

```clojure
(help/set-show-all h true)  ; Show full
(help/set-show-all h false) ; Show short
```

### set-bindings

```clojure
(help/set-bindings h new-bindings)
```

### add-binding

```clojure
(help/add-binding h "n" "new item")
```

### from-pairs

```clojure
;; Interleaved arguments
(help/from-pairs "j" "down" "k" "up" "q" "quit")

;; Vector pairs
(help/from-pairs ["j" "down"] ["k" "up"] ["q" "quit"])
```

## Full Example

```clojure
(ns my-app
  (:require
   [charm.components.help :as help]
   [charm.message :as msg]
   [charm.program :as program]
   [charm.style.core :as style]))

(def bindings
  (help/from-pairs
   "j/k" "navigate"
   "Enter" "select"
   "?" "toggle help"
   "q" "quit"))

(defn init []
  [{:help (help/help bindings :width 60)
    :items ["Item 1" "Item 2" "Item 3"]}
   nil])

(defn update-fn [state msg]
  (cond
    (msg/key-match? msg "q")
    [state program/quit-cmd]

    (msg/key-match? msg "?")
    [(update state :help help/toggle-show-all) nil]

    :else
    [state nil]))

(defn view [state]
  (let [show-full? (:show-all (:help state))]
    (str "My Application\n\n"
         (clojure.string/join "\n" (:items state))
         "\n\n"
         (if show-full?
           (str "Keyboard Shortcuts\n"
                "──────────────────\n"
                (help/full-help-view (:help state)))
           (help/short-help-view (:help state))))))

(program/run {:init init :update update-fn :view view})
```

## Styled Help

```clojure
(help/help bindings
           :key-style (style/style :fg style/cyan :bold true)
           :desc-style (style/style :fg 250)
           :separator-style (style/style :fg 240))
```

## Dynamic Bindings

Update bindings based on application state:

```clojure
(defn get-bindings [state]
  (if (:editing state)
    (help/from-pairs
     "Esc" "cancel"
     "Enter" "save")
    (help/from-pairs
     "e" "edit"
     "d" "delete"
     "q" "quit")))

(defn view [state]
  (help/short-help-view (help/help (get-bindings state))))
```
