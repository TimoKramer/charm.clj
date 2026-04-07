# Messages API

Messages are plain maps with a `:type` key that flow through the update function. charm.clj provides factory functions and predicates for common message types.

## Key Press Messages

### key-press

```clojure
(msg/key-press key & options)
```

Create a key press message (mainly for testing).

**Options:**

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `key` | string/keyword | required | The key pressed |
| `:ctrl` | boolean | `false` | Ctrl modifier |
| `:alt` | boolean | `false` | Alt modifier |
| `:shift` | boolean | `false` | Shift modifier |

```clojure
(msg/key-press "a")
(msg/key-press :enter)
(msg/key-press "c" :ctrl true)
```

### key-press?

```clojure
(msg/key-press? msg) ; => boolean
```

Check if a message is a key press.

### key-match?

```clojure
(msg/key-match? msg key) ; => boolean
```

Check if a key press matches a specific key pattern.

**Key patterns:**

| Pattern | Matches |
|---------|---------|
| `"a"` | Letter a |
| `"A"` | Shift+a |
| `"1"` | Number 1 |
| `" "` | Space |
| `:enter` | Enter key |
| `:tab` | Tab key |
| `:up` | Up arrow |
| `:down` | Down arrow |
| `:left` | Left arrow |
| `:right` | Right arrow |
| `:backspace` | Backspace |
| `:delete` | Delete |
| `:escape` or `"esc"` | Escape |
| `:home` | Home |
| `:end` | End |
| `:pgup` | Page Up |
| `:pgdown` | Page Down |
| `"ctrl+c"` | Ctrl+C |
| `"ctrl+x"` | Ctrl+X |
| `"alt+f"` | Alt+F |
| `"shift+tab"` | Shift+Tab |
| `"ctrl+alt+delete"` | Ctrl+Alt+Delete |

**Examples:**

```clojure
(defn update-fn [state msg]
  (cond
    (msg/key-match? msg "q") [state program/quit-cmd]
    (msg/key-match? msg :enter) [(submit state) nil]
    (msg/key-match? msg "ctrl+c") [state program/quit-cmd]
    (msg/key-match? msg :up) [(move-up state) nil]
    :else [state nil]))
```

### Modifier Predicates

```clojure
(msg/ctrl? msg)  ; => boolean - Ctrl modifier set?
(msg/alt? msg)   ; => boolean - Alt modifier set?
(msg/shift? msg) ; => boolean - Shift modifier set?
```

## Mouse Messages

### mouse

```clojure
(msg/mouse action button x y & options)
```

Create a mouse event message (mainly for testing).

**Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `action` | keyword | `:press`, `:release`, `:motion`, `:wheel-up`, `:wheel-down` |
| `button` | keyword | `:left`, `:middle`, `:right`, `:none` |
| `x` | int | Column (0-indexed) |
| `y` | int | Row (0-indexed) |

**Options:** `:ctrl`, `:alt`, `:shift` (boolean modifiers)

```clojure
(msg/mouse :press :left 10 5)
(msg/mouse :wheel-up :none 10 5)
```

### mouse?

```clojure
(msg/mouse? msg) ; => boolean
```

Check if a message is a mouse event.

**Mouse message structure:**

```clojure
{:type :mouse
 :action :press      ; :press, :release, :motion, :wheel-up, :wheel-down
 :button :left       ; :left, :middle, :right, :none
 :x 10               ; column
 :y 5                ; row
 :ctrl false
 :alt false
 :shift false}
```

**Example:**

```clojure
(defn update-fn [state msg]
  (cond
    (and (msg/mouse? msg) (= :press (:action msg)))
    (let [{:keys [x y button]} msg]
      [(handle-click state x y button) nil])

    (and (msg/mouse? msg) (= :wheel-up (:action msg)))
    [(scroll-up state) nil]

    :else
    [state nil]))
```

## Window Size Messages

### window-size

```clojure
(msg/window-size width height)
```

Create a window size message (sent automatically on resize).

### window-size?

```clojure
(msg/window-size? msg) ; => boolean
```

**Window size message structure:**

```clojure
{:type :window-size
 :width 80
 :height 24}
```

**Example:**

```clojure
(defn update-fn [state msg]
  (if (msg/window-size? msg)
    [(assoc state
            :width (:width msg)
            :height (:height msg))
     nil]
    [state nil]))
```

## Focus Messages

### focus / blur

```clojure
(msg/focus) ; Create focus gained message
(msg/blur)  ; Create focus lost message
```

### focus? / blur?

```clojure
(msg/focus? msg) ; => boolean
(msg/blur? msg)  ; => boolean
```

Requires `:focus-reporting true` in run options.

```clojure
(defn update-fn [state msg]
  (cond
    (msg/focus? msg)
    [(assoc state :active true) nil]

    (msg/blur? msg)
    [(assoc state :active false) nil]

    :else
    [state nil]))
```

## Quit and Error Messages

### quit

```clojure
(msg/quit) ; Create quit message
```

### quit?

```clojure
(msg/quit? msg) ; => boolean
```

### error

```clojure
(msg/error throwable) ; Create error message
```

### error?

```clojure
(msg/error? msg) ; => boolean
```

## Message Type Helper

### msg-type

```clojure
(msg/msg-type msg) ; => keyword
```

Get the type of any message.

```clojure
(msg/msg-type {:type :key-press :key "a"}) ; => :key-press
```

## Custom Messages

Create custom messages as plain maps with a `:type` key:

```clojure
;; Define custom message types
(defn data-loaded [data]
  {:type :data-loaded
   :data data})

(defn timer-tick [id]
  {:type :timer-tick
   :id id})

;; Use in update function
(defn update-fn [state msg]
  (case (:type msg)
    :data-loaded (handle-data state (:data msg))
    :timer-tick (handle-tick state (:id msg))
    :key-press (handle-key state msg)
    [state nil]))
```

## Complete Example

```clojure
(ns my-app
  (:require
   [charm.message :as msg]
   [charm.program :as program]))

(defn update-fn [state msg]
  (cond
    ;; Quit on q or Ctrl+C
    (or (msg/key-match? msg "q")
        (msg/key-match? msg "ctrl+c"))
    [state program/quit-cmd]

    ;; Navigation
    (msg/key-match? msg :up)
    [(update state :cursor dec) nil]

    (msg/key-match? msg :down)
    [(update state :cursor inc) nil]

    ;; Window resize
    (msg/window-size? msg)
    [(assoc state :size [(:width msg) (:height msg)]) nil]

    ;; Mouse click
    (and (msg/mouse? msg) (= :press (:action msg)))
    [(assoc state :clicked [(:x msg) (:y msg)]) nil]

    ;; Focus changes
    (msg/focus? msg)
    [(assoc state :focused true) nil]

    (msg/blur? msg)
    [(assoc state :focused false) nil]

    :else
    [state nil]))
```
