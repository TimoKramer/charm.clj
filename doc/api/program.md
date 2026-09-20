# Program API

The program module provides the main event loop and command system for TUI applications.

## Running a Program

### run

```clojure
(program/run options)
```

Run a TUI program with the Elm Architecture pattern.

**Options:**

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:init` | fn/value | required | Initial state or `(fn [] [state cmd])` |
| `:update` | fn | required | `(fn [state msg] [new-state cmd])` |
| `:view` | fn | required | `(fn [state] string)` |
| `:alt-screen` | boolean | `false` | Use alternate screen buffer |
| `:mouse` | keyword | `nil` | Mouse mode: `nil`, `:normal`, `:cell`, `:all` |
| `:focus-reporting` | boolean | `false` | Report focus in/out events |
| `:fps` | int | `60` | Frames per second |
| `:hide-cursor` | boolean | `true` | Hide terminal cursor |
| `:sanitize` | boolean | `true` | Drop every escape sequence but SGR styling from the view |
| `:bracketed-paste` | boolean | `false` | Deliver a paste as one [`:paste` message](messages.md#paste-messages) |
| `:ctrl-c` | keyword | `:quit` | `:quit` stops the program; `:message` delivers Ctrl+C to `update` |
| `:overflow` | keyword | `:top` | Which end to drop lines from when the view is too tall |
| `:color-profile` | keyword | `nil` | `:ascii`, `:ansi`, `:ansi256` or `:true-color`; `nil` detects from `TERM`/`COLORTERM` |
| `:dark-background?` | boolean | `nil` | `nil` queries the terminal |

The last two pin the [color environment](styling.md#color-profiles) instead of
detecting it, for a terminal that misreports itself or a test that needs a fixed
environment. Pinning `:dark-background?` also skips the OSC 11 background query,
and with it the probe timeout that a terminal which never answers costs at every
startup.

### Frames and throughput

The loop blocks for messages, handles everything queued, and renders at most once
per `:fps`. So a burst of input - a paste, mouse motion under `:mouse :all`, a
batch of command results - costs one `view` and one terminal diff rather than one
each, and no message waits on a timer before it is handled. A message that leaves
the state `identical?` renders nothing at all.

`:fps` is therefore a ceiling on redraws, not a polling rate: an idle program
does no work beyond waking once a frame to notice that it should still be
running.

### Views taller than the terminal

A view with more lines than the terminal has rows gets trimmed, and `:overflow`
decides which end goes:

- `:top` (default) keeps the **last** lines. Right for an inline program, where
  the newest output is at the bottom.
- `:bottom` keeps the **first** lines. Usually right for a full-screen program —
  otherwise a view one line too tall loses its title, with nothing to say it did.

Neither scrolls. For content that should scroll, use the viewport component
rather than letting the renderer trim.

### Sizing a component to the terminal

Components take a fixed `:height`, and `0` means unbounded. Nothing sizes itself,
so a program that wants a component to fill the screen works out how much room is
left and says so — on the `:window-size` message, which arrives once at startup and
again on every resize.

Measure the rest of the view rather than keeping the number by hand:

```clojure
(defn- header [state]
  (str (style/render title-style "My App") "\n"
       (style/render path-style (:path state)) "\n\n"))

(defn- footer [state]
  (str "\n" (help/short-help-view (:help state))))

(defn- rows-in [s]
  ;; newlines, not str/split-lines, which drops a trailing blank line
  (count (re-seq #"\n" s)))

(defn- body-height [state]
  (- (:term-height state)
     (rows-in (header state))
     (rows-in (footer state))))

(defn update-fn [state msg]
  (cond
    (msg/window-size? msg)
    (let [state (assoc state :term-width (:width msg) :term-height (:height msg))]
      [(assoc state :body (viewport/viewport-set-dimensions
                           (:body state) (:width msg) (body-height state)))
       nil])
    ...))

(defn view [state]
  ;; the same header and footer that were measured, so the two cannot disagree
  (str (header state) (viewport/viewport-view (:body state)) (footer state)))
```

The point is that `view` and the measurement share the same functions. A constant
works until someone adds a line to the view, and then it is wrong with nothing to
say so — `examples/file_browser.clj` carried `chrome-height 5` where its view used
4, and quietly wasted a row on every terminal.

If the view ends up taller than the terminal anyway, `:overflow` above decides
which end survives.

### Ctrl+C

By default Ctrl+C stops the program before `update` sees it, so a program that
does not handle it can still be killed from the keyboard. There is no need to
write a `"ctrl+c"` binding for that.

Pass `:ctrl-c :message` to take it over - to ask for confirmation before
quitting, or to use it for something else. Then it arrives as an ordinary
`"ctrl+c"` key press, and quitting is up to the application:

```clojure
(program/run
  {:init {}
   :update (fn [state msg]
             (if (msg/key-match? msg "ctrl+c")
               [(assoc state :confirming-quit true) nil]
               [state nil]))
   :view view
   :ctrl-c :message})
```

An application that takes Ctrl+C over and then never acts on it cannot be
interrupted from the keyboard.

### Untrusted content

A TUI usually displays data its user did not author - filenames, log lines, HTTP
responses. Escape sequences in that data are instructions to the terminal, not
characters: `ESC c` resets it outright, a carriage return lets content overwrite
what it just drew, and a private `CSI` sequence turns into visible garbage that
also throws off every width calculation downstream.

So the view is sanitized before it is written: everything but SGR styling is
removed, along with every control character except newline and tab. Styled
content is unaffected, and text with nothing to remove is passed through
untouched.

Set `:sanitize false` only for a view that writes its own control sequences, and
then sanitize the untrusted parts of it yourself:

```clojure
(require '[charm.ansi.sanitize :as sanitize])

(sanitize/sanitize log-line)   ; keeps styling, drops the rest
(sanitize/strip log-line)      ; the plain text a terminal would show
```

`charm.style/strip-ansi` is `strip` under another name, so it is safe to reach
for when displaying data from elsewhere.

**Example:**

```clojure
(program/run
  {:init {:count 0}
   :update (fn [state msg]
             (cond
               (msg/key-match? msg "q") [state program/quit-cmd]
               (msg/key-match? msg "up") [(update state :count inc) nil]
               :else [state nil]))
   :view (fn [state]
           (str "Count: " (:count state) "\nPress q to quit"))
   :alt-screen true})
```

### run-async

```clojure
(program/run-async options)
```

Run a TUI program in the background. Accepts the same options as `run` but returns immediately with a handle instead of blocking. Mainly for testing in the REPL.

**Returns** a map with:

| Key | Type | Description |
|-----|------|-------------|
| `:quit!` | fn | Call to stop the program |
| `:result` | promise | Deref to get the final state |

**Example:**

```clojure
;; Start the app in the background
(def app (program/run-async {:init init
                             :update update-fn
                             :view view
                             :alt-screen true}))

;; Stop it from another thread / REPL
((:quit! app))

;; Get the final state (blocks until the app has stopped)
@(:result app)
```

## Commands

Commands are asynchronous operations that produce messages. They're returned from `init` and `update` functions.

### cmd

```clojure
(program/cmd f)
```

Create a command from a function that returns a message.

```clojure
;; Command that sends a message after 1 second
(program/cmd (fn []
               (Thread/sleep 1000)
               {:type :timer-done}))
```

Command bodies run on their own virtual threads, so blocking in one - sleeping,
reading a file, calling an HTTP API - is expected and will not hold up the event
loop or any other command, however many are in flight. Returning `nil` sends no
message, and a command that throws becomes an
[error message](messages.md#quit-and-error-messages).

Commands are for I/O. A command that does heavy computation rather than blocking
occupies a carrier thread while it runs, so that work belongs on a thread the
application manages itself.

### batch

```clojure
(program/batch & cmds)
```

Combine multiple commands into one. All commands run in parallel.

```clojure
(program/batch
  (program/cmd #(do-thing-1))
  (program/cmd #(do-thing-2))
  (program/cmd #(do-thing-3)))
```

### sequence-cmds

```clojure
(program/sequence-cmds & cmds)
```

Run commands in sequence (each waits for the previous to complete).

```clojure
(program/sequence-cmds
  (program/cmd #(step-1))
  (program/cmd #(step-2))
  (program/cmd #(step-3)))
```

### quit-cmd

```clojure
program/quit-cmd
```

A pre-built command that exits the program.

```clojure
(defn update-fn [state msg]
  (if (msg/key-match? msg "q")
    [state program/quit-cmd]
    [state nil]))
```

## Init Function

The `init` option can be:

1. **A value** - used as initial state, no startup command

```clojure
{:init {:count 0}}
```

2. **A function** returning `[state cmd]`

```clojure
{:init (fn []
         (let [[t cmd] (timer/timer-init (timer/timer :timeout 5000))]
           [{:timer t} cmd]))}
```

3. **A function** returning just `state`

```clojure
{:init (fn [] {:count 0})}
```

## Update Function

The update function receives the current state and a message, returning `[new-state cmd]`.

```clojure
(defn update-fn [state msg]
  (cond
    ;; Handle quit
    (msg/key-match? msg "q")
    [state program/quit-cmd]

    ;; Handle key press
    (msg/key-match? msg "up")
    [(update state :count inc) nil]

    ;; Handle custom message
    (= :data-loaded (:type msg))
    [(assoc state :data (:data msg)) nil]

    ;; Ignore unhandled messages
    :else
    [state nil]))
```

## View Function

The view function receives state and returns a string to display.

```clojure
(defn view [state]
  (str "Count: " (:count state) "\n"
       "Press up/down to change, q to quit"))
```

## Mouse Modes

| Mode | Description |
|------|-------------|
| `nil` | No mouse support |
| `:normal` | Basic click events |
| `:cell` | Click and drag events |
| `:all` | All mouse events including motion |

```clojure
(program/run {:init init
              :update update-fn
              :view view
              :mouse :normal})
```

## Focus Reporting

When enabled, focus events are sent when the terminal gains/loses focus.

```clojure
(program/run {:init init
              :update update-fn
              :view view
              :focus-reporting true})

;; In update function
(defn update-fn [state msg]
  (cond
    (msg/focus? msg)
    [(assoc state :focused true) nil]

    (msg/blur? msg)
    [(assoc state :focused false) nil]

    :else
    [state nil]))
```

## Complete Example

```clojure
(ns my-app
  (:require
   [charm.message :as msg]
   [charm.program :as program]))

(defn fetch-data-cmd []
  (program/cmd (fn []
                 ;; Simulate async data fetch
                 (Thread/sleep 1000)
                 {:type :data-loaded
                  :data ["Item 1" "Item 2" "Item 3"]})))

(defn init []
  [{:loading true :data nil}
   (fetch-data-cmd)])

(defn update-fn [state msg]
  (cond
    (msg/key-match? msg "q")
    [state program/quit-cmd]

    (= :data-loaded (:type msg))
    [(assoc state :loading false :data (:data msg)) nil]

    :else
    [state nil]))

(defn view [state]
  (if (:loading state)
    "Loading..."
    (str "Data:\n" (clojure.string/join "\n" (:data state))
         "\n\nPress q to quit")))

(defn -main [& _args]
  (program/run {:init init
                :update update-fn
                :view view
                :alt-screen true}))
```
