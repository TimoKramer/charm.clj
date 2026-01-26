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
