# Changelog

Notable changes to charm.clj, in the style of
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

Versions are `MAJOR.MINOR.<commit count>` — the last number comes from
`git-count-revs` at release time. Releases before 0.3 are recorded only in the
[git tags](https://github.com/TimoKramer/charm.clj/tags).

## Unreleased (0.3)

Light/dark adaptive styling, colors written as plain values now actually apply,
a round of correctness fixes to documented features that silently did nothing,
content a program did not author no longer reaches the terminal as instructions,
and an event loop that no longer caps input at about 100 messages a second.

### Added

- **`:bracketed-paste` option on `run`** (default `false`). With it on, a paste
  arrives as a single `:paste` message carrying the whole text, instead of one
  key press per character that an application cannot tell apart from fast
  typing. `msg/paste` and `msg/paste?` come with it. Off by default, because a
  program that handles only key presses would otherwise stop seeing pastes.
- **`:ctrl-c` option on `run`** (default `:quit`). Ctrl+C now stops the program
  before `update` sees it, so a program that does not handle it can still be
  killed from the keyboard. Pass `:message` to take it over - to confirm before
  quitting, say - and quitting becomes the application's job.
- **`charm.ansi.sanitize`.** `sanitize` keeps SGR styling and drops every other
  escape sequence, plus every control character but newline and tab; `strip`
  drops the styling too, leaving the text a terminal would display;
  `strip-controls` removes control characters entirely, for strings that go
  inside a sequence charm writes itself. All three return their argument
  unchanged when there is nothing to remove.
- **`:sanitize` option on `run`** (default `true`), which sanitizes the view
  before it is written. Set it to `false` only for a view that authors its own
  control sequences.
- **Adaptive colors.** `adaptive` takes a light and a dark variant and resolves
  against the terminal background at render time:

  ```clojure
  (style/style :fg (style/adaptive (style/hex "#333333")
                                   (style/hex "#dddddd")))
  ```

- **Terminal environment detection.** `run` detects the color profile and the
  background at startup, binds both for the lifetime of the program, and sends
  an `:environment` message carrying `:color-profile` and `:dark-background?`,
  so applications can branch on the environment themselves.
- **`:color-profile` and `:dark-background?` options on `run`**, which pin the
  environment instead of detecting it — for a terminal that misreports itself,
  or a test that needs a fixed environment. Pinning `:dark-background?` also
  skips the OSC 11 background query, and with it the probe timeout that a
  terminal which never answers costs at every startup.
- **`charm.terminal/dark-background?`**, which queries the terminal's background
  and assumes dark when the terminal does not answer.

### Changed

- **The event loop blocks for messages instead of polling, and renders once a
  frame.** It used to sleep 10 ms every iteration and then handle exactly one
  message, so every keystroke waited up to 10 ms and throughput was capped near
  100 messages a second: a 300-character paste took three seconds. It now waits
  on the channel, handles everything queued, and renders at most once per
  `:fps`. Measured on the loop alone: 300 messages in 3 ms, 5000 in 7 ms.
- **`:fps` does something.** It was documented, stored and never read - every
  single message triggered a full `view` and terminal diff. It is now the redraw
  ceiling, and a burst of messages costs one frame rather than one per message.
  The frame a program quits on is still drawn, so an inline program's last view
  is not lost.
- **Command bodies run on virtual threads** (`a/io-thread`), not in a `go` block.
  A command is where a program does its blocking work, which is exactly what a
  `go` block must not do; on the eight-thread dispatch pool a handful of
  concurrent commands starved it and froze the UI. Twenty commands sleeping
  100 ms each now finish in ~100 ms rather than ~300, and 2000 concurrent
  commands cost no extra platform threads where a thread pool would have created
  2000 of them. Commands remain the place for blocking I/O rather than for
  extended computation, which now holds a carrier thread. The color environment
  still reaches command bodies, since `io-thread` conveys the binding frame too.
  Where the runtime has no virtual threads - babashka's native image - it falls
  back to ordinary ones.
- **`strip-ansi` strips.** It returned `AttributedString/fromAnsi`'s rendering,
  which leaves a private `CSI` visible as text - `ESC[?1049h` came out as
  `1049h` - and keeps carriage returns, BEL and backspace. It is now
  `charm.ansi.sanitize/strip`.
- **Width measurement sanitizes first.** `string-width` and `truncate` measure
  what the terminal will be given, so a sequence JLine's SGR parser mangles
  rather than removes no longer throws off truncation, padding, borders, joins
  and overlays.
- **`copy-to-clipboard` throws** an `ex-info` naming the size when the encoded
  payload exceeds `max-clipboard-bytes` (74994, tmux's limit and the smallest
  of the common ones), instead of letting the terminal truncate it silently.
- **`key-match?` accepts the short spellings** `"esc"`, `"pgup"` and `"pgdown"`
  (as keywords too) for `:escape`, `:page-up` and `:page-down`. The default
  page bindings in `list`, `table`, `viewport` and `paginator` now read
  `"page-up"` / `"page-down"`.
- **The event loop skips the render when `update` returns the state
  unchanged**, instead of re-running `view` and diffing the frame for a message
  the application ignored. A resize still repaints, since the frame changed even
  when the state did not.
- **Colors written as plain values now apply.** `:fg 240` (ANSI 256 code),
  `:fg :red` (ANSI 16 name) and `:fg "#ff8000"` (hex string) were silently
  dropped before. This also disabled the library's own default styling in
  `help`, `list` and `text-input`, and most sample code.
- **Invalid color values now throw** an `ex-info` naming the value, with
  `:color` in `ex-data`, instead of being ignored. This includes malformed hex
  strings, so `(style/style :fg "red")` is now an error. **Potentially
  breaking** for code that passed an invalid value and rendered unstyled.
- **Colors are downgraded to the terminal's profile at render time.** Under
  `:ascii` color is dropped entirely.
- **Nearest-color matching is delegated to JLine's `Colors`**, which measures
  distance in CIE Lab across the full 256-entry table. The previous hand-rolled
  matcher searched only the color cube and the grayscale ramp, so an exact match
  among the first 16 entries was unreachable — olive `(128,128,0)`, which *is*
  entry 3, came back as 142 — and it quantised as though the cube levels were
  evenly spaced, mapping 128 to 175 instead of 95.
- **JLine 4.3.1 → 4.4.5.** Two `ColorPalette` defects found while building the
  above were fixed upstream ([#2260](https://github.com/jline/jline3/pull/2260),
  [#2261](https://github.com/jline/jline3/pull/2261)). One consequence is
  visible without any charm code change: output to 8- and 16-color terminals is
  now downgraded as it is written, so on `TERM=xterm` a bright red `ESC[91m`
  reaches the terminal as `ESC[31m`. 256-color terminals are unaffected.

### Security

- **Content a program did not author no longer reaches the terminal as
  instructions.** The view is sanitized before it is written, so an escape
  sequence in a filename or a log line is removed rather than obeyed. What
  JLine 4.4.5 did not already stop, and this does:

  | injected into displayed text | before |
  |---|---|
  | `ESC c` | **resets the terminal** |
  | `CR` | content overwrites the line it just drew, and measures wider than it displays |
  | `ESC [ ?1049h` and other private CSI | rendered as the visible text `1049h`, corrupting the frame |
  | C1 controls (`0x80`-`0x9f`) | passed through |

  Note for anyone reading the 0.2 threat model: on JLine 4.3.1 this list also
  included OSC 52 (**writes attacker data into the system clipboard**), OSC 2
  (rewrites the window title), OSC 8 (hyperlinks the text elsewhere) and DCS.
  `AttributedString/fromAnsi` drops all of those as of 4.4.5, so on this release
  they were already unreachable through the render path before the sanitizer -
  but not through `set-window-title`, and not for anyone pinning an older JLine.

- **`set-window-title` escapes its argument.** Control characters are removed,
  so a BEL or an ESC in the title can no longer end charm's OSC early and let
  the rest of the string open one of its own - which was a clipboard write
  reachable from any application that puts untrusted text in its title.

- **Signal handlers are restored on exit.** `run` captured neither the `WINCH`
  nor the `INT` handler it displaced. Both stayed installed after the program
  returned, feeding a closed channel and a closed terminal - so in a REPL, which
  outlives the program, Ctrl+C was swallowed for the rest of the session and a
  window resize reached a terminal that was already gone.

- **A shutdown hook restores the terminal.** `finally` does not cover
  `System/exit` or `SIGTERM`, and an example in this repository exits from inside
  a running program, which left raw mode on, the cursor hidden and the alternate
  screen active.

- **CI supply chain.** Actions are pinned by commit SHA rather than by tag, and
  the babashka installer is fetched from a release tag rather than from `master`.
  (The dev build it installs is still a moving target: charm needs JLine 4.4.5,
  which babashka has only there.)

### Fixed

- **A failing terminal reader no longer spins a core.** Every exception in the
  input thread was caught and discarded with no backoff. It now backs off
  exponentially, and after ten consecutive failures reports an error and stops
  instead of looping forever.
- **Modifiers on messages built from terminal events are booleans**, not `nil`.
  `key-press` documents them as `false`, and that is what they now are however
  the message was built.
- **`"esc"`, `"pgup"` and `"pgdown"` never matched anything.** Key events carry
  `:escape`, `:page-up` and `:page-down`, so `key-match?` compared against
  `"escape"` and friends. Page Up and Page Down were dead in `list`, `table`,
  `viewport` and `paginator`, and every `"esc"` binding — including the ones in
  this repository's own examples and guides — silently did nothing.
- **`:strikethrough` is applied.** It was documented and used, but never reached
  `AttributedStyle`.
- **`(style :padding 3)` no longer throws.** A bare number is normalized to a
  box vector, as `with-padding` already did, so both entry points agree.
- **A viewport or an overflowing view no longer copies all of its content each
  frame.** Both paths called `(vec lines)` on what was already a vector before
  taking a `subvec`, making a viewport over a large log O(total lines) per frame
  instead of O(visible).
- **Reflective calls on render paths.** Type hints in `charm.style.overlay`
  (per overlay line), `charm.render.core` and `charm.input.keymap`; padding is
  built with `String.repeat` rather than a seq of characters. A new
  `clojure -M:reflection` check fails the build on any reflection or
  auto-boxing warning under `src/`.
- **The 16-color downgrade produced arbitrary colors.** It took `(mod code 16)`
  of a 256-color cube index, which is colorimetrically meaningless — orange came
  out cyan. It was unreachable until profile downgrading was wired into the
  render path, which would have turned every RGB color on a `TERM=xterm`
  terminal into an effectively random one of 16.

### Requirements

JDK 22+. Ships with JLine 4.4.5, which carries the palette fixes above.
Babashka with JLine 4.4.5 (currently on master) for use with babashka.
