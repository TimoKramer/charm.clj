# Text Input

Text entry component with cursor movement, editing, and multiple echo modes.

## Quick Example

```clojure
(require '[charm.components.text-input :as text-input])

(def my-input (text-input/text-input :prompt "Name: "
                                     :placeholder "Enter your name"))

;; In update function
(let [[input cmd] (text-input/text-input-update my-input msg)]
  ;; Handle key presses
  )

;; In view function
(text-input/text-input-view my-input)  ; => "Name: |Enter your name"
```

## Creation Options

```clojure
(text-input/text-input & options)
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:prompt` | string | `"> "` | Text displayed before input |
| `:placeholder` | string | `nil` | Text shown when empty |
| `:value` | string | `""` | Initial value |
| `:echo-mode` | keyword | `:normal` | `:normal`, `:password`, or `:none` |
| `:echo-char` | char | `\*` | Character for password mode |
| `:char-limit` | int | `0` | Max characters (0 = unlimited) |
| `:width` | int | `0` | Display width (0 = unlimited) |
| `:focused` | boolean | `true` | Start focused |
| `:prompt-style` | style | `nil` | Style for prompt |
| `:text-style` | style | `nil` | Style for input text |
| `:placeholder-style` | style | gray | Style for placeholder |
| `:cursor-style` | style | reverse | Style for cursor |
| `:id` | any | random | Unique identifier |

## Echo Modes

```clojure
;; Normal - shows typed text
(text-input/text-input :echo-mode text-input/echo-normal)

;; Password - shows asterisks
(text-input/text-input :echo-mode text-input/echo-password)
(text-input/text-input :echo-mode text-input/echo-password :echo-char \●)

;; None - hides all input
(text-input/text-input :echo-mode text-input/echo-none)
```

## Key Bindings

| Action | Keys |
|--------|------|
| Move left | `Left`, `Ctrl+B` |
| Move right | `Right`, `Ctrl+F` |
| Word left | `Alt+Left`, `Ctrl+Left`, `Alt+B` |
| Word right | `Alt+Right`, `Ctrl+Right`, `Alt+F` |
| Line start | `Home`, `Ctrl+A` |
| Line end | `End`, `Ctrl+E` |
| Delete char left | `Backspace`, `Ctrl+H` |
| Delete char right | `Delete`, `Ctrl+D` |
| Delete word left | `Alt+Backspace`, `Ctrl+W` |
| Delete word right | `Alt+Delete`, `Alt+D` |
| Delete to start | `Ctrl+U` |
| Delete to end | `Ctrl+K` |

## Functions

### text-input-update

```clojure
(text-input/text-input-update input msg) ; => [input cmd]
```

Handle key messages. Only processes input when focused.

### text-input-view

```clojure
(text-input/text-input-view input) ; => "Name: John|"
```

Render the input with prompt and cursor.

### value

```clojure
(text-input/value input) ; => "John"
```

Get current value as a string.

### focus / blur

```clojure
(text-input/focus input)  ; Focus the input
(text-input/blur input)   ; Unfocus the input
```

### reset

```clojure
(text-input/reset input)  ; Clear the value
```

### set-value

```clojure
(text-input/set-value input "new value")
```

Set the value and move cursor to end.

## Full Example

```clojure
(ns my-app
  (:require
   [charm.components.text-input :as text-input]
   [charm.message :as msg]
   [charm.program :as program]))

(defn init []
  [{:username (text-input/text-input :prompt "Username: " :focused true)
    :password (text-input/text-input :prompt "Password: "
                                     :echo-mode text-input/echo-password
                                     :focused false)
    :current :username}
   nil])

(defn update-fn [state msg]
  (cond
    (msg/key-match? msg "tab")
    (let [next-field (if (= :username (:current state)) :password :username)]
      [(-> state
           (update :username (if (= next-field :username)
                               text-input/focus
                               text-input/blur))
           (update :password (if (= next-field :password)
                               text-input/focus
                               text-input/blur))
           (assoc :current next-field))
       nil])

    (msg/key-match? msg "enter")
    ;; Submit form
    [state program/quit-cmd]

    :else
    (let [field (:current state)
          [input cmd] (text-input/text-input-update (get state field) msg)]
      [(assoc state field input) cmd])))

(defn view [state]
  (str (text-input/text-input-view (:username state)) "\n"
       (text-input/text-input-view (:password state)) "\n\n"
       "Tab to switch fields, Enter to submit"))

(program/run {:init init :update update-fn :view view})
```
