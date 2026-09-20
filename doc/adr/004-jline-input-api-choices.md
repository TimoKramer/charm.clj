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

## Addendum (2026-09-20): KeyParser evaluated and not adopted

Added after the decision above, which it confirms rather than revises.

`org.jline.terminal.KeyParser` did not exist when this was written.
`KeyParser.parse(String)` is static and non-blocking — so none of the
BindingReader objections above apply to it — and returns a structured `KeyEvent`
carrying a type, an `EnumSet` of modifiers, arrows, function keys and specials. It
overlaps heavily with `charm.input.keys` and `charm.input.keymap`, so it was worth
measuring against them.

Every sequence charm binds was run through it, taken from the keymap's own tables
including the generated xterm modifier combinations:

| | count |
|---|---|
| sequences charm binds | 259 |
| KeyParser agrees | 209 |
| KeyParser disagrees | **0** |
| KeyParser cannot parse | 50 |

**Not adopted, for three independent reasons.**

*It cannot parse 19% of what charm needs.* The 50 gaps are not obscure:

| gap | count | charm needs it for |
|---|---|---|
| `ESC O A/B/C/D/F/H` | 6 | arrows and home/end in application keypad mode, which real terminals send |
| `ESC [ 1~`, `4~`, `7~`, `8~` and their modifier forms | 32 | home and end on VT-style terminals |
| `ESC [ 25~` … `34~` | 8 | F13–F20 |
| `ESC [ I`, `ESC [ O` | 2 | focus reporting, a documented `run` option |
| `ESC [ 200~`, `201~` | 2 | bracketed paste, a documented `run` option |

*It is hardcoded where `KeyMap` is terminal-aware.* charm binds from terminfo
capabilities first and falls back to standard sequences, so it follows whatever
the terminal declares. `KeyParser` has one fixed table.

*It is not available on a supported platform.* `KeyParser` and `KeyEvent` are not
in babashka's image; `KeyMap` is. Input is core rather than test-only, so there is
no way to exclude it there.

**What the spike did buy.** Zero disagreements across 209 sequences is an
independent check on a table charm generates itself, and it is now a standing one:
`charm.input.keyparser-test` (under `test-jvm/`, since `KeyParser` is absent from
babashka) asserts that the two agree wherever both understand a sequence, and that
the gaps above are still gaps. If JLine closes one, that test fails and this
addendum should be revisited.
