# charm.clj

![Status](https://img.shields.io/badge/status-alpha-orange)

A Clojure TUI (Terminal User Interface) library inspired by [Bubble Tea](https://github.com/charmbracelet/bubbletea).

Build terminal applications using the Elm Architecture (Model-Update-View pattern) with a simple, functional API. Run them
on the JVM, as a native-image binary or on [babashka](https://babashka.org).

## Status

This library is vibecoded and very early. It works for the examples but please let me know if you encounter any
issues. I am planning to use it for something more sophisticated. Please expect breaking changes.

## Features

- **Elm Architecture** - Simple init/update/view pattern for predictable state management
- **UI Components** - Spinner, text-input, list, paginator, timer, progress, help
- **Styling** - Colors (ANSI, 256, true color), borders, padding, alignment
- **Input handling** - Keyboard and mouse events with modifier support
- **Efficient rendering** - Line diffing for minimal terminal updates
- **core.async** - Asynchronous command execution

## Documentation

- **[Getting Started](docs/guides/getting-started.md)** - Build your first app
- **[Components](docs/components/overview.md)** - UI component reference
  - [spinner](docs/components/spinner.md), [text-input](docs/components/text-input.md), [list](docs/components/list.md), [paginator](docs/components/paginator.md), [timer](docs/components/timer.md), [progress](docs/components/progress.md), [help](docs/components/help.md)
- **API Reference**
  - [Program](docs/api/program.md) - run, cmd, batch, quit-cmd
  - [Messages](docs/api/messages.md) - key-press, mouse, window-size
  - [Styling](docs/api/styling.md) - style, render, colors, borders
  - [Layout](docs/api/layout.md) - join-horizontal, join-vertical
- **Guides**
  - [Component Composition](docs/guides/component-composition.md)
  - [Styling Patterns](docs/guides/styling-patterns.md)
- **[Examples](docs/examples/README.md)** - Runnable demo applications

## Installation

Add to your `deps.edn`:

```clojure
{:deps {charm.clj {:local/root "/path/to/charm.clj"}}}
```

Or if published:

```clojure
{:deps {io.github.yourname/charm.clj {:git/tag "v0.1.0" :git/sha "..."}}}
```

## Quick Start

```clojure
(ns myapp.core
  (:require [charm.core :as charm]))

(defn update-fn [state msg]
  (cond
    (charm/key-match? msg "q") [state charm/quit-cmd]
    (charm/key-match? msg "k") [(update state :count inc) nil]
    (charm/key-match? msg "j") [(update state :count dec) nil]
    :else [state nil]))

(defn view [state]
  (str "Count: " (:count state) "\n\n"
       "j/k to change, q to quit"))

(charm/run {:init {:count 0}
            :update update-fn
            :view view})
```

## API Overview

### Running a Program

```clojure
(charm/run {:init    initial-state-or-fn
            :update  (fn [state msg] [new-state cmd])
            :view    (fn [state] "string to render")

            ;; Options
            :alt-screen false      ; Use alternate screen buffer
            :mouse :cell           ; Mouse mode: nil, :normal, :cell, :all
            :focus-reporting false ; Report focus in/out events
            :fps 60})              ; Frames per second
```

### Messages

Messages are maps with a `:type` key. Built-in message types:

```clojure
;; Check message types
(charm/key-press? msg)    ; Keyboard input
(charm/mouse? msg)        ; Mouse event
(charm/window-size? msg)  ; Terminal resized
(charm/quit? msg)         ; Quit signal

;; Match specific keys
(charm/key-match? msg "q")        ; Letter q
(charm/key-match? msg "ctrl+c")   ; Ctrl+C
(charm/key-match? msg "enter")    ; Enter key
(charm/key-match? msg :up)        ; Arrow up

;; Check modifiers
(charm/ctrl? msg)
(charm/alt? msg)
(charm/shift? msg)
```

### Commands

Commands are async functions that produce messages:

```clojure
;; Quit the program
charm/quit-cmd

;; Create a custom command
(charm/cmd (fn [] (charm/key-press :custom)))

;; Run multiple commands in parallel
(charm/batch cmd1 cmd2 cmd3)

;; Run commands in sequence
(charm/sequence-cmds cmd1 cmd2 cmd3)
```

### Styling

```clojure
(require '[charm.core :as charm])

;; Create a style
(def my-style
  (charm/style :fg charm/red
               :bold true
               :padding [1 2]))

;; Apply style to text
(charm/render my-style "Hello!")

;; Shorthand
(charm/styled "Hello!" :fg charm/green :italic true)

;; Colors
(charm/rgb 255 100 50)      ; True color
(charm/hex "#ff6432")       ; Hex color
(charm/ansi :red)           ; ANSI 16 colors
(charm/ansi256 196)         ; 256 palette

;; Borders
(charm/render (charm/style :border charm/rounded-border) "boxed")

;; Layout
(charm/join-horizontal :top block1 block2)
(charm/join-vertical :center block1 block2)
```

## Example: Counter App

```clojure
(ns counter.core
  (:require [charm.core :as charm]))

(def title-style
  (charm/style :fg charm/magenta :bold true))

(def count-style
  (charm/style :fg charm/cyan
               :padding [0 1]
               :border charm/rounded-border))

(defn update-fn [state msg]
  (cond
    ;; Quit on q or Ctrl+C
    (or (charm/key-match? msg "q")
        (charm/key-match? msg "ctrl+c"))
    [state charm/quit-cmd]

    ;; Increment on k or up arrow
    (or (charm/key-match? msg "k")
        (charm/key-match? msg :up))
    [(update state :count inc) nil]

    ;; Decrement on j or down arrow
    (or (charm/key-match? msg "j")
        (charm/key-match? msg :down))
    [(update state :count dec) nil]

    ;; Ignore other messages
    :else
    [state nil]))

(defn view [state]
  (str (charm/render title-style "Counter App") "\n\n"
       (charm/render count-style (str (:count state))) "\n\n"
       "j/k or arrows to change\n"
       "q to quit"))

(defn -main [& args]
  (charm/run {:init {:count 0}
              :update update-fn
              :view view
              :alt-screen true}))
```

## Project Structure

```
charm.clj/
├── src/charm/
│   ├── core.clj          ; Public API
│   ├── program.clj       ; Event loop
│   ├── terminal.clj      ; JLine wrapper
│   ├── message.clj       ; Message types
│   ├── ansi/
│   │   ├── parser.clj    ; ANSI sequence parsing
│   │   └── width.clj     ; Text width calculation
│   ├── input/
│   │   ├── keys.clj      ; Key sequence mapping
│   │   ├── mouse.clj     ; Mouse event parsing
│   │   └── handler.clj   ; Input reading
│   ├── style/
│   │   ├── core.clj      ; Style API
│   │   ├── color.clj     ; Color definitions
│   │   ├── border.clj    ; Border styles
│   │   └── layout.clj    ; Padding, margin, alignment
│   └── render/
│       ├── core.clj      ; Renderer
│       └── screen.clj    ; ANSI sequences
└── test/charm/           ; Tests
```

## Testing

```bash
clojure -M:test
```

## Dependencies

- JDK 22+
- Clojure 1.12+
- [JLine 3](https://github.com/jline/jline3) - Terminal I/O
- [core.async](https://github.com/clojure/core.async) - Async message handling

## License

MIT
