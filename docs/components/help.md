# Help

Keyboard shortcut display component with short and expanded modes.

## Quick Example

```clojure
(require '[charm.core :as charm])

(def bindings [{:key "j/k" :desc "up/down"}
               {:key "q" :desc "quit"}])

(def my-help (charm/help bindings))

;; In view function
(charm/help-view my-help)  ; => "j/k up/down • q quit"
```

## Creation Options

```clojure
(charm/help bindings & options)
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
(charm/help [{:key "j/k" :desc "up/down"}
             {:key "q" :desc "quit"}])

;; Pairs (vectors)
(charm/help [["j/k" "up/down"]
             ["q" "quit"]])

;; Using from-pairs helper
(charm/help (charm/help-from-pairs
             "j/k" "up/down"
             "q" "quit"))

;; Or with vectors
(charm/help (charm/help-from-pairs
             ["j/k" "up/down"]
             ["q" "quit"]))
```

## Display Modes

### Short Mode (default)

Single line with separator:

```clojure
(charm/help bindings)
;; => "j/k up/down • q quit • ? help"
```

With width constraint:

```clojure
(charm/help bindings :width 30)
;; => "j/k up/down • q quit • …"
```

### Full Mode

Multi-line with aligned columns:

```clojure
(charm/help bindings :show-all true)
;; j/k        up/down
;; q          quit
;; ?          help
```

## Functions

### help-view

```clojure
(charm/help-view help) ; => "j/k up/down • q quit"
```

Render help as a string.

### help-toggle-show-all

```clojure
(charm/help-toggle-show-all help)
```

Toggle between short and full display modes.

### help-set-show-all

```clojure
(charm/help-set-show-all help true)  ; Show full
(charm/help-set-show-all help false) ; Show short
```

### help-set-bindings

```clojure
(charm/help-set-bindings help new-bindings)
```

### help-add-binding

```clojure
(charm/help-add-binding help "n" "new item")
```

### help-from-pairs

```clojure
;; Interleaved arguments
(charm/help-from-pairs "j" "down" "k" "up" "q" "quit")

;; Vector pairs
(charm/help-from-pairs ["j" "down"] ["k" "up"] ["q" "quit"])
```

## Full Example

```clojure
(ns my-app
  (:require [charm.core :as charm]))

(def bindings
  (charm/help-from-pairs
   "j/k" "navigate"
   "Enter" "select"
   "?" "toggle help"
   "q" "quit"))

(defn init []
  [{:help (charm/help bindings :width 60)
    :items ["Item 1" "Item 2" "Item 3"]}
   nil])

(defn update-fn [state msg]
  (cond
    (charm/key-match? msg "q")
    [state charm/quit-cmd]

    (charm/key-match? msg "?")
    [(update state :help charm/help-toggle-show-all) nil]

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
                (charm/help-view (:help state)))
           (charm/help-view (:help state))))))

(charm/run {:init init :update update-fn :view view})
```

## Styled Help

```clojure
(charm/help bindings
            :key-style (charm/style :fg charm/cyan :bold true)
            :desc-style (charm/style :fg 250)
            :separator-style (charm/style :fg 240))
```

## Dynamic Bindings

Update bindings based on application state:

```clojure
(defn get-bindings [state]
  (if (:editing state)
    (charm/help-from-pairs
     "Esc" "cancel"
     "Enter" "save")
    (charm/help-from-pairs
     "e" "edit"
     "d" "delete"
     "q" "quit")))

(defn view [state]
  (charm/help-view (charm/help (get-bindings state))))
```
