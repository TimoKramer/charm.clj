# ADR 008: Styling Currency — What Flows Through the Layout Stack

## Status

Rejected. The premise below does not survive measurement — see
**Why this was rejected** at the end. The alternative it points at is recorded
there too, because the problem it describes is real even though this answer to it
is not.

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

Measured:

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
`*dark-background?*`, which arrived with adaptive colors and never got an ADR of
their own. They are thread-local, and `init` runs before `run` binds them.

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
- `view` output becomes inspectable and diffable data: the event loop's
  skip-render can compare structure instead of strings, and tests can assert on
  spans instead of escape sequences
- Existing tests keep passing — they assert on `style/render`'s ANSI output,
  which is preserved

### Cons

- Touches nine component namespaces (24 `style/render` call sites) plus the
  whole of `layout`, `border` and `overlay`
- Two representations coexist during migration, and `render!` must accept both
- Blocks are verbose to print when debugging
- `truncate` / `strip-ansi` need span-aware equivalents
- A style map inside every span is more allocation than an interned style table
  would be; not optimising that until measured

## Alternatives considered

**`AttributedString` as the currency** — the obvious first instinct. Same
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

1. Parse each line once in `render!`, truncating via `.columnLength` /
   `.columnSubSequence` rather than re-parsing the truncated string. ~27%
   (140 µs → 102 µs), internal, no API surface. Independent of everything below.
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

Step 5 is where fact 3 above gets resolved — the point at which true color
actually reaches the terminal. Until then the dynamic vars stay as the interim
mechanism.

## Why this was rejected

Implemented far enough to measure, then reverted. Two assumptions were wrong.

**1. Re-parsing is not what costs.** The whole argument rests on ANSI parsing per
layer being the expense. It is not: parsing a line and measuring it costs about
the same as measuring it alone, because `columnLength` has to walk the characters
for wide ones and grapheme clusters either way. Removing the parse removes almost
nothing, so neither spans-as-data nor `AttributedString` avoids the real cost.

**2. The win does not survive realistic content.** A block amortises one
measurement across several layout layers. Measured against the string pipeline,
with span widths cached at construction and a single `StringBuilder` for emission:

| operation | strings | blocks |
|---|---|---|
| short styled label | 0.82 µs | 1.08 µs |
| 20-line styled block, no layout | 8.8 µs | 11.5 µs |
| small box: border + padding + width | 9.0 µs | 9.8 µs |
| bordered + padded **20-line** box | 76 µs | **32 µs** |
| width + align + border + margin, 20 lines | 192 µs | **44 µs** |
| `join-horizontal`, 4 blocks of 20×80 | 177 µs | **103 µs** |
| **realistic view: 40 labels + one box** | **42 µs** | **55 µs** |

Blocks win by 2–4× on large content with several layers stacked on it, and lose
on everything small. A view is mostly small: forty short labels and a box came
out 30% slower. Reaching parity on the small cases needed a memoised escape
table, a per-call style cache, an ASCII fast path for width, and a second
non-block path in `render` for styles with no layout — which is the tell. The
caching existed to claw back overhead the representation introduced.

Note also that the cost table in the Context above did not reproduce: the
bordered-and-padded 20-line box measures 76–105 µs here, not 164 µs. And the
shape that actually dominates a frame — many small renders — was never measured
before the decision.

**What is true, and what to do instead.** The real redundancy is not the
representation, it is that the same text is measured many times. One full
`style/render` over *n* lines measures roughly 7*n* times: `pad` twice per line
(once for the widest, once per line), `align-horizontal` once, `apply-border`
twice, `margin` twice. `render!` then measures again.

Every width after the first is arithmetic — padded is `width + left + right`,
bordered is that plus the border. So measure the *n* lines once, carry the
numbers alongside the strings, and roughly 6*n* of the measurements disappear.
That keeps strings as the currency, keeps every public contract, needs no
emission boundary, no styles-as-data and no caching, and is confined to
`style/render` and the layout helpers' internals. It should reach a similar win
to the one blocks got on deep chains, without the machinery.

**Two other things this turned up**, both since fixed independently:

- `toAnsi` without a `Terminal` rewrites Unicode line-drawing characters as
  ASCII, so any *styled* text containing them lost them — a styled border
  rendered as `+--+`. That is a property of JLine's emission, not of the
  currency, and it would have bitten `AttributedString`-as-currency harder: with
  the text inside the object there is nowhere to put the escapes but around it.
- Parsing each line once in `render!` and slicing with `.columnSubSequence`,
  rather than measuring, cutting via a string and parsing the result again, is
  worth about 2× on the render path. That is `AttributedString` used at the
  boundary, where it has a terminal and where parsing genuinely was repeated —
  and it is the part of this ADR's instinct that paid off.

The two motivations that do **not** depend on the performance argument — true
color never reaching the terminal (fact 3 above), and retiring the dynamic vars —
still stand on their own. Both are about the renderer holding the terminal at
emission time, which is where `render!` already builds `AttributedString`s. They
do not need a new representation to be solved.
