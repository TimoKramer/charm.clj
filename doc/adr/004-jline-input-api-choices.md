# ADR 004: JLine Input API Choices

## Status

Accepted

## Context

charm.clj follows the Elm architecture where:
- Input is read as raw keystroke events
- Events are sent to an async message channel
- The `update` function processes events one at a time
- The `view` function renders the entire screen on each update

JLine provides several APIs for terminal input handling:

1. **NonBlockingReader** - Low-level character-by-character reading with timeout support
2. **BindingReader** - Reads complete key sequences, blocks until sequence is recognized
3. **LineReader** - Full line editing with history, completion, and its own display management
4. **KeyMap** - Maps key sequences to objects, provides O(1) lookup via trie structure

## Decision

Use **NonBlockingReader** for raw input and **KeyMap** for escape sequence lookup. Do not use BindingReader or LineReader.

### What We Use

| JLine API | Usage |
|-----------|-------|
| `Terminal` | Terminal creation, raw mode, size detection |
| `NonBlockingReader` | Character-by-character input with timeout |
| `KeyMap` | Escape sequence → key event mapping |
| `InfoCmp$Capability` | Terminal-aware key sequences |
| `Display` | Efficient screen diffing and rendering |
| `AttributedString` | Unicode width calculation, ANSI parsing |

### What We Don't Use

| JLine API | Reason |
|-----------|--------|
| `BindingReader` | Blocks until complete sequence; incompatible with async event loop |
| `LineReader` | Manages its own display; conflicts with Elm `view` function |
| JLine mouse API | Requires detecting mouse prefix first; custom parsing is cleaner |

## Consequences

### BindingReader Rejection

`BindingReader.readBinding()` blocks until it recognizes a complete key sequence or times out. This is problematic for the Elm architecture:

```java
// BindingReader blocks here until complete sequence
Object binding = bindingReader.readBinding(keyMap);
```

In contrast, the async event loop needs to:
1. Read available input with short timeout
2. Parse partial sequences incrementally
3. Return control to the event loop for message processing

The custom approach reads characters individually via `NonBlockingReader`:

```clojure
(defn read-event [terminal & {:keys [timeout-ms]}]
  (let [reader (.reader terminal)
        c (.read reader timeout-ms)]  ; Returns immediately on timeout
    (when (pos? c)
      (parse-input c))))
```

### LineReader Rejection

`LineReader` provides rich line editing (history, completion, syntax highlighting) but:
- Manages its own terminal display via `Display`
- Blocks until the user presses Enter
- Incompatible with Elm's model where `view` renders the entire screen

charm.clj's text-input component provides similar functionality within the Elm architecture, where each keystroke is an event that updates state and triggers a full re-render.

### KeyMap Usage

We use `KeyMap` for escape sequence lookup while handling input ourselves:

```clojure
(defn create-keymap [terminal]
  (let [keymap (KeyMap.)]
    ;; Terminal-aware: uses actual sequences from terminfo
    (when terminal
      (.bind keymap {:type :up} (KeyMap/key terminal Capability/key_up)))
    ;; Fallback: standard sequences for terminals without capabilities
    (.bind keymap {:type :up} "[A")
    (.bind keymap {:type :up} "OA")
    keymap))

;; O(1) lookup via trie
(defn lookup [keymap sequence]
  (.getBound keymap sequence))
```

Benefits:
- Terminal capability awareness (adapts to xterm, vt100, etc.)
- Efficient O(1) lookup via internal trie structure
- Fallback sequences for terminals without capabilities
- Programmatic modifier generation (Shift+Up, Ctrl+Up, etc.)

### Custom Mouse Parsing

JLine's `BindingReader` can parse mouse sequences, but:
- Requires detecting the mouse prefix (`[M` or `[<`) first
- Then delegating to mouse-specific parsing
- This two-phase approach doesn't fit our single-pass reader

Our custom parser handles X10 and SGR mouse formats directly:

```clojure
(defn parse-sgr-mouse [s]
  (when-let [[_ code x y final] (re-find #"\x1b\[<(\d+);(\d+);(\d+)([Mm])" s)]
    {:type :mouse
     :button (parse-button code)
     :x (parse-long x)
     :y (parse-long y)
     :action (if (= final "m") :release :press)}))
```

## Notes

This decision can be revisited if:
- JLine adds non-blocking variants of BindingReader
- The Elm architecture is replaced with a different event model
- Performance profiling shows the custom input handling is a bottleneck
