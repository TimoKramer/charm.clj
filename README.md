# charm.clj

![Status](https://img.shields.io/badge/status-beta-blue)
[![Clojars Project](https://img.shields.io/clojars/v/de.timokramer/charm.clj.svg)](https://clojars.org/de.timokramer/charm.clj)
[![GitHub tag](https://img.shields.io/github/v/tag/TimoKramer/charm.clj?label=git%20tag)](https://github.com/TimoKramer/charm.clj/tags)

A Clojure TUI (Terminal User Interface) library inspired by [Bubble Tea](https://github.com/charmbracelet/bubbletea).

Build terminal applications using the Elm Architecture (Model-Update-View pattern) with a simple, functional API. Run them
on the JVM, as a native-image binary or on [babashka](https://babashka.org).

## Status

This library is in beta. The API is stabilizing but breaking changes may still occur. Please let me know if you
encounter any issues.

## Features

- **Elm Architecture** - Simple init/update/view pattern for predictable state management
- **UI Components** - Spinner, text-input, list, paginator, timer, progress, help, viewport, table
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

Add to your `deps.edn`. Click the badges to find the latest version and git tag:

[![Clojars](https://img.shields.io/clojars/v/de.timokramer/charm.clj.svg)](https://clojars.org/de.timokramer/charm.clj)

```clojure
{:deps {de.timokramer/charm.clj {:mvn/version "VERSION"}}}
```

[![GitHub tag](https://img.shields.io/github/v/tag/TimoKramer/charm.clj)](https://github.com/TimoKramer/charm.clj/tags)

```clojure
{:deps {io.github.timokramer/charm.clj {:git/tag "TAG" :git/sha "SHA"}}}
```


## Quick Start

```clojure
(ns myapp.core
  (:require
   [charm.message :as msg]
   [charm.program :as program]
   [charm.style.core :as style]))

(defn update-fn [state msg]
  (cond
    (msg/key-match? msg "q") [state program/quit-cmd]
    (msg/key-match? msg "k") [(update state :count inc) nil]
    (msg/key-match? msg "j") [(update state :count dec) nil]
    :else [state nil]))

(defn view [state]
  (str "Count: " (:count state) "\n\n"
       "j/k to change, q to quit"))

(program/run {:init {:count 0}
              :update update-fn
              :view view})
```

## API Overview

### Running a Program

```clojure
(program/run {:init    initial-state-or-fn
              :update  (fn [state msg] [new-state cmd])
              :view    (fn [state] "string to render")

              ;; Options
              :alt-screen false      ; Use [alternate screen buffer](#alternate-screen-buffer)
              :mouse :cell           ; Mouse mode: nil, :normal, :cell, :all
              :focus-reporting false ; Report focus in/out events
              :fps 60})              ; Frames per second
```

#### Alternate Screen Buffer

Terminals have two screen buffers: the **normal buffer** (where your shell history lives) and the **alternate buffer** (a separate, clean screen).

When you set `:alt-screen true`, your program switches to the alternate buffer on startup and switches back when it exits. This is the same mechanism used by `vim`, `less`, and `htop` — your previous terminal content disappears while the program runs and reappears when it quits, as if nothing happened.

Use `:alt-screen true` for full-screen applications like file browsers, editors, or dashboards. Leave it `false` (the default) for inline programs like prompts or spinners that should leave their output visible in the scrollback after they finish.


### Messages

Messages are maps with a `:type` key. Built-in message types:

```clojure
;; Check message types
(msg/key-press? msg)    ; Keyboard input
(msg/mouse? msg)        ; Mouse event
(msg/window-size? msg)  ; Terminal resized
(msg/quit? msg)         ; Quit signal

;; Match specific keys
(msg/key-match? msg "q")        ; Letter q
(msg/key-match? msg "ctrl+c")   ; Ctrl+C
(msg/key-match? msg "enter")    ; Enter key
(msg/key-match? msg :up)        ; Arrow up

;; Check modifiers
(msg/ctrl? msg)
(msg/alt? msg)
(msg/shift? msg)
```

### Commands

Commands are async functions that produce messages:

```clojure
;; Quit the program
program/quit-cmd

;; Create a custom command
(program/cmd (fn [] (msg/key-press :custom)))

;; Run multiple commands in parallel
(program/batch cmd1 cmd2 cmd3)

;; Run commands in sequence
(program/sequence-cmds cmd1 cmd2 cmd3)
```

### Styling

```clojure
(require '[charm.style.core :as style])

;; Create a style
(def my-style
  (style/style :fg style/red
               :bold true
               :padding [1 2]))

;; Apply style to text
(style/render my-style "Hello!")

;; Shorthand
(style/styled "Hello!" :fg style/green :italic true)

;; Colors
(style/rgb 255 100 50)      ; True color
(style/hex "#ff6432")       ; Hex color
(style/ansi :red)           ; ANSI 16 colors
(style/ansi256 196)         ; 256 palette

;; Borders (require '[charm.style.border :as border])
(style/render (style/style :border border/rounded) "boxed")

;; Layout
(style/join-horizontal :top block1 block2)
(style/join-vertical :center block1 block2)
```

## Examples

Please take a look at the [examples](docs/examples/README.md) in the docs folder and don't hesitate to contribute your examples please.

## Project Structure

```
charm.clj/
├── src/charm/
│   ├── program.clj       ; Event loop & program runner
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
│   │   ├── overlay.clj   ; Overlay placement
│   │   └── layout.clj    ; Padding, margin, alignment
│   ├── components/
│   │   ├── spinner.clj   ; Spinner animations
│   │   ├── text_input.clj; Text input field
│   │   ├── list.clj      ; Scrollable list
│   │   ├── paginator.clj ; Page navigation
│   │   ├── timer.clj     ; Countdown timer
│   │   ├── progress.clj  ; Progress bar
│   │   ├── viewport.clj  ; Scrollable content
│   │   ├── table.clj     ; Table display
│   │   └── help.clj      ; Help key bindings
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
