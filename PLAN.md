# charm.clj — Improvement Plan

Findings from a full review of `src/` (~5.5k lines), the examples, docs and CI,
plus a review of what changed in JLine since the project started (3.30.6 →
4.3.1, the current 4.x tip). Everything here was verified by running it:
benchmarks, escape-injection probes, the test suite (152 tests / 940 assertions,
all passing at time of review), and `javap` inspection of the local
`jline-terminal-4.3.1.jar`.

Ordered by impact within each section. Each item lists the evidence, the
location, and a concrete suggestion.

---

## Top priorities

1. ~~**Background color detection / adaptive colors (J1)**~~ — **done**, together
   with U1. JLine 4 exposes `getDefaultBackgroundColor()` (OSC 10/11 query);
   wired up to offer light/dark-adaptive styling, with the dormant
   `detect-color-profile` / `downgrade-color` code wired in alongside.
2. ~~**Sanitize untrusted content before it reaches the terminal**, and make
   `strip-ansi` honest~~ — **done** (S1, S2, S3). The OSC 52 clipboard write
   this was written for stopped being reachable through the render path when
   Phase 0 bumped JLine to 4.4.5, whose `fromAnsi` drops OSC and DCS; what was
   left, and is now fixed, is `ESC c` resetting the terminal, CR hiding content,
   private CSI arriving as visible text, and the window title. See S1 for the
   measurements.
3. ~~**Rewrite the event loop** to block on the channel, drain, and render once per
   frame~~ — **done** (P1, P2, P3). 300 messages went from 3 s to 3 ms, and
   `:fps` is now the redraw ceiling it was documented to be.
4. ~~**Fix `:fg 240`, `:strikethrough` and `"pgup"`**~~ — **done** (U1, U2, U3).
   Three small bugs that made documented features, the library's own default
   styling, and Page Up/Down silently do nothing. U3 turned out to cover `"esc"`
   as well, which was dead in every example in the repository.

---

## 1. Performance

### P1 — Event loop caps the library at ~100 messages/second — **done**

`src/charm/program.clj:242`

```clojure
(when-let [_ (a/<!! (a/timeout 10))] nil)   ; sleep 10ms every iteration
(when-let [m (a/poll! msg-chan)] ...)       ; then handle exactly ONE message
```

Every keystroke, mouse-motion event and command result waits up to 10 ms, and
only one is drained per tick. Pasting 300 characters takes 3 seconds. `:mouse
:all` generates motion events far faster than they drain. `msg-chan` is
`(chan 256)` and the input thread uses `a/put!`, which throws once 1024 puts are
pending.

**Suggestion:** block on `a/alts!!` over `msg-chan` (plus a frame-tick channel),
then drain everything available before rendering.

**Resolved**, as suggested, with one deviation: there is no separate frame-tick
channel. `alts!!` waits on `msg-chan` against a timeout sized to the remaining
time in the current frame, which doubles as the poll that notices an externally
flipped `running?` — `run-async`'s `:quit!` only sets that atom, so a purely
blocking take would not see it.

Measured on `run-event-loop!` alone, at 60 fps:

| messages | before | after |
|---|---|---|
| 300 | ~3000 ms | 3.0 ms |
| 1000 | ~10 s | 5.5 ms |
| 5000 | ~50 s | 7.3 ms |

End to end through `run`, a 300-character burst plus terminal setup and teardown
is 161 ms.

### P2 — `:fps` does nothing — **done**

Documented in the README and in `run`'s docstring, stored at
`src/charm/render/core.clj:29`, never read anywhere. There is no frame
coalescing: every single message triggers a full `view` + `render!`.

**Suggestion:** fold into P1 — drain all pending messages → run `update` for each
→ render once per frame tick. One change fixes both the input ceiling and the
render amplification.

**Resolved** as suggested. `handle-msg!` no longer renders; it returns whether
the frame is dirty, and the loop owns the render.

Two things the benchmark caught that the plan did not anticipate:

1. **The frame a program quits on was being dropped.** Rendering only on the
   frame tick means a state change and the quit that follows it can land in the
   same batch, and the loop then exits with the render still owed. The old
   per-message render always drew it. For an alt-screen program it does not
   matter — cleanup wipes the screen — but an inline program's last view is what
   stays on the terminal, so "Done!" would never appear. The loop now draws a
   frame it still owes on its way out.
2. **A dirty frame must not be forgotten by a later clean batch.** `dirty?` has
   to be `or`-ed across iterations, not replaced: a state change followed, inside
   the same frame, by messages that change nothing would otherwise lose its
   render entirely. Both are covered by tests.

### P3 — No skip-render on unchanged state — **done**

`src/charm/program.clj:263-268`. A message that leaves state identical still
re-runs `view` and `Display.update`.

**Suggestion:** `(when-not (identical? new-state old-state) (render! ...))`.

**Resolved:** the two copies of update-then-render in the event loop collapsed
into `handle-msg!`, which renders only when `update` returned a state that is
not `identical?` to the old one. A resize passes `:force-render? true`, since
the frame changed even when the application ignored the message.

### P4 — Every line is ANSI-parsed twice per frame — **done**

`render!` calls `scr/truncate-line` (→ `w/string-width` →
`AttributedString/fromAnsi`) and then `AttributedString/fromAnsi` again on the
result.

Measured on 40×80 styled lines: **140 µs → 102 µs** (~27%) by parsing once and
using `.columnLength` / `.columnSubSequence`.

**Suggestion:** parse each line to an `AttributedString` once in `render!` and
truncate on that.

**Resolved**, and it was three parses, not two: measure (`string-width`), cut
(`truncate`, which parses again), then parse the cut result to hand to `Display`.

Measured on 40 lines of 80 styled columns, better than the estimate:

| per frame | before | after |
|---|---|---|
| lines fit the width | 165.1 µs | 82.6 µs |
| lines need cutting | 312.1 µs | 116.2 µs |

**It was also a correctness bug, which the item did not mention.** `str` on an
`AttributedString` returns the plain text, so `w/truncate` discarded the styling
`columnSubSequence` had just preserved — `(w/truncate "\e[31mred-and-long\e[0m" 5)`
came back as `"red-a"`, no escapes. Every line too wide for its container
rendered unstyled: in the renderer, in viewport lines, in table cells, and for
anyone calling the public `style/truncate`. `w/truncate` now serialises with
`.toAnsi`; the tail stays unstyled as before.

`screen/truncate-line` had exactly one caller and went with it.

### P5 — The styling stack round-trips through ANSI strings at every layer

`style/render` → `pad` → `align-*` → `apply-border` → `margin`, each re-measuring
each line with a fresh `fromAnsi`.

| operation | cost |
|---|---|
| `string-width`, one 80-col styled line | 4.2 µs |
| bordered + padded 20-line box | 164 µs |
| `join-horizontal`, 4 blocks of 20×80 | 218 µs |

A dashboard doing a few joins plus borders is already a meaningful slice of a
16.7 ms frame, and the cost scales with layout depth rather than content size.

The escape hatch already exists — `src/charm/style/core.clj:161`
`attributed-string` — but nothing in `charm.style.layout`, `charm.style.border`
or any component uses it.

**Suggestion:** stop round-tripping — carry one representation through the layout
stack and serialise only at `render!`. This is the one structural change with
real headroom; do it after P1–P4, which are cheap and independent.

Which representation is [ADR 008](adr/008-styling-currency.md): spans as plain
Clojure data rather than `AttributedString`, because `AttributedStyle` holds
already-resolved colors and so would keep `*dark-background?*` alive, and
because `(str attributed-string)` drops styling *silently* — the dominant idiom
in charm's own examples. Same rewrite either way; the data version additionally
retires both dynamic vars and lands true color at the same boundary. The ADR
carries the sequencing.

### P6 — Blocking work runs on core.async's dispatch pool — **done**

`src/charm/program.clj:73-90`. `:cmd` and `:sequence` invoke user functions
inside `go` blocks. `go` blocks must not block; the pool is 8 threads.

`doc/examples/src/examples/download.clj:38` calls `Thread/sleep 50` inside a
`:cmd` — i.e. the documented pattern is exactly the wrong one. A handful of
concurrent sleeping or IO-bound commands starves the pool and freezes the UI.

**Suggestion:** use `a/thread` for `:cmd` and `:sequence` bodies.

**Resolved**, and then taken one step further to `a/io-thread`, which is a
virtual thread where the runtime has them. The per-command body is shared by a
`run-cmd-fn!` helper rather than written twice.

Verified: twenty commands sleeping 100 ms each finish in ~100 ms rather than the
~300 ms that eight dispatch threads would force. Also verified that both
`a/thread` and `a/io-thread` convey the thread binding frame, so
`*color-profile*` and `*dark-background?*` still reach a command that renders
styled text — `go` did that too, and losing it would have been a silent
regression until Phase 4 retires those vars.

`download.clj`'s `Thread/sleep` inside a `:cmd` is now the right pattern rather
than the wrong one, so the documented example needed no change.

**Why `io-thread` rather than `thread`:** `a/thread`'s pool is a cached pool of
platform threads, so a burst of commands creates one OS thread each and parks
them for a minute.

| concurrent commands | `a/thread` | `a/io-thread` |
|---|---|---|
| 50 | +50 platform threads, 110 ms | +0, 119 ms |
| 500 | +500, 167 ms | +0, 111 ms |
| 2000 | +2000, 188 ms | +0, 146 ms |

The trade-off is that a command doing extended *computation* now holds a carrier
thread for the duration where a platform thread would have been time-sliced.
Commands are for I/O; if a compute-heavy command ever needs its own pool,
`a/thread-call` takes a `:compute` workload.

`a/io-thread` exists in babashka's bundled core.async too, and falls back to
ordinary threads there, since a native image has no virtual threads — so the
platform-thread test is guarded on the runtime actually providing them.

**Why not virtual threads anywhere else.** The other three threads charm creates
are all single, long-lived ones, where virtual threads buy nothing:

- the **input thread** blocks in a JLine read, which goes through FFM downcalls;
  a native call pins its carrier, so a virtual thread would hold one of the few
  carriers permanently for no scalability gain (JDK 24's JEP 491 fixed
  `synchronized` pinning, not native-call pinning)
- the **shutdown hook** runs once
- **`run-async`'s program thread** is one thread running the whole event loop

So "we are on a modern JVM, use virtual threads" applies to commands and to
nothing else here.

### P7 — Full-vector copies per frame — **done**

`src/charm/components/viewport.clj:255` and `src/charm/render/core.clj:187` both
do `(subvec (vec lines) …)`. `lines` is already a vector from `str/split-lines`,
so `(vec …)` copies the entire content on every render. A viewport over a large
log is O(total lines) per frame instead of O(visible).

**Suggestion:** drop the `(vec …)`.

**Resolved:** dropped in both places. `:lines` comes from `split-content` and
the renderer's from `content->lines`; both are already vectors.

### P8 — Input thread can busy-spin — **done**

`src/charm/program.clj:143-146` catches and discards every exception inside
`(while @running? …)` with no backoff. A persistently failing reader pins a core
at 100%.

**Suggestion:** count consecutive failures, back off, and bail out after a
threshold.

**Resolved:** exponential backoff from 2 ms, capped at 500 ms, and after ten
consecutive failures the thread sends an `:error` message and stops, so the
program fails visibly instead of looping. A successful read resets the count,
which keeps the expected single failures during shutdown harmless.

The event-to-message conversion came out of the loop into `event->msg` while
here — it was a 40-line `cond` nested five levels deep inside the thread body,
and the backoff needed another level. It is now unit-testable, which is how the
`nil`-versus-`false` modifier wart surfaced.

### P9 — Reflection on hot paths — **done**

- `src/charm/style/overlay.clj:48,51,57` — per line, per overlay
- `src/charm/input/keymap.clj:134`
- `src/charm/render/core.clj:199`

Also `w/pad-right` / `w/pad-left` build padding via `(apply str (repeat n char))`,
called per line per frame throughout layout.

**Suggestion:** add type hints; enable `*warn-on-reflection*` in the test alias so
regressions get caught.

**Resolved:** all five sites hinted — `.append` takes `^CharSequence`,
`KeyMap.bind` resolves to the `(Object, CharSequence)` overload, and the
`ArrayList` constructor to `(Collection)`. `w/repeat-char` builds padding with
`String.repeat` and is used by `pad-right`, `pad-left` and the overlay.

The regression guard is a `:reflection` alias rather than the test alias:
Clojure honours `*warn-on-reflection*` only while compiling, and neither
`clojure -M` nor the test runner binds it, so `dev/reflection.clj` loads every
namespace under `src/` with the flag on and exits non-zero if anything warned.
It is wired into `bb ci` and the workflow. Since it captures `*err*` wholesale
it also catches auto-boxing warnings, which turned up three `recur` args in
`charm.ansi.parser`, `charm.style.border` and `charm.components.help`; those are
coerced with `long`.

---

## 2. Security

The threat model that matters here is *a TUI rendering data its user didn't
author* — filenames, log lines, HTTP responses. That is precisely what the
file-browser and download examples do.

### S1 — Untrusted content reaches the terminal as OSC and DCS escapes — **done**

Verified end to end: these survive both `strip-ansi` and the
`AttributedString/fromAnsi` render path unchanged.

| injected into displayed text | result |
|---|---|
| `ESC ] 52 ; c ; <base64> BEL` | **writes attacker data into the user's system clipboard** (iTerm2, kitty, foot, wezterm, tmux with `set-clipboard`) |
| `ESC ] 2 ; … BEL` | rewrites the terminal window/tab title |
| `ESC ] 8 ; ; <url> BEL` | injects a hyperlink — visible text says one thing, the link goes elsewhere |
| `ESC P … ESC \` (DCS) | passes through untouched |
| `ESC [ 2K CR` | erases the line and rewrites it — content can hide what it just displayed |

Highest-severity item. `AttributedString/fromAnsi` is an SGR parser, not a
sanitizer, and it is currently the only thing between untrusted input and the
terminal.

**Suggestion:** add `charm.ansi/sanitize` that keeps SGR and drops
OSC/DCS/APC/PM/SOS, C1, and C0 other than `\n` / `\t`. Apply it in `render!` by
default, with an opt-out for content the app authored itself.

**Resolved, and the table above is out of date — half of it was fixed by the
JLine bump in Phase 0, not by this.**

`charm.ansi.sanitize/sanitize` does what the suggestion says, `strip` also drops
SGR, and `strip-controls` drops control characters outright for strings that go
inside a sequence charm writes itself. `render!` applies `sanitize` unless the
renderer was built with `:sanitize false`, which `run` exposes as an option. A
single scan, and the argument is returned unchanged when nothing was removed, so
plain and already-styled text cost no allocation.

**The premise that needs recording:** the table was measured against JLine
4.3.1. Phase 0 moved to 4.4.5 for the `ColorPalette` fixes, and 4.4.5's
`AttributedString/fromAnsi` drops OSC, DCS and standard CSI. Verified by running
the same probes under both versions:

| injected | 4.3.1 `fromAnsi` | 4.4.5 `fromAnsi` |
|---|---|---|
| `ESC ] 52 ; c ; <b64> BEL` | passes through, width 20 | dropped, width 8 |
| `ESC ] 2 ; … BEL` | passes through | dropped |
| `ESC P … ESC \` (DCS) | passes through | dropped |
| `ESC [ 10;10 H` | dropped | dropped |

So on the version charm now ships, the clipboard write was already unreachable
through `render!` before this change. It was **not** unreachable through
`set-window-title` (S4), and it stays reachable for anyone pinning an older
JLine.

What 4.4.5 still lets through, which is what the sanitizer is actually buying:

| injected | 4.4.5 `fromAnsi` result |
|---|---|
| `ESC c` (RIS) | **passes through — resets the terminal** |
| `CR` | passes through; `visible ESC[2K CR hidden` measures 12 for 6 cells |
| `BEL`, backspace | pass through |
| `ESC [ ?1049h` (private CSI) | **mangled into the visible text `1049h`**, width 5 |
| C1 `0x80`-`0x9f` | pass through |

The first is a live terminal-reset vector, the second and fourth are content
hiding plus a wrong width for everything downstream.

### S2 — `strip-ansi` doesn't strip — **done**

`src/charm/ansi/width.clj:13`. It is the function anyone displaying untrusted
data will reach for, and it leaves everything in S1 intact. It also *mangles*
private CSI rather than removing it: `ESC [ ?1049h` comes out as the visible
text `1049h`.

**Suggestion:** make it exhaustive as part of S1.

**Resolved:** it is `sanitize/strip`. The `ESC [ ?1049h` → `1049h` mangling was
still there on 4.4.5, so this half of S2 was real as written.

### S3 — Width measurement is wrong for unrecognised escapes — **done**

`"file" ESC "]2;PWNED" BEL ".txt"` measures **14** but displays **8**. Every
downstream decision — truncate, pad, border, join, overlay — is then computed
from a wrong number, so injected content also breaks the frame.

**Suggestion:** falls out of S1 once sanitisation happens before measurement.

**Resolved, but not by itself** — sanitising in `render!` is too late, because
every layout decision is made above it. `string-width` and `truncate` sanitize
their argument first instead.

The example in the heading no longer reproduces on 4.4.5 (`fromAnsi` measures
that OSC as 8), but `visible ESC[2K CR hidden` still measured 12 for 6 cells and
`ESC[?1049h` still measured 5 for 0, so the class of bug was intact.

### S4 — `set-window-title` doesn't escape its argument — **done**

`src/charm/render/screen.clj:52`. A BEL or ESC in the title terminates the OSC
early and lets the caller's string open a new one. Verified: title
`"hi" ESC "]52;c;ZXZpbA=="` emits two OSCs, the second a clipboard write.

`copy-to-clipboard` (`src/charm/render/screen.clj:59`) is likewise unbounded —
terminals cap OSC 52 payloads and truncate silently.

**Suggestion:** strip control characters from the title; document and enforce a
size cap on the clipboard payload.

**Resolved:** the title goes through `sanitize/strip-controls`, so it cannot end
charm's OSC or open one of its own. This was the one place where the OSC 52
clipboard write stayed reachable on 4.4.5, since it does not go through
`fromAnsi`. `copy-to-clipboard` throws an `ex-info` naming the size above
`max-clipboard-bytes` (74994, tmux's limit and the smallest of the common ones).

### S5 — Signal handlers are registered and never restored — **done**

`src/charm/program.clj:228,233`. `run`'s `finally` restores terminal attributes
but not the WINCH/INT handlers. `Signals/register` returns the previous handler;
nothing captures it.

In a REPL — which the README actively promotes — Ctrl+C stays hijacked after a
program exits, feeding a closure that puts into a closed channel, so SIGINT is
silently swallowed for the rest of the session.

**Suggestion:** capture the return value of `Signals/register` and restore it in
the `finally`.

**Resolved, but not via J6** — see J6 for why `Terminal.handle` could not be
used. `charm.terminal/handle-signal` and `restore-signal!` wrap
`Signals/register` / `Signals/unregister`, `run` keeps what it displaced and puts
it back in the `finally`, before the terminal closes under it.

Verified against a real program: install a marker handler, displace it once to
learn the native handler object JLine wrapped it in, run a program to completion,
and check that object is the one installed afterwards. Note that
`Signals/register` returns JLine's *native* handler, not the `Runnable` handed
to it, so comparing against the `Runnable` reports a false failure.

### S6 — No shutdown hook — **done**

`finally` covers exceptions but not `System/exit` or SIGTERM. The terminal is
left in raw mode, cursor hidden, alt-screen active.
`doc/examples/src/examples/timer.clj:198` calls `System/exit 1` from inside a
running program, so this is reachable from the repo's own code.

**Suggestion:** register a shutdown hook that restores attributes, shows the
cursor and exits the alt screen.

**Resolved:** `run` registers one and removes it again in the `finally`, so
repeated runs do not accumulate hooks. Both it and the `finally` call the same
`restore-terminal!`, which tolerates running twice and after the terminal is
gone. Verified by reproducing the `System/exit` path from
`examples/timer.clj:198`: the restore sequences are emitted and the exit code
survives.

`restore-terminal!` is also two lines shorter than the cleanup it replaced:
`render/stop!` already disables the mouse, focus reporting, shows the cursor and
leaves the alternate screen, so the `finally`'s own `disable-mouse!` and
`disable-focus-reporting!` were emitting every sequence twice.

### S7 — CI supply chain — **mostly done**

`.github/workflows/ci.yml`:

- `bash <(curl https://raw.githubusercontent.com/babashka/babashka/master/install)`
  executes an unpinned script from a moving branch.
- Actions are pinned by tag, not commit SHA — including third-party
  `DeLaGuardo/setup-clojure@13.4`.
- The `release` job holds `CLOJARS_PASSWORD` with `contents: write` and fires on
  every push to `main`.
- `git config set user.email GIT_COMMITTER_EMAIL` is missing a `$` — it sets the
  literal string, not the secret.

**Suggestion:** pin actions by SHA, pin the babashka installer to a tag, and fix
the `$`. Consider gating release on a tag rather than every push to `main`.

**Resolved, except the release gate.** The `$` was fixed in Phase 1. All six
actions are now pinned by commit SHA with the tag in a trailing comment, and the
babashka installer is fetched from the `v1.13.223` tag rather than from `master`.
The dev build that installer then downloads is still a moving target, which is
unavoidable while charm needs JLine 4.4.5 and babashka carries it only there.

**Still open:** the `release` job holds `CLOJARS_PASSWORD` with `contents: write`
and fires on every push to `main`. Gating it on a tag changes how releases are
cut, so it is the maintainer's call rather than a mechanical fix.

---

## 3. Usability

### U1 — `:fg 240` silently does nothing — **done**

`doc/api/styling.md:198` documents it as "ANSI 256 shorthand", but
`src/charm/style/color.clj:118` dispatches on `(:type color)`, which is `nil` for
an integer, and returns the style unchanged. Verified:
`(style/render (style/style :fg 240) "hi")` → `"hi"`, no escapes.

Used in the library's own defaults — `src/charm/components/text_input.clj:97`
(placeholder) and `src/charm/components/list.clj:305` (item descriptions) — and
in ~15 places across docs and examples. So a documented feature, the library's
default styling, and most of the sample code all render as plain text.

**Suggestion:** coerce integers and keywords to colour maps in `style` /
`with-fg` / `with-bg`, or throw on an unrecognised colour value.

**Resolved:** `charm.style.color/coerce-color` accepts integers (ANSI 256),
keywords (ANSI 16 names) and strings (hex), and throws an `ex-info` naming the
offending value otherwise. Coercion happens in `apply-color-fg` / `apply-color-bg`
rather than in each constructor, so every entry point — `style`, `with-fg`,
`with-bg`, `styled-str` — picks it up at once.

### U2 — `:strikethrough` silently does nothing — **done**

Documented in the `doc/api/styling.md:32` options table, used in
`doc/examples/src/examples/todos.clj:20`, never applied by
`style->attributed-style` (`src/charm/style/core.clj:143`).

**Suggestion:** add it to the `cond->`, and audit the docs table against that
function for anything else missing.

**Resolved:** added as `.crossedOut`, plus `:strikethrough false` in the `style`
constructor's defaults. The audit found nothing else missing — the docs table
and the `cond->` now agree, and a test walks every documented attribute and
asserts its SGR code reaches the output.

### U3 — `"pgup"` / `"pgdown"` never match — Page Up/Down is dead in four components — **done**

Key events carry `:key :page-up`; `key-match?` compares against
`(name :page-up)` = `"page-up"`. Verified:
`(msg/key-match? (msg/key-press :page-up) "pgup")` → `false`.

Affects `src/charm/components/viewport.clj:29-30`,
`src/charm/components/list.clj:24-25`,
`src/charm/components/table.clj:28-29`,
`src/charm/components/paginator.clj:20-21`.

**Suggestion:** rename the bindings to `"page-up"` / `"page-down"`, or accept
aliases in `key-match?`. Add a test that asserts every default binding string in
every component resolves against a real key event — this class of bug is
otherwise invisible.

**Resolved:** both. The component defaults were renamed to `"page-up"` /
`"page-down"`, and `key-match?` gained a small alias table, because the same bug
covered **`"esc"`**, which is used in ten of this repository's own examples and
guides plus the `doc/api/messages.md` table — so escape handling was dead
throughout the sample code, not just Page Up and Page Down.

The guard is `test/charm/components/keybindings_test.clj`. Rather than encode a
naming convention, it generates one key-press message for every key a real event
can carry (`keys/key-types` plus printable runes, across all eight modifier
combinations) and asserts that each default binding in every component matches at
least one of them — i.e. that some real keystroke can reach it.

`key-match?` was also simplified while there: its keyword and string branches
were the same comparison written twice, and the modifier branch's
`(= key-part (str msg-key))` fallback was unreachable, since `:key` is always
either a string or a keyword.

One thing found here and deliberately left alone, now U12 below.

### U4 — `(style :padding 3)` throws — **done**

`render` passes the raw value to `expand-box-values` →
`UnsupportedOperationException: count not supported on this type: Long`.
`with-padding` normalizes a bare number; the `style` constructor doesn't, so the
two entry points disagree.

`hex` (`src/charm/style/color.clj:96`) likewise throws a raw
`NumberFormatException` on bad input rather than an `ex-info`.

**Suggestion:** normalize in `style`, and wrap `hex` parse failures in `ex-info`
with the offending string.

**Resolved:** the normalization `with-padding` did inline moved to
`layout/normalize-box`, which `style`, `with-padding` and `with-margin` all use,
so the stored value is a vector whichever entry point built it. The `hex` half
landed with U1.

### U12 — `key-match?` ignores modifiers unless the pattern names one

`(msg/key-match? (msg/key-press "c" :ctrl true) "c")` is `true`: only the
`"ctrl+x"` branch looks at `:ctrl` / `:alt` / `:shift`, so a plain `"c"` binding
also fires on Ctrl+C. Found while fixing U3 and left alone there, because it
changes matching semantics for every existing binding rather than adding a
missing name.

**Suggestion:** require the message's modifiers to be unset when the pattern
names none, and check the repo's own bindings for anything that was relying on
the loose match.

### U5 — Bracketed paste is built but never wired up — **done**

`src/charm/render/core.clj:146-154` has the enable/disable calls and
`src/charm/input/keymap.clj:79-80` binds `:paste-start` / `:paste-end` — but
`run` has no option and never enables it. So pastes arrive as individual
keystrokes (throttled to 100/s by P1), and the markers are indistinguishable
from typed input.

**Suggestion:** add a `:bracketed-paste` option, enable it in `start!`, and
coalesce everything between the markers into a single paste message.

**Resolved** as suggested, plus `msg/paste` / `msg/paste?`, and `stop!` disables
it again. Default `false`: turning it on changes what an application receives, and
one that only handles key presses would stop seeing pastes altogether.

Inside a paste, Enter and Tab are appended as text rather than delivered as key
presses, and an event that cannot be part of the text is dropped without ending
the paste early.

**A bound was needed that the suggestion did not mention.** Waiting for the end
marker with no limit means a truncated paste sequence keeps the input thread in
that loop forever, and the application stops seeing input at all — a freeze, not
a slow path. Found by an integration test for the unterminated case hanging the
suite. Three consecutive read timeouts (~300 ms of silence) now end the paste; a
paste arrives as one contiguous burst, so silence means the terminal is not going
to finish it.

### U6 — Ctrl+C is intercepted and turned into a key message — **done**

`src/charm/program.clj:233`. If the app's `update` doesn't handle `"ctrl+c"`, the
program cannot be killed from the keyboard.

**Suggestion:** make it an option, default to quitting unless the app opts in,
and document it prominently either way.

**Resolved:** `:ctrl-c` is `:quit` by default, which stops the program before
`update` sees the key, or `:message`, which delivers it as an ordinary
`"ctrl+c"` key press and leaves quitting to the application. Documented in
`doc/api/program.md` under its own heading and in the README's option list.

Verified against a program whose `update` handles nothing at all: it now exits on
Ctrl+C, where before it could not be killed from the keyboard. Every one of the
fourteen examples in this repository uses `"ctrl+c"` only to quit, so the new
default is behaviour-preserving for all of them and their bindings simply became
redundant.

### U7 — Overflowing views are truncated from the top

`src/charm/render/core.clj:184` keeps the *last* `height` lines. Sensible inline;
surprising full-screen, where a view one line too tall silently loses its title.

**Suggestion:** document it, and make the direction configurable.

### U8 — Component IDs are `(rand-int 1000000)`

`src/charm/components/list.clj:88`, `src/charm/components/text_input.clj:79`,
`src/charm/components/viewport.clj:66`. ~1% collision chance at 150 components,
and it makes component state non-reproducible in tests.

**Suggestion:** a counter or `gensym`.

### U9 — Two overlapping key-matching APIs

`msg/key-match?` and `keys/key-matches?`, with different pattern semantics
operating on different data shapes (message vs. event map).

**Suggestion:** collapse to one; keep `msg/key-match?` as the public entry point.

### U10 — `style` returns all 17 keys defaulted

So `merge`ing a variant onto a base overwrites with `nil` / `false` instead of
inheriting. There is no `merge-style` / `inherit`, which is the first thing
anyone building a theme reaches for.

**Suggestion:** add `merge-style` that ignores unset keys, or stop defaulting
every key in the constructor.

### U11 — Nothing auto-sizes to the terminal

Every component takes `:height 0` = unbounded, so each app hand-threads
window-size arithmetic. `doc/examples/src/examples/file_browser.clj:85-93` spends
real code on `chrome-height` bookkeeping.

**Suggestion:** a "fill remaining space" affordance would remove a lot of
per-app boilerplate.

---

## 4. JLine 4.x adoption

The project started on JLine 3.30.6 (2026-01), moved to 4.0.10/4.0.12 (April —
the Mode 2027 grapheme work, ADR 007), and sits on 4.3.1 (July) — verified to be
the current tip of the 4.x line (3.30.16 is a maintenance backport on the old
branch). So the version is current; these items are 4.x capabilities the
library doesn't use yet, plus behavior changes to be aware of.

Already absorbed: Mode 2027 grapheme clustering (ADR 007), the 4.3.1 ReDoS
guards, 4.1's `Display.update()` optimizations, and the shift+tab CSI Z binding
(done in-repo, `aa6a75e` — JLine still doesn't bind it).

### J1 — Background color detection / adaptive colors — **done**

JLine 4 can query the terminal's actual default colors (OSC 10/11):

```java
terminal.getDefaultBackgroundColor()   ; -> int RGB, or -1 if unknown
terminal.getDefaultForegroundColor()
terminal.getPalette()                  ; -> ColorPalette
```

This is the primitive lipgloss uses for `HasDarkBackground` — light/dark
adaptive styling is one of the most-requested TUI features and charm.clj
currently has no way to do it.

Related dormant code: `charm.style.color` already contains
`detect-color-profile` and `downgrade-color`, and **nothing calls either**
(verified by grep). JLine 4 also detects true-color from `COLORTERM` natively.

**Suggestion:**
- Add `charm.terminal/dark-background?` (query `getDefaultBackgroundColor`,
  compute luminance, sensible default when the query returns -1).
- Add an adaptive color type: `{:type :adaptive :light <color> :dark <color>}`,
  resolved at render time against the detected background.
- Wire `detect-color-profile` + `downgrade-color` into the render path (or
  delegate profile detection to JLine's `ColorPalette`) so ANSI-only terminals
  degrade instead of getting raw true-color escapes.
- Expose the resolved profile/background on the program state (e.g. an
  `:environment` msg at startup) so apps can branch on it.
- Note the interaction with U1: fixing integer-color coercion first avoids
  building adaptive colors on top of a constructor that silently drops ints.

**Resolved:**

- `charm.terminal/dark-background?` queries `getDefaultBackgroundColor`, computes
  luminance via `dark-color?`, and defaults to dark when the query returns -1.
- Adaptive colors are `{:type :adaptive :light … :dark …}`, built with
  `style/adaptive` and resolved at render time by `color/resolve-color`.
- `run` detects profile and background once at startup, binds
  `*color-profile*` / `*dark-background?*` around the event loop, and sends an
  `:environment` message so apps can branch themselves.
- `:color-profile` and `:dark-background?` are `run` options; `nil` (the
  default) detects. Pinning `:dark-background?` also skips the OSC 11 query and
  its probe timeout. An unknown profile throws rather than silently passing
  through `downgrade-color`'s `case` default.
- Documented in `doc/api/styling.md` (adaptive colors, profile table, pinning),
  `doc/api/program.md` (both options) and `doc/api/messages.md`
  (`:environment`).

**Three premises above turned out to be wrong, and are worth recording:**

1. *"ANSI-only terminals get raw true-color escapes."* They never did — and
   neither does anything else. `AttributedString.toAnsi()` called without a
   terminal argument collapses 24-bit colors to the 256 palette, so charm has
   never emitted a `38;2;r;g;b` sequence: `(rgb 255 0 0)` renders as
   `ESC[38;5;196m` even under the `:true-color` profile, and the existing tests
   in `test/charm/style/color_test.clj` assert exactly that. Making true color
   actually reach the terminal is a separate change — it needs the `toAnsi`
   overload that takes a terminal — and it belongs with the Phase 4 render work,
   since it changes those assertions.

2. *`downgrade-color` was dormant but also wrong.* Its `:ansi` branch did
   `(mod code 16)` on a 256-cube index, which is colorimetrically meaningless:
   orange `(rgb 255 128 0)` came out **cyan**. Harmless while nothing called it,
   but wiring it in would have turned every RGB color on a `TERM=xterm` terminal
   into an effectively random one of 16 — worse than the previous
   pass-everything-through behavior.

   It was first replaced with a hand-rolled nearest-neighbour match, which was
   the wrong instinct — see premise 3.

3. *All of the color conversion was JLine's already, and our copies were worse.*
   `org.jline.utils.Colors` offers `rgbColor(int)` (palette index → RGB),
   `roundColor(idx, max)` and `roundRgbColor(r, g, b, max)`, matching in CIE Lab
   via `Colors$Distance`, over `DEFAULT_COLORS_256`. Measured against it:

   - `ansi256->rgb` was identical to `Colors/rgbColor` on every probe — pure
     duplication.
   - `rgb->ansi16` agreed on 5 of 6 probes; ours measured distance in RGB space.
   - `rgb->ansi256` was **strictly worse in 73 of 125 probes**, for two reasons:
     it only searched the 16–231 cube and the 232–255 ramp, so exact matches in
     the first 16 entries were missed (olive `(128,128,0)` *is* entry 3; we
     returned 142), and it quantised as `(v*5/255)` as if the cube levels were
     evenly spaced when they are 0/95/135/175/215/255, so 128 became 175 rather
     than 95.

   Worse, wiring `downgrade-color` into `apply-color-fg` *took this job away
   from JLine*, which had been doing it correctly all along: before the wiring,
   `apply-color-fg` passed raw RGB to `.foreground` and JLine's `toAnsi` chose
   208 for orange; after it, our matcher chose 214 and JLine merely printed it.

   `downgrade-color` now delegates to `Colors`, and the three conversion
   functions plus `ansi-hex` and `cube-levels` are deleted. The `:ansi` profile
   keeps the one thing the wiring genuinely gained — basic SGR codes
   (`ESC[91m`) instead of a 256-color code, which no-terminal `toAnsi` will not
   emit.

   Still ours, because JLine has no equivalent: `coerce-color` (ints, keywords,
   hex) and `adaptive`. Also still ours, but duplicating terminfo `max_colors`:
   `detect-color-profile`.

**The dynamic vars are the interim mechanism, and Phase 4 retires them.**

`resolve-color` needs the profile four frames below anything that was handed the
state:

```
(view state) → (list-view (:list state)) → (style/render item-style title) → resolve-color
```

`style/render` returns a finished `String` with escapes already in it, so the
"is this terminal true-color?" decision is made down there, in a function whose
signature is `(style & strings)`. Dynamic vars are the standard answer to that,
and the alternative — threading an environment argument through every
`xxx-view` — is viral across the whole component API.

The exit is [ADR 008](adr/008-styling-currency.md): `style/render` builds styled
*data*, and the renderer, which already holds the terminal, resolves colors when
it emits.
No ambient value, no threading. That is the same change premise 1 above needs —
real `38;2;r;g;b` output requires the `toAnsi` overload that takes a `Terminal`,
which only the renderer has — so the two should land together. It breaks user
code that concatenates styled strings (`(str (style/render …) "more")`), which
is why it belongs with the Phase 4 render work and not here.

Two smaller things deliberately left alone until then, both invisible today:

1. `init` is called in `run`'s `let`, before the `binding` opens, so styling
   done at init time resolves against the root values. No component's `init`
   styles anything, so only user code that pre-renders styled strings in its
   own `init` can reach it.
2. The vars are thread-local. core.async's `go` conveys the binding frame, so
   commands are fine; a `view` that renders parts on app-owned threads or
   futures is not.

Both disappear with the renderer-owned environment, so fixing them now would be
wasted work.

### J2 — `KeyEvent` / `KeyParser`: revisit ADR 004

`org.jline.terminal.KeyParser/parse` (static, non-blocking — none of ADR 004's
BindingReader objections apply) returns a structured `KeyEvent`: type
(Character/Arrow/Function/Special/Unknown), `EnumSet` of Shift/Alt/Control,
raw sequence. This API did not exist when ADR 004 was written and overlaps
heavily with the ~500 hand-rolled lines in `charm.input.keys` +
`charm.input.keymap` (including the generated xterm modifier table).

**Suggestion:** spike — run the corpus from `test/charm/input/keys_test.clj`
through `KeyParser.parse` and diff coverage. If it covers the table, delete
code; if not, document the gap in ADR 004 and keep the keymap.

### J3 — `ScreenTerminal` for integration tests

`org.jline.utils.ScreenTerminal` (consolidated 4.2, scrollback + cell decoding
exposed 4.3.0) is a full in-memory VT emulator: `write` the renderer's output,
then assert on the resulting screen grid via `dump` / `getHistory` and per-cell
accessors (`cellCodePoint`, `cellBold`, `cellFg`, …).

The current integration-test pattern (ADR 003: dumb terminal +
`ByteArrayOutputStream`) asserts on raw escape bytes, which breaks whenever
`Display`'s diffing strategy changes even though the screen is identical.
Asserting on final screen cells tests what the user actually sees, and could
absorb some VHS visual tests without the ffmpeg/ttyd CI machinery (currently
commented out in `ci.yml`).

**Suggestion:** add a test helper that renders a frame into a `ScreenTerminal`
and returns the grid; migrate the brittle byte-level assertions; update ADR 003.

### J4 — `Terminal.trackMouse` now covers SGR

`MouseSupport` in 4.3.1 emits `?1005/?1006/?1015` (verified in the jar), so
ADR 004's era gap is closed. `trackMouse(MouseTracking/{Off,Normal,Button,Any})`
maps 1:1 onto charm's `nil/:normal/:cell/:all` and could replace the raw escape
writes in `src/charm/render/core.clj:96-120`; `readMouseEvent(String)` accepts
the already-read prefix, addressing the "detect the prefix first" objection.
JLine then also cleans up mouse state on `close()`.

**Suggestion:** replace enable/disable with `trackMouse`; keep the custom SGR
*parsing* (it works and is tested).

### J5 — Terminal graphics: images (Kitty / iTerm2 / Sixel)

4.0 added `TerminalGraphicsManager` with runtime protocol detection and
one-call display (`isGraphicsSupported`, `displayImage(terminal, file)`).
A genuine feature opportunity — image support Bubble Tea core doesn't have.

Caveats: lives in the `impl` package (weaker stability guarantees) and depends
on `java.awt.BufferedImage` — expect unavailable on babashka and extra config
for native-image (`isJavaDesktopAvailable` guard exists for graceful
degradation).

**Suggestion:** optional `charm.components.image` guarded by
`isGraphicsSupported`; document the platform matrix.

### J6 — Signal handling: migrate to `Terminal.handle` — **blocked**

JLine 4 modernized signals (4.1 FFM `sigaction`, 4.2 ISIG restoration, 4.3
PosixSysTerminal interception). charm uses the static
`org.jline.utils.Signals/register` (`src/charm/program.clj:228,233`);
`Terminal.handle(Signal, SignalHandler)` is the supported route **and returns
the previous handler** — exactly what S5 needs to restore Ctrl+C after exit.

**Suggestion:** fix S5 by migrating to `Terminal.handle`, one change for both.

**Not done — blocked on babashka, and the blocker is `reify`, not the class.**
The migration was written and reverted: it fails to load under `bb test:bb`, a
platform this project supports and tests in CI. Diagnosed precisely afterwards,
because the first read of the error was misleading:

| in babashka | result |
|---|---|
| `Class/forName` on `Terminal$Signal`, `Terminal$SignalHandler`, `Terminal$MouseTracking`, `MouseEvent` | all resolve — the classes are in the image |
| `Terminal$Signal` as a *symbol* (`:import`, static field access) | not exposed to SCI |
| `Terminal$SignalHandler` as a symbol | **exposed** |
| `(.getEnumConstants Terminal$Signal)` via `Class/forName` | works — enum values are reachable |
| `(.getField signal-class "WINCH")` | fails, not registered for reflection |
| `reify Terminal$SignalHandler` | **`Unsupported interface in reify`** |
| `java.lang.reflect.Proxy` | not exposed |

So the handler interface cannot be implemented in babashka at all: `reify` works
only for the interfaces on babashka's own list (`Runnable`, `Comparator`,
`Map$Entry` and the like), and there is no `Proxy` to fall back on. Exposing
`Terminal$Signal` would not be enough; babashka would have to add a third-party
callback interface to its reify registry, which is a larger ask than exposing a
class, and would then put a floor on the babashka version charm supports.

S5 was fixed with the static `Signals` helper instead — it returns the previous
handler just as `Terminal.handle` does, and `reify Runnable` is on babashka's
list. Nothing was lost but the supported-API argument.

Worth revisiting when there is a reason beyond tidiness. The candidates:
`Signals` installs handlers process-globally where `Terminal.handle` is
per-terminal (latent, since the handlers are now restored); JLine's newer signal
work — 4.1's FFM `sigaction`, 4.2's ISIG restoration, 4.3's `PosixSysTerminal`
interception — lands on `Terminal.handle` and not on `Signals`, and charm runs in
raw mode, where ISIG behavior matters; and `Signals` reflects on
`sun.misc.Signal`, which needs GraalVM reflection config (untested against the
native-image config under `doc/examples/`).

**Note for J4:** `trackMouse` takes the `Terminal$MouseTracking` *enum*, not a
callback, and `.getEnumConstants` does work in babashka — so J4 does not need
anything from babashka, unlike this.

### J7 — Behavior changes to be aware of

- **Terminals throw when used after close** (4.0 breaking change; 3.x was
  silent). `run`'s cleanup ordering is fine, but `run-async`'s `:quit!` racing
  a late `render!` against `term/close` would now throw. Audit alongside S5/S6.

  **Audited.** `run-async`'s `:quit!` only flips the `running?` atom; the loop
  notices, finishes its iteration and then does its own cleanup, all on the
  program's thread, so there is no external `render!` to race. The real exposure
  was the WINCH handler, which outlived the program and called `get-size` on a
  closed terminal — that is S5, now fixed. What remains is the input thread,
  which can touch the reader after `term/close`; it catches `Exception` broadly
  and is a daemon, so it exits quietly. P8 (backoff) is where that gets tidied.
- 4.1.2/4.1.3 fixed alt-screen cursor positioning and raw-mode ISIG behavior —
  any local workarounds can be removed.
- New sibling modules `jline-prompt` / `jline-components` / `jline-shell` (4.0):
  JLine growing its own UI story. Nothing to adopt, worth watching as
  overlapping ecosystem.
- **Kitty keyboard protocol (CSI u): not in JLine** as of 4.3.1 — the xterm
  modifier table in `keymap.clj` remains necessary; disambiguated keys
  (Shift+Enter etc.) would need an in-repo implementation.

---

## Suggested sequencing

**Phase 0 — background color detection (J1)** — **done**
U1 (integer-color coercion) landed as its opening step, so adaptive colors
aren't built on a constructor that silently drops ints. Also fixed
`downgrade-color`'s nearest-ANSI-16 mapping, which the wiring made reachable,
and added the `:color-profile` / `:dark-background?` options so the detected
environment can be pinned. 163 tests / 1001 assertions passing, up from
152 / 940.

**Phase 1 — correctness bugs, small and independent** — **done**
U2, U3, U4, P3, P7, P9, and the `$` fix in S7. U3 grew to cover `"esc"`, which
was dead everywhere; P9 grew a `:reflection` build check, which also turned up
three auto-boxing `recur` args. 163 tests / 1105 assertions passing, up from
163 / 1001. Left for later, found while here: `key-match?` ignores modifiers
outside its `"ctrl+x"` branch (now U12).

**Phase 2 — security** — **done**
S1 + S2 + S3 as one sanitiser (`charm.ansi.sanitize`), then S4, S5, S6, and the
action pinning in S7. The J7 close-race audit landed on S5. 169 tests / 1159
assertions passing, up from 163 / 1105.

Two things did not go as planned. S5 could not be done via J6: `Terminal.handle`
needs nested classes babashka cannot resolve, so it uses the `Signals` helper,
which returns the previous handler just the same. And the JLine 4.4.5 bump from
Phase 0 had already closed the OSC/DCS half of S1 — the sanitizer's remaining
value is `ESC c`, CR, private CSI and C1, which are still live. Both are written
up under their items.

**Still open from this phase:** gating the release job on a tag instead of every
push to `main` (S7), which changes how releases are cut.

**Phase 3 — event loop** — **done**
P1 + P2 as one change, then P6, P8, U5, U6. 175 tests / 1217 assertions passing,
up from 169 / 1159.

Two hazards that only showed up once the loop stopped rendering per message:
the frame a program quits on was dropped, and a dirty frame could be forgotten by
a later clean batch. Both under P2. U5 needed a timeout bound that the item did
not mention, or an unterminated paste freezes the input thread — found by a test
hanging.

**Phase 4 — rendering architecture**
P4, then P5 (one representation through the layout stack — spans as data, see
[ADR 008](adr/008-styling-currency.md)). Largest change, biggest headroom, best
done once the loop above is stable. Also where premise 1 under J1 gets resolved
and the Phase 0 dynamic vars are retired.

**Phase 5 — JLine adoption + API ergonomics**
J3 (ScreenTerminal tests — can also be pulled earlier, it's independent),
J2 spike, J4, J5. U7, U8, U9, U10, U11, U12 (U12 with U9, since both are about
key matching).
