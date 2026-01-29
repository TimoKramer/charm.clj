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

# Code Style

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

# Testing

## Running Tests via REPL

See [ADR 003: Testing Strategy](docs/adr/003-testing-strategy.md) for the full decision record.

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
