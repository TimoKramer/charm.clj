# ADR 006: Remove charm.core Facade Namespace

## Status

Accepted

## Context

charm.clj originally exposed its entire public API through a single `charm.core` namespace that re-exported vars from `charm.message`, `charm.program`, `charm.style.core`, `charm.style.overlay`, and all component namespaces (`charm.components.*`). This facade contained ~130 `def` bindings with no logic of its own.

Problems with this approach:

1. **Naming gymnastics** — To avoid clashes in a single namespace, component functions needed prefixes: `text-input-value`, `list-selected-item`, `timer-timeout`, `progress-complete?`, etc. With direct namespace requires, these become the more natural `text-input/value`, `item-list/selected-item`, `timer/timeout`, `progress/complete?`.

2. **Maintenance burden** — Every new or renamed function in any component required a corresponding update in `charm.core`. This was a frequent source of drift.

3. **Discoverability** — Users couldn't easily tell which namespace a function actually lived in, making source navigation harder.

4. **Unnecessary coupling** — Requiring `charm.core` pulled in every component even when only a few were needed.

## Decision

Remove `charm.core` entirely. Users require the namespaces they need directly:

```clojure
(ns my-app
  (:require
   [charm.components.list :as item-list]
   [charm.components.text-input :as text-input]
   [charm.message :as msg]
   [charm.program :as program]
   [charm.style.core :as style]))
```

The public API namespaces are:

- `charm.program` — `run`, `run-async`, `cmd`, `batch`, `quit-cmd`
- `charm.message` — `key-match?`, `key-press?`, `window-size?`, `ctrl?`, etc.
- `charm.style.core` — `style`, `render`, colors, border aliases, layout joins
- `charm.style.border` — border definitions (`rounded`, `normal`, `thick`, etc.)
- `charm.style.overlay` — `place-overlay`, `center-overlay`
- `charm.components.*` — each component in its own namespace

## Consequences

- Simple examples need 3-4 requires instead of 1. This is standard Clojure practice and improves clarity.
- Function names become shorter and more natural (e.g. `timer/timeout` instead of `charm/timer-timeout`).
- Adding new component functions no longer requires updating a central file.
- This is a breaking change for all existing users of `charm.core`.
