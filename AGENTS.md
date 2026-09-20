# Clojure REPL Evaluation

The command `clj-nrepl-eval` is installed on your path for evaluating Clojure code via nREPL.

**Discover nREPL servers:**

`clj-nrepl-eval --discover-ports`

**Evaluate code:**

`clj-nrepl-eval -p <port> "<clojure-code>"`

With timeout (milliseconds)

`clj-nrepl-eval -p <port> --timeout 5000 "<clojure-code>"`

The REPL session persists between evaluations - namespaces and state are maintained.
Always use `:reload` when requiring namespaces to pick up changes.

# API Stability

charm.clj is a library. Its public API is used by applications we cannot see, so a
break is a cost to justify before making it, not a note to add afterwards.

Public is every namespace whose `ns` form does *not* carry `^:no-doc`:
`charm.program`, `charm.message`, `charm.style.core`, `charm.style.border`,
`charm.style.overlay`, `charm.ansi.sanitize` and the components. Everything marked
`^:no-doc` is internal and free to change.

- Prefer adding to the API over changing it. A new option whose default preserves
  today's behaviour breaks nobody.
- A nicer design is not a serious reason. Do not change what an existing function
  returns, or what an existing option means, to tidy something up.
- Serious reasons: a bug that cannot be fixed otherwise, a security problem, or a
  documented feature that never worked. Say which one applies.
- Prefer a break that fails loudly over one that keeps compiling and quietly
  behaves differently.
- Deprecate before deleting when it costs little.
- Record every break in `CHANGELOG.md`, marked **Potentially breaking**, and write
  an ADR when the reasoning is worth keeping.

# ADRs

An ADR records a decision as it was made. Do not rewrite one so that it reads like
what is true now.

- Changing `Status` is part of the lifecycle: Proposed becomes Accepted, or
  Rejected or Superseded when something overtakes it. Say what overtook it.
- Anything learned after the decision goes in an appended, dated addendum, not
  into the original Context or Decision.
- A decision that replaces another gets its own ADR, and the old one points to it.

# Code Style

## Keep it simple

Always pick the simple solution and don't overthink. Later requirements are to be solved later. Don't optimize early.

## Avoid duplication

Prefer a solution that has logic just once and references this solution before you duplicate logic.

## Namespace Requires and Imports

Always use proper `:require` and `:import` declarations in the `ns` form instead of fully qualified names in code.

**Do this:**
```clojure
(ns my.namespace
  (:require
   [clojure.string :as str])
  (:import
   [java.lang Math]))

(str/join "," items)
(Math/abs x)
```

**Not this:**
```clojure
(clojure.string/join "," items)
(java.lang.Math/abs x)
```

# Git

Use [Conventional Commits](https://www.conventionalcommits.org/): `type: description`

Types: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `chore`

Never mention a plan because a plan is ephemeral and internal. Rather propose to write an ADR instead.

# Testing

## Running examples

You can run examples like the file-browser with this command
```bash
clojure -M -m examples.file-browser
```

## Running Tests via REPL

See [ADR 003: Testing Strategy](doc/adr/003-testing-strategy.md) for the full decision record.

## Three-Tier Testing Strategy

1. **Unit tests** for pure functions (Elm architecture: `update`, `view`, input parsing)
2. **Integration tests** using JLine's dumb terminal with `ByteArrayInputStream`/`ByteArrayOutputStream`
3. **Visual tests** with [charmbracelet/vhs](https://github.com/charmbracelet/vhs) for critical user flows

## Integration Test Pattern

When testing terminal I/O, use JLine's dumb terminal:

```clojure
(let [input (java.io.ByteArrayInputStream. (.getBytes "hello\u001b[A"))  ; input with up arrow
      output (java.io.ByteArrayOutputStream.)
      terminal (-> (org.jline.terminal.TerminalBuilder/builder)
                   (.dumb true)
                   (.streams input output)
                   (.build))]
  (try
    ;; test terminal reading/writing here
    (finally
      (.close terminal))))
```

## When to Write Each Type

- **Unit test**: Component logic, message handling, parsing, pure functions
- **Integration test**: Terminal I/O, input reading, output rendering, escape sequences
- **VHS test**: Critical user journeys, visual regressions (sparingly)
