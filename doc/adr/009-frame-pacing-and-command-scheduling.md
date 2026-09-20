# ADR 009: Frame Pacing and Command Scheduling

## Status

Accepted

## Context

The event loop polled instead of waiting, and rendered once per message:

```clojure
(when-let [_ (a/<!! (a/timeout 10))] nil)   ; sleep 10 ms every iteration
(when-let [m (a/poll! msg-chan)] ...)       ; then handle exactly ONE message
```

Three costs follow from those two lines.

**1. Throughput was capped near 100 messages a second.** Every message waited up
to 10 ms before being looked at, and only one was taken per iteration, so *n*
messages took *n* × 10 ms whatever their source. A 300-character paste took three
seconds. `:mouse :all` produces motion events far faster than that drains.
`msg-chan` is `(chan 256)` and the input thread uses `a/put!`, which throws once
1024 puts are pending — so a large enough burst did not just lag, it failed.

**2. `:fps` did nothing.** It was documented, stored on the renderer and never
read. Every single message ran `view` and a full `Display.update`, so the render
cost scaled with message count rather than with how often the screen changes.

**3. Command bodies ran on core.async's dispatch pool.** `:cmd` and `:sequence`
invoked user functions inside `go` blocks. A `go` block must not block, and a
command is precisely where an application does its blocking work — sleeping,
reading a file, calling an HTTP API. The pool has eight threads, so a handful of
concurrent commands starved it and froze the UI. The library's own download
example slept inside a `:cmd`, which made the documented pattern the wrong one.

Separately, three smaller costs sat on the per-frame path: both the viewport and
the renderer copied their whole content before taking a `subvec`, five call sites
reflected, and padding was built with `(apply str (repeat n char))`.

## Decision

### Block, drain, render once a frame

`run-event-loop!` waits for a message rather than sleeping, handles everything
already queued, and renders at most once per `:fps`:

```clojure
(if-not @running?
  (when dirty? (render! ...))          ; the frame still owed on the way out
  (if (and dirty? (>= (- now last-render) frame-ns))
    (render! ...)                      ; frame is due
    (let [wait-ns (if dirty? (- frame-ns (- now last-render)) frame-ns)
          wait-ms (max 1 (quot wait-ns 1000000))   ; never a 0 ms spin
          [m _] (a/alts!! [msg-chan (a/timeout wait-ms)])]
      (recur (or (drain! ctx m) dirty?) last-render))))
```

`handle-msg!` runs `update` for one message and reports whether the frame is
dirty; it does not render. `drain!` applies it to the message in hand and to
everything `poll!` yields behind it. The loop owns the render.

**There is no separate frame-tick channel.** `alts!!` waits against a timeout
sized to the remainder of the current frame, and that timeout doubles as the poll
that notices an externally flipped `running?` — `:running?` is a public option and
`run-async`'s `:quit!` only sets that atom, so a purely blocking take would never
see it. One mechanism, two jobs.

Two invariants are not obvious and are covered by tests:

- **`dirty?` is `or`-ed across iterations, never replaced.** A state change
  followed, inside the same frame, by messages that change nothing must not lose
  its render.
- **A frame still owed is drawn on the way out.** A state change and the quit that
  follows it can land in the same batch. For an alt-screen program this does not
  matter, since cleanup wipes the screen, but an inline program's last view is
  what stays on the terminal — a final "Done!" would otherwise never appear.

### Skip the render when the state is unchanged

A message that leaves the state `identical?` renders nothing: no `view`, no diff.
Identity rather than equality, because it is O(1) and the Elm shape makes it
accurate — an `update` that ignores a message returns the state it was handed. An
equal-but-fresh state still renders, which is the safe direction to err in.

A resize is the exception and forces a render, because the frame changed even if
the application ignored the message.

### Commands run on virtual threads

`execute-cmd!` dispatches bodies to `a/io-thread`, which is a virtual thread where
the runtime has one:

- **Not a `go` block**, for the reason above — commands block by nature.
- **Not `a/thread`**, because that pool is a cached pool of *platform* threads: a
  burst of commands creates one OS thread each and parks them.

The trade-off is explicit: a command doing extended *computation* rather than I/O
holds a carrier thread for the duration, where a platform thread would have been
time-sliced. Commands are documented as the place for blocking I/O; work that is
CPU-bound belongs on a thread the application manages, and `a/thread-call` takes a
`:compute` workload if that ever needs a supported route.

`a/io-thread` conveys the thread binding frame, as `a/thread` and `go` did, so the
color environment still reaches a command that renders styled text. Where the
runtime has no virtual threads — babashka's native image — it falls back to
ordinary threads on its own.

### Every other thread stays a platform thread

charm creates three besides commands, and all three are single and long-lived,
which is the shape virtual threads do nothing for:

- the **input thread** blocks in a JLine read that goes through FFM downcalls. A
  native call pins its carrier, so a virtual thread would hold one of the few
  carriers for no scalability gain. JDK 24's JEP 491 removed `synchronized`
  pinning, not native-call pinning.
- the **shutdown hook** runs once.
- **`run-async`'s program thread** runs the whole event loop.

It also backs off rather than spinning: the input thread used to catch and discard
every exception with no delay, so a reader that failed forever pinned a core. It
now backs off exponentially to 500 ms and, after ten consecutive failures, reports
an error and stops.

### Keep the cheap per-frame wins honest with a build check

The vector copies are gone (`:lines` and `content->lines` are already vectors), the
five reflective call sites are hinted, padding uses `String.repeat`, and three
`recur` args no longer auto-box.

Because none of that is visible in behaviour, `clojure -M:reflection` loads every
namespace under `src/` with `*warn-on-reflection*` on and fails on any warning. It
lives in its own alias rather than the test alias because Clojure honours that flag
only while compiling, and neither `clojure -M` nor the test runner binds it.

## Consequences

### Measured

`run-event-loop!` alone, at 60 fps. The *before* column is arithmetic, not a
measurement: the old loop slept 10 ms per message by construction.

| messages | before | after |
|---|---|---|
| 300 | 3000 ms | 3.0 ms |
| 1000 | 10 s | 5.5 ms |
| 5000 | 50 s | 7.3 ms |

A 300-character burst driven through `run` against a real terminal completes in
161 ms measured around the `run` call, so including terminal setup, the input
thread and teardown. A batch of 50 messages renders once.

Commands, measured by concurrent count:

| concurrent commands | `a/thread` | `a/io-thread` |
|---|---|---|
| 50 | +50 platform threads, 110 ms | +0, 119 ms |
| 500 | +500, 167 ms | +0, 111 ms |
| 2000 | +2000, 188 ms | +0, 146 ms |

Twenty commands sleeping 200 ms each finish in 213 ms. On the eight-thread
dispatch pool the same twenty would have taken three waves.

### Behavioural

- **`:fps` is a ceiling on redraws, not a polling rate.** An idle program wakes
  once a frame to observe `running?` and does nothing else — 60 wakeups a second
  at the default, against 100 for the old sleep loop.
- **Quit latency is bounded by one frame**, since that is the longest the loop
  waits. Flipping `:running?` from outside is noticed within the same bound.
- **The input thread's 100 ms read timeout is unchanged**, so it can take that long
  to notice shutdown. It is a daemon thread, so this never delays exit.
- **A compute-heavy command now occupies a carrier thread**, as above.
- **Under babashka, commands get ordinary threads.** The thread-count test is
  guarded on the runtime actually providing virtual threads, using
  `Thread/activeCount` — it counts the main thread group, which virtual threads
  are not part of, so it measures exactly the thing that must not grow.

### Not addressed here

`msg-chan` stays at 256 with `a/put!` from the input thread. Draining keeps
pending puts near zero in practice, and bracketed paste removes the one case that
could plausibly exceed 1024 — a large paste is now a single message.

What a frame costs, as opposed to how often one happens, is a separate question.
Parsing each line once in `render!` has since landed; carrying a different
representation through the layout stack was tried and rejected, and
[ADR 008](008-styling-currency.md) records why along with what to do instead.
This ADR changes *how often* `view` and `render!` are called, not what they cost.

## Alternatives considered

**A separate frame-tick channel**, the obvious shape for `alts!!`. Rejected: it
needs a ticker running whether or not anything is dirty, and it still does not
solve observing `running?`. A timeout sized to the frame deadline does both jobs
with no extra channel.

**A purely blocking take, with no timeout at all.** Cleanest loop, but `:running?`
is a documented option that external code flips directly, and `run-async`'s
`:quit!` does exactly that. A blocking take would hang until the next message
arrived, which for an idle program could be never.

**Debouncing inside `render!` instead of in the loop.** Rejected: `view` is the
expensive half for a deep layout, so coalescing has to happen before `view` runs,
not after it has produced a string.

**Keeping `a/thread` for commands.** Correct as far as it goes — it is off the
dispatch pool and conveys bindings — but it spends one OS thread per concurrent
command, which the table above prices.

**A bounded executor for commands.** Rejected as premature. `io-thread` needs no
sizing decision and degrades by itself where virtual threads are absent.

## Notes

The benchmarks are reproducible against the private vars:

```clojure
(def run-event-loop! #'charm.program/run-event-loop!)
(def execute-cmd! #'charm.program/execute-cmd!)
```

Fill `msg-chan` with *n* messages plus a `msg/quit`, redefine
`charm.render.core/render!` to count calls, and time the loop. For commands, spawn
*n* sleeping commands and sample `Thread/activeCount` while they are in flight.

The three loop tests worth keeping in mind when changing any of this assert the
non-obvious parts rather than the throughput: that a burst coalesces to one frame,
that the frame a program quits on is drawn, and that a dirty frame survives a
later clean batch.
