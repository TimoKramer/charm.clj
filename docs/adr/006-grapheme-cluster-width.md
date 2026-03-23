# ADR 006: Grapheme Cluster Width via JLine 4 Mode 2027

## Status

Accepted

## Context

Multi-codepoint emoji (ZWJ sequences like 👨‍👩‍👧, flags like 🇩🇪, skin tone modifiers) are measured incorrectly by JLine 3's `columnLength()`, which sums per-codepoint widths. A family emoji reports as 6 cells instead of 2, breaking borders, padding, and truncation.

### Mode 2027

[Mode 2027](https://mitchellh.com/writing/grapheme-clusters-in-terminals) (`CSI ?2027h`) is a terminal protocol that enables grapheme clustering for cursor movement. Supported by Ghostty, Contour, Foot, WezTerm, kitty, and others. Terminals that don't recognize it silently ignore the sequence (standard VT behavior for unknown private modes).

### JLine 4

JLine 4.0.0 added built-in Mode 2027 support:

- `TerminalBuilder.build()` automatically probes for Mode 2027 via DECRQM and enables it if supported
- `columnLength(terminal)` / `columnSubSequence(start, end, terminal)` use grapheme clustering when Mode 2027 is active, fall back to per-codepoint wcwidth otherwise
- `Display.update()` uses the terminal's grapheme mode for its internal screen diffing
- `AbstractTerminal.doClose()` automatically disables Mode 2027 on cleanup

No custom implementation needed — JLine handles probing, enabling, width calculation, and cleanup.

## Decision

Upgrade from JLine 3.30.6 to JLine 4.x and use its built-in Mode 2027 support.

### Threading the terminal

JLine 4's grapheme-aware methods require a `Terminal` reference. We use a dynamic var `charm.ansi.width/*terminal*` to thread it through the width calculation functions:

- `column-length` and `column-sub-sequence` delegate to JLine's terminal-aware overloads when `*terminal*` is bound
- `string-width` and `truncate` use these helpers
- `charm.program/run` binds `*terminal*` for the duration of the event loop

When `*terminal*` is unbound (tests, REPL usage outside a program), the functions fall back to JLine's no-arg `columnLength()` (per-codepoint wcwidth).

## Consequences

### Pros

- Correct width for all emoji types on terminals that support Mode 2027
- Graceful fallback to wcwidth on terminals that don't
- No custom width tables or grapheme clustering logic — all JLine
- JLine's `Display` also benefits from grapheme mode, fixing screen diffing artifacts

### Cons

- JLine 4 requires Java 11+ (not an issue — charm.clj already requires Java 21+ for FFM)
- JLine 4 removed JNA/Jansi providers (not an issue — charm.clj uses FFM)
