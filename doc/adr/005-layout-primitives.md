# ADR 005: Layout Primitives

## Status

Proposed

## Context

Building panel-based layouts (e.g., a file browser with a list on the left and details box on the right) exposed three problems in the current layout system:

### 1. `split-lines` is duplicated and drops trailing empty lines

Three private `split-lines` functions exist in `layout.clj`, `border.clj`, and `overlay.clj`. All use `clojure.string/split-lines`, which drops trailing empty strings. This breaks the rendering pipeline when `:height` is used with borders:

```
align-vertical "a\nb" height=5  →  "a\nb\n\n\n"  (correct, 5 lines)
align-horizontal processes it   →  "a\nb"          (3 trailing lines lost)
apply-border wraps it           →  only 4 lines instead of 7
```

The `:height` style option is effectively broken when combined with `:border`.

### 2. No column layout primitive

`join-horizontal` concatenates text blocks side-by-side but doesn't guarantee the total width equals the terminal width. There's no way to say "left column gets the remaining space, right column is 36 chars wide." Building a two-panel layout requires manual line-by-line concatenation with `pad-right`:

```clojure
;; What you have to write today
(defn- two-columns [left right left-width height]
  (let [left-lines (str/split-lines left)
        right-lines (str/split-lines right)]
    (str/join "\n" (map (fn [i]
                          (str (w/pad-right (nth left-lines i "") left-width)
                               (nth right-lines i "")))
                        (range height)))))
```

### 3. No word-wrap utility

Text that exceeds a column width can only be truncated (`charm.ansi.width/truncate`). There's no way to wrap text to fit within a width, which is needed for content panes and description text.

## Decision

Add three focused primitives rather than a full layout engine.

### 1. Shared `split-lines` in `charm.ansi.width`

Move `split-lines` to `charm.ansi.width` as a public function. Use `(str/split text #"\n" -1)` to preserve trailing empty lines. Update `layout.clj`, `border.clj`, and `overlay.clj` to use it.

```clojure
(defn split-lines
  "Split text into lines, preserving trailing empty lines.
   Unlike clojure.string/split-lines, this is a true inverse of
   (clojure.string/join \"\\n\" lines)."
  [s]
  (if (or (nil? s) (empty? s))
    [""]
    (str/split s #"\n" -1)))
```

### 2. `columns` function in `charm.style.layout`

A function that joins pre-rendered text blocks into a fixed-width, fixed-height grid. Each column has a fixed width. Rows are padded/truncated to the specified height.

```clojure
(defn columns
  "Join text blocks into a fixed-width row layout.

   Each column is a map with :content (string) and :width (int).
   The last column's width is optional — it takes whatever space it has.

   Options:
     :height - Total height in lines (default: tallest column)"
  [cols & {:keys [height]}]
  ...)
```

Usage:

```clojure
(columns [{:content file-list-view :width 44}
          {:content details-view}]
         :height 20)
```

This operates at the line level: split each column's content into lines, pad each line to the column's width with `pad-right`, concatenate row by row.

### 3. `word-wrap` in `charm.ansi.width`

Wrap text to fit within a display width, breaking at word boundaries.

```clojure
(defn word-wrap
  "Wrap text to fit within a display width, breaking at spaces.
   Preserves existing line breaks."
  [s width]
  ...)
```

## Consequences

### Pros

- `split-lines` duplication eliminated — single source of truth in `charm.ansi.width`
- `:height` + `:border` works correctly in the style pipeline
- Panel layouts (file browser, split views) become trivial with `columns`
- `word-wrap` enables content-aware text display in fixed-width panes

### Cons

- Changing `split-lines` to preserve trailing empty lines changes behavior for all layout functions — needs careful testing
- `columns` is intentionally simple (fixed widths only) — proportional/flex sizing is left for later if needed

## Notes

- `columns` is not a component — it's a pure layout function that works on pre-rendered strings
- The existing `join-horizontal`/`join-vertical` remain useful for simpler cases where exact width control isn't needed
- A future ADR could propose flex-based sizing if fixed-width columns prove insufficient
