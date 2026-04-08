# ADR 001: Testing Strategy

## Status

Accepted

## Context

charm.clj is a TUI library built on JLine. Testing TUI applications is challenging because they involve terminal I/O, escape sequences, and interactive behavior. We need a testing strategy that:

1. Catches logic bugs in application code
2. Validates terminal I/O integration without requiring a real terminal
3. Ensures visual correctness for critical user flows

## Decision

We adopt a three-tier testing strategy:

### Tier 1: Unit Tests (Pure Functions)

The Elm architecture (`init`/`update`/`view`) makes most application logic pure and directly testable. Unit tests should cover:

- Component `update` functions with synthetic messages
- Component `view` functions with known state
- Input parsing (escape sequences, mouse events)
- Message creation and predicates
- Style and layout computations

**Example:**
```clojure
(ns charm.components.list-test
  (:require [clojure.test :refer [deftest is testing]]
            [charm.components.list :as list]
            [charm.message :as msg]))

(deftest list-navigation-test
  (testing "down arrow moves selection"
    (let [items ["Apple" "Banana" "Cherry"]
          list (list/item-list items)
          [updated _] (list/list-update list (msg/key-press "down"))]
      (is (= 1 (list/selected-index updated)))))

  (testing "view reflects selection"
    (let [list (-> (list/item-list ["A" "B" "C"])
                   (list/select-index 1))
          view (list/list-view list)]
      (is (some #(re-find #">" %) (clojure.string/split-lines view))))))
```

### Tier 2: Integration Tests (Dumb Terminal)

For testing the full input → update → view → render pipeline, use JLine's dumb terminal with `ByteArrayInputStream` and `ByteArrayOutputStream`. This allows:

- Simulating user input via byte streams
- Capturing rendered output
- Testing without a real terminal
- Running in CI environments

**Example:**
```clojure
(ns charm.integration-test
  (:require [clojure.test :refer [deftest is testing]])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [org.jline.terminal TerminalBuilder]))

(defn make-test-terminal
  "Creates a dumb terminal with the given input string."
  [input-str]
  (let [input (ByteArrayInputStream. (.getBytes input-str))
        output (ByteArrayOutputStream.)]
    {:terminal (-> (TerminalBuilder/builder)
                   (.dumb true)
                   (.streams input output)
                   (.build))
     :output output}))

(deftest terminal-input-test
  (testing "terminal can read input bytes"
    (let [{:keys [terminal output]} (make-test-terminal "hello\n")]
      (try
        (let [reader (.reader terminal)
              chars (repeatedly 5 #(.read reader))]
          (is (= [\h \e \l \l \o] (map char chars))))
        (finally
          (.close terminal))))))

(deftest terminal-output-test
  (testing "terminal captures output"
    (let [{:keys [terminal output]} (make-test-terminal "")]
      (try
        (let [writer (.writer terminal)]
          (.write writer "test output")
          (.flush writer)
          (is (= "test output" (.toString output))))
        (finally
          (.close terminal))))))
```

**Guidelines for integration tests:**

- Use `(TerminalBuilder/builder)` with `.dumb true` and `.streams input output`
- Simulate escape sequences by including them in the input stream (e.g., `"\u001b[A"` for up arrow)
- Assert on captured output bytes/strings
- Always close the terminal in a `finally` block
- Keep integration tests focused on I/O boundaries, not business logic

### Tier 3: Visual Tests (VHS)

For critical user flows where visual correctness matters, use [charmbracelet/vhs](https://github.com/charmbracelet/vhs) to record and verify terminal output.

**Example VHS tape (`test/vhs/list-navigation.tape`):**
```
# Test list navigation
Output test/vhs/output/list-navigation.gif

Set Shell "bash"
Set FontSize 14
Set Width 800
Set Height 600

Type "clj -M:examples -m examples.todos"
Enter
Sleep 1s

# Navigate down
Down
Sleep 500ms
Down
Sleep 500ms

# Select item
Enter
Sleep 500ms

# Quit
Type "q"
```

**Guidelines for VHS tests:**

- Place tape files in `test/vhs/`
- Use for smoke tests of critical user journeys
- Keep tapes short and focused
- Run manually or in CI with `vhs < tape.tape`
- Compare output gifs/screenshots for regressions

## Consequences

### Positive

- Unit tests run fast and catch most logic bugs
- Integration tests validate JLine integration without manual testing
- VHS tests provide visual regression safety for key flows
- All tiers can run in CI (no real terminal needed)

### Negative

- Integration tests add JLine dependency to test setup
- VHS tests require the `vhs` tool installed
- Some terminal-specific bugs may only surface in real terminals

## Test File Organization

```
test/
├── charm/
│   ├── components/        # Tier 1: Unit tests for components
│   │   ├── list_test.clj
│   │   └── text_input_test.clj
│   ├── input/             # Tier 1: Unit tests for input parsing
│   │   └── handler_test.clj
│   └── integration/       # Tier 2: Dumb terminal integration tests
│       ├── terminal_test.clj
│       └── program_test.clj
└── vhs/                   # Tier 3: VHS visual tests
    ├── list-navigation.tape
    └── output/
```

## Running Tests

```bash
# Unit + Integration tests
clj -X:test

# VHS visual tests (requires vhs installed)
vhs < test/vhs/list-navigation.tape
```
