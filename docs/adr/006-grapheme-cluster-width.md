# ADR 006: Grapheme Cluster Width via JLine 4 Mode 2027

## Status

Accepted

## Context

### The problem

Unicode text that looks like a single character on screen can be made up of multiple codepoints. The classic `wcwidth` approach (assigning a width to each codepoint and summing) breaks for these multi-codepoint sequences because it doesn't understand that the terminal renders them as one grapheme cluster.

Examples:

| Sequence | Codepoints | wcwidth sum | Actual terminal width |
|---|---|---|---|
| 👨‍👩‍👧 (family) | `👨` + ZWJ + `👩` + ZWJ + `👧` (5 codepoints) | 2+0+2+0+2 = **6** | **2** |
| 🇩🇪 (flag) | `🇩` + `🇪` (2 regional indicators) | 2+2 = **4** | **2** |
| 👋🏽 (wave + skin tone) | `👋` + `🏽` (2 codepoints) | 2+2 = **4** | **2** |

When TUI code uses the wrong width, everything that depends on character measurement breaks:

- **Borders and boxes** — right edges are misaligned because the emoji occupies fewer cells than calculated
- **Padding** — `pad-right` adds too few spaces (it thinks the string is wider than it is)
- **Truncation** — `truncate` cuts too early, clipping visible text to fit a phantom width
- **Screen diffing** — JLine's `Display.update()` miscalculates cursor positions, causing render artifacts (ghost characters, misplaced redraws)

JLine 3's `columnLength()` uses per-codepoint wcwidth, so it produces the wrong values for all the cases above.

### Why this is hard

The fundamental issue is that the **terminal**, not the application, decides how to cluster codepoints into graphemes. Different terminals use different Unicode versions and clustering rules. An application that hardcodes a width table will always be wrong on some terminals.

Mode 2027 solves this by letting the terminal tell the application "I support grapheme clustering"—meaning the terminal itself treats each grapheme cluster as a single unit for cursor movement. Once the application knows the terminal clusters correctly, it can count each cluster as width 2 (for emoji) or 1 (for text), rather than summing per-codepoint widths.

### Mode 2027

[Mode 2027](https://mitchellh.com/writing/grapheme-clusters-in-terminals) (`CSI ?2027h`) is a terminal protocol that enables grapheme clustering for cursor movement. The protocol works as follows:

1. **Probe**: the application sends `CSI ?2027$p` (DECRQM — request mode) to ask the terminal if it supports grapheme clustering
2. **Response**: a supporting terminal replies with `CSI ?2027;1$y` (mode set) or `CSI ?2027;2$y` (mode reset but recognized). A non-supporting terminal either doesn't reply or returns an unrecognized-mode response
3. **Enable**: if supported, the application sends `CSI ?2027h` to activate grapheme clustering
4. **Effect**: the terminal now moves the cursor in grapheme-cluster units. A family emoji `👨‍👩‍👧` occupies 2 cells and the cursor advances by 2, not by 6
5. **Disable**: on exit, the application sends `CSI ?2027l` to restore default behavior

Supported by Ghostty, Contour, Foot, WezTerm, kitty, and others. Terminals that don't recognize it silently ignore the sequence (standard VT behavior for unknown private modes), so enabling it is always safe.

### JLine 4

JLine 4.0.0 added built-in Mode 2027 support, handling the entire lifecycle:

- **Probing**: `TerminalBuilder.build()` automatically sends the DECRQM query and parses the response to detect Mode 2027 support
- **Enabling**: if the terminal supports it, JLine enables Mode 2027 automatically during terminal construction
- **Width calculation**: `columnLength(terminal)` and `columnSubSequence(start, end, terminal)` are new overloads that accept a `Terminal` parameter. When the terminal has Mode 2027 active, these methods use grapheme clustering (each cluster = 1 or 2 cells). When it doesn't, they fall back to per-codepoint wcwidth — same as JLine 3
- **Screen diffing**: `Display.update()` uses the terminal's grapheme mode for its internal cursor position tracking, fixing render artifacts with emoji
- **Cleanup**: `AbstractTerminal.doClose()` automatically sends `CSI ?2027l` to disable the mode, so the terminal is left in a clean state

No custom implementation needed — JLine handles probing, enabling, width calculation, and cleanup.

## Decision

Upgrade from JLine 3.30.6 to JLine 4.x and use its built-in Mode 2027 support.

### Threading the terminal

JLine 4's grapheme-aware `columnLength(terminal)` and `columnSubSequence(start, end, terminal)` require a `Terminal` reference to check whether Mode 2027 is active. But width functions like `string-width` and `truncate` are called deep in the call stack—inside `view` functions, inside component rendering, inside layout helpers—where passing a terminal explicitly would thread it through every function signature.

We use a dynamic var `charm.ansi.width/*terminal*` to avoid this:

```clojure
(def ^:dynamic *terminal* nil)

(defn column-length [^AttributedString attr-s]
  (if *terminal*
    (.columnLength attr-s ^Terminal *terminal*)   ; grapheme-aware
    (.columnLength attr-s)))                       ; wcwidth fallback
```

- `charm.program/run` binds `*terminal*` once at the top of the event loop via `binding`
- All width calculations within the event loop automatically use the grapheme-aware overload
- `column-length`, `column-sub-sequence`, `string-width`, `truncate`, `pad-right`, `pad-left` all benefit without any signature changes

When `*terminal*` is unbound (unit tests, REPL usage outside a running program), the functions fall back to JLine's no-arg `columnLength()` which uses per-codepoint wcwidth. This means tests don't need a terminal, and the library degrades gracefully.

## Consequences

### Pros

- Correct width for all emoji types on terminals that support Mode 2027
- Graceful fallback to wcwidth on terminals that don't
- No custom width tables or grapheme clustering logic — all JLine
- JLine's `Display` also benefits from grapheme mode, fixing screen diffing artifacts

### Cons

- JLine 4 requires Java 11+ (not an issue — charm.clj already requires Java 21+ for FFM)
- JLine 4 removed JNA/Jansi providers (not an issue — charm.clj uses FFM)
