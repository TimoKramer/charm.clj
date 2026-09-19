# ADR 008: Styling Currency — What Flows Through the Layout Stack

## Status

Proposed

## Context

Everything in charm's styling stack passes ANSI-encoded strings to the next
layer, and every layer re-parses them.

`style/render` applies text style, then `pad`, then `align-vertical`,
`align-horizontal`, `apply-border`, `margin` — each splitting lines and
re-measuring each one with a fresh `AttributedString/fromAnsi`. `pad` is
representative (`charm/style/layout.clj:85`):

```clojure
line-width (w/string-width line)   ; → fromAnsi, per line, per layer
(str (styled-spaces left-pad bg) line (styled-spaces right-pad-count bg))
```

Measured (P5):

| operation | cost |
|---|---|
| `string-width`, one 80-col styled line | 4.2 µs |
| bordered + padded 20-line box | 164 µs |
| `join-horizontal`, 4 blocks of 20×80 | 218 µs |

Against a 16.7 ms frame at 60fps, a dashboard doing a few joins plus borders is
already a meaningful slice, and **the cost scales with layout depth, not content
size** — each nested layer re-parses what the previous one just serialised.

Three further facts shape the decision:

**1. charm does not emit ANSI itself.** There are exactly three emission sites,
and all three delegate to JLine:

```
src/charm/style/color.clj:271    (.toAnsi (AttributedString. text style))
src/charm/style/core.clj:184     (.toAnsi (AttributedString. % attr-style))
src/charm/style/overlay.clj:58   (.toAnsi (.toAttributedString builder))
```

JLine picks the SGR codes, emits only the delta between adjacent styles, resets
at the right places, and downgrades to the terminal's capabilities. Whatever we
choose, we keep using it for that.

`charm.style.core/attributed-string` already exists as an escape hatch from the
round-trip, and nothing in `layout`, `border` or any component uses it.

**2. Color resolution currently has to be ambient.** `resolve-color` runs four
frames below anything holding the state:

```
(view state) → (list-view (:list state)) → (style/render item-style title) → resolve-color
```

Because `style/render` returns a finished `String`, the "is this terminal
true-color? is the background dark?" decision is made down there, in a function
whose signature is `(style & strings)`. Hence `*color-profile*` and
`*dark-background?*` (ADR-less, added in Phase 0/J1). They are thread-local, and
`init` runs before `run` binds them.

**3. True color has never reached the terminal.** `AttributedString.toAnsi()`
without a terminal argument collapses 24-bit color to the 256 palette, so
`(rgb 255 0 0)` emits `ESC[38;5;196m` even under the `:true-color` profile.
Fixing that needs the `toAnsi(Terminal)` overload — which only the renderer
holds. That change and this one touch the same boundary.

## Decision

**Carry a block of styled spans as plain Clojure data through the layout stack,
and serialise once at `render!`.**

A rendered block is a vector of lines; a line is a vector of spans; a span is
text plus an unresolved charm style map:

```clojure
[[{:text "Hello" :style {:fg (adaptive light dark) :bold true}}
  {:text " world"}]
 [{:text "line 2"}]]
```

Invariants: span text contains no newlines and no escape sequences; `:style` is
optional and `nil` means default; a line's display width is the sum of its
spans' widths.

Consequences of that shape:

- **Width measurement never parses ANSI.** Span text is plain, so
  `w/string-width` on it skips `fromAnsi` entirely and keeps delegating to
  JLine's `.columnLength` for wide chars and grapheme clusters (ADR 007).
- **Layout becomes structural.** `pad` prepends and appends a space span
  carrying the `:bg`; `align-horizontal` appends one; `apply-border` wraps lines
  in border spans. No string surgery, no re-measuring.
- **Emission stays JLine's job.** One new function reduces a block into an
  `AttributedStringBuilder` via `.styled` and calls `toAnsi` — the pattern
  `overlay.clj:58` already uses. This is the only place that resolves colors,
  and it is the only place that needs the terminal.

### Public API

`style/render` keeps returning a `String`, as a thin wrapper that builds a block
and serialises it. The data path becomes the fast path, not the only path.

This is not politeness — it avoids a *silent* break. `AttributedString`
implements `CharSequence` but its `toString` drops all styling:

```
toString: "hi"                      ; styles gone
toAnsi:   "\e[38;5;196mhi\e[0m"
str:      "hi"                      ; (str x) silently renders unstyled
```

`(str (style/render …) "\n" …)` is the dominant idiom in charm's own examples —
`todos`, `talk`, `countdown`, `emojis`, `spinner_demo`, `form` and others all
use it. If `render` returned spans, `(str …)` on them would break loudly, which
is survivable; if it returned an `AttributedString`, every one of those would
keep compiling and quietly lose its colors. Neither is necessary.

`view` may return either a `String` or a block; `render!` dispatches on
`string?`. Components return blocks.

### What happens to the dynamic vars

- `*dark-background?*` is **retired**: adaptive spans stay unresolved until
  emission, which happens in the renderer, which knows the background.
- `*color-profile*` is **retired**: `toAnsi(Terminal)` does capability-based
  downgrade, which is the same call that makes true color work (fact 3).
- The `:color-profile` / `:dark-background?` options on `run` survive unchanged;
  they become renderer configuration instead of dynamic bindings.

## Consequences

### Pros

- Removes the re-parse per layer — the one structural change with real headroom
- Retires both dynamic vars, and with them the thread-locality caveat and the
  `init`-runs-before-`binding` hole
- Lands true color for free, at the same boundary
- `view` output becomes inspectable and diffable data: P3's skip-render can
  compare structure instead of strings, and tests can assert on spans instead of
  escape sequences
- Existing tests keep passing — they assert on `style/render`'s ANSI output,
  which is preserved

### Cons

- Largest change in the plan: nine component namespaces (24 `style/render` call
  sites) plus the whole of `layout`, `border` and `overlay`
- Two representations coexist during migration, and `render!` must accept both
- Blocks are verbose to print when debugging
- `truncate` / `strip-ansi` need span-aware equivalents
- A style map inside every span is more allocation than an interned style table
  would be; not optimising that until measured

## Alternatives considered

**`AttributedString` as the currency** — what P5 originally suggested. Same
layout-stack rewrite, same perf win, less new code, and JLine's own type. But
`AttributedStyle` holds concrete color indices built at construction time, so
adaptive resolution still happens deep in the stack: `*dark-background?*` would
have to stay. Combined with the silent-`str`-break above, it costs the same and
delivers less.

**Status quo, fix the round-trip locally** — use `attributed-string` inside
`style/render` only. Helps one layer; the parse comes back at the next `pad` or
`join`. Doesn't address depth, which is the actual problem.

## Notes

Sequencing — each step is independently shippable:

1. **P4** — parse each line once in `render!`, truncate via `.columnLength` /
   `.columnSubSequence`. ~27% (140 µs → 102 µs), internal, no API surface.
   Independent of everything below.
2. Introduce the block representation and the emission boundary
   (`block->attributed-string`) alongside the current path. No public change.
3. Rewrite `layout` / `border` / `overlay` on blocks; `style/render` becomes
   build-block-then-serialise.
4. `render!` accepts blocks; migrate component views.
5. Retire the dynamic vars; switch emission to `toAnsi(Terminal)` and update the
   true-color assertions in `test/charm/style/color_test.clj`.

**Step 5 can delete `downgrade-color` outright, from JLine 4.4.5 on.**
`toAnsi(Terminal)` rounds into the terminal's `ColorPalette`, which is now sized
from `max_colors`, so passing a terminal gets profile detection *and* the
downgrade — including to 8 and 16 colors, emitted as basic SGR:

```
TERM=xterm           max=8    len=8    toAnsi="\e[31m"
TERM=xterm-16color   max=16   len=16   toAnsi="\e[91m"
TERM=xterm-256color  max=256  len=256  toAnsi="\e[38;5;208m"
```

This needed two fixes upstream, both filed from this work and released in
**4.4.5**:

- [#2256](https://github.com/jline/jline3/issues/2256) — the palette was built in
  `AbstractTerminal`'s constructor, before `parseInfoCmp()` had populated the
  capabilities, so `max_colors` read back `null` and every terminal got the full
  256-entry table. Fixed by
  [#2260](https://github.com/jline/jline3/pull/2260), which adds
  `ColorPalette.reloadFromCapabilities()` and calls it from `parseInfoCmp()` and
  `setEnv()`.
- [#2257](https://github.com/jline/jline3/issues/2257) — `loadPalette()` accepted
  a one-entry all-black palette from a terminal that never answered OSC 4. Fixed
  by [#2261](https://github.com/jline/jline3/pull/2261), which returns an empty
  array when nothing was read and catches `ClosedException`.

`ColorPalette.loadPalette()` is therefore safe to call from 4.4.5 on — a silent
terminal now falls back to the default table and returns `false`. It stays
optional: its value is rounding against the user's actual theme rather than a
default table.

**This makes JLine ≥ 4.4.5 a hard floor** for step 5, which matters because
consumers can override the version. Before 4.4.5 the palette is always 256
entries, `toAnsi(Terminal)` emits a 256-color escape to a `TERM=xterm` terminal,
and charm's own `:ansi` branch is the only thing doing that downgrade.

Note that `charm.render.core` already renders through JLine's `Display`, which
holds `terminal.getPalette()` and emits via `toAnsiBytes(…, ColorPalette, …)`.
So from 4.4.5 the write-time rounding is live on the current code, before any of
this ADR is implemented: on an 8-color terminal charm's `\e[91m` now reaches the
terminal as `\e[31m`. That is a second reason the two downgrades should collapse
into one.

Step 5 is where PLAN.md's premise 1 gets resolved. Until then the dynamic vars
stay as the interim mechanism, as recorded in J1.
