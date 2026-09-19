# Changelog

Notable changes to charm.clj, in the style of
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

Versions are `MAJOR.MINOR.<commit count>` — the last number comes from
`git-count-revs` at release time. Releases before 0.3 are recorded only in the
[git tags](https://github.com/TimoKramer/charm.clj/tags).

## Unreleased (0.3)

Light/dark adaptive styling, and colors written as plain values now actually
apply.

### Added

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

### Fixed

- **The 16-color downgrade produced arbitrary colors.** It took `(mod code 16)`
  of a 256-color cube index, which is colorimetrically meaningless — orange came
  out cyan. It was unreachable until profile downgrading was wired into the
  render path, which would have turned every RGB color on a `TERM=xterm`
  terminal into an effectively random one of 16.

### Requirements

JDK 22+. Ships with JLine 4.4.5, which carries the palette fixes above.
Babashka with JLine 4.4.5 (currently on master) for use with babashka.
