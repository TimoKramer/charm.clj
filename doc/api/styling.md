# Styling API

The styling API provides colors, text attributes, and box model properties for rendering styled text.

## Creating Styles

### style

```clojure
(charm/style & options)
```

Create a style map with colors, attributes, and layout properties.

**Color Options:**

| Option | Type | Description |
|--------|------|-------------|
| `:fg` | color | Foreground color |
| `:bg` | color | Background color |

**Text Attributes:**

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:bold` | boolean | `false` | Bold text |
| `:italic` | boolean | `false` | Italic text |
| `:underline` | boolean | `false` | Underlined text |
| `:blink` | boolean | `false` | Blinking text |
| `:faint` | boolean | `false` | Dim/faint text |
| `:reverse` | boolean | `false` | Reverse video |
| `:strikethrough` | boolean | `false` | Strikethrough text |

**Dimensions:**

| Option | Type | Description |
|--------|------|-------------|
| `:width` | int | Fixed width (pads or truncates) |
| `:height` | int | Fixed height |

**Alignment:**

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:align` | keyword | `:left` | Horizontal: `:left`, `:center`, `:right` |
| `:valign` | keyword | `:top` | Vertical: `:top`, `:center`, `:bottom` |

**Spacing:**

| Option | Type | Description |
|--------|------|-------------|
| `:padding` | vector/int | Inner spacing `[top right bottom left]` or single value |
| `:margin` | vector/int | Outer spacing `[top right bottom left]` or single value |

**Border:**

| Option | Type | Description |
|--------|------|-------------|
| `:border` | border | Border style |
| `:border-fg` | color | Border foreground color |
| `:border-bg` | color | Border background color |

**Example:**

```clojure
(def my-style
  (charm/style :fg charm/cyan
               :bg charm/black
               :bold true
               :padding [1 2]
               :border charm/rounded-border))
```

### render

```clojure
(charm/render style text)
(charm/render style text1 text2 ...)
```

Apply a style to text and return the styled string.

```clojure
(charm/render (charm/style :fg charm/red :bold true) "Error!")
; => "\u001b[31m\u001b[1mError!\u001b[0m"

(charm/render (charm/style :fg charm/green) "Hello" "World")
; => styled "Hello World"
```

### styled

```clojure
(charm/styled text & style-options)
```

Shorthand for creating and rendering a style in one call.

```clojure
(charm/styled "Hello" :fg charm/cyan :bold true)
; equivalent to:
(charm/render (charm/style :fg charm/cyan :bold true) "Hello")
```

## Colors

### Color Constructors

#### rgb

```clojure
(charm/rgb r g b)
```

Create a 24-bit true color.

```clojure
(charm/rgb 255 128 0)   ; Orange
(charm/rgb 0 255 128)   ; Mint green
```

#### hex

```clojure
(charm/hex hex-string)
```

Create a color from hex string.

```clojure
(charm/hex "#ff8000")   ; Orange
(charm/hex "00ff80")    ; Mint green (# optional)
```

#### ansi

```clojure
(charm/ansi code-or-name)
```

Create an ANSI 16 color (0-15).

```clojure
(charm/ansi 1)          ; Red
(charm/ansi :red)       ; Red
(charm/ansi :bright-blue) ; Bright blue
```

**ANSI color names:** `:black`, `:red`, `:green`, `:yellow`, `:blue`, `:magenta`, `:cyan`, `:white`, `:bright-black`, `:bright-red`, `:bright-green`, `:bright-yellow`, `:bright-blue`, `:bright-magenta`, `:bright-cyan`, `:bright-white`

#### ansi256

```clojure
(charm/ansi256 code)
```

Create an ANSI 256 color (0-255).

```clojure
(charm/ansi256 208)     ; Orange from 256 palette
(charm/ansi256 240)     ; Dark gray
```

**ANSI 256 ranges:**
- 0-15: Standard colors (same as ANSI 16)
- 16-231: 6x6x6 color cube
- 232-255: Grayscale ramp

### Predefined Colors

```clojure
charm/black
charm/red
charm/green
charm/yellow
charm/blue
charm/magenta
charm/cyan
charm/white

charm/bright-black
charm/bright-red
charm/bright-green
charm/bright-yellow
charm/bright-blue
charm/bright-magenta
charm/bright-cyan
charm/bright-white
```

### Using Colors

```clojure
;; Foreground color
(charm/style :fg charm/red)
(charm/style :fg (charm/rgb 255 128 0))
(charm/style :fg (charm/hex "#ff8000"))
(charm/style :fg 240)  ; ANSI 256 shorthand

;; Background color
(charm/style :bg charm/blue)
(charm/style :bg (charm/rgb 0 0 128))

;; Both
(charm/style :fg charm/white :bg charm/red)
```

## Borders

### Border Styles

```clojure
charm/normal-border   ; ┌─┐│ │└─┘
charm/rounded-border  ; ╭─╮│ │╰─╯
charm/thick-border    ; ┏━┓┃ ┃┗━┛
charm/double-border   ; ╔═╗║ ║╚═╝
charm/hidden-border   ; spaces (for alignment)
```

### Using Borders

```clojure
(charm/render
  (charm/style :border charm/rounded-border
               :padding [0 1])
  "Hello")
; ╭───────╮
; │ Hello │
; ╰───────╯

(charm/render
  (charm/style :border charm/double-border
               :border-fg charm/cyan)
  "Styled")
; ╔════════╗
; ║ Styled ║ (border in cyan)
; ╚════════╝
```

## Box Model

### Padding

Inner spacing between content and border.

```clojure
;; Single value: all sides
(charm/style :padding [1])      ; 1 on all sides
(charm/style :padding 1)        ; shorthand

;; Two values: [vertical horizontal]
(charm/style :padding [1 2])    ; 1 top/bottom, 2 left/right

;; Four values: [top right bottom left]
(charm/style :padding [1 2 1 2])
```

### Margin

Outer spacing outside the border.

```clojure
(charm/style :margin [1])       ; 1 on all sides
(charm/style :margin [0 2])     ; 0 top/bottom, 2 left/right
(charm/style :margin [1 2 3 4]) ; top right bottom left
```

### Width and Height

```clojure
;; Fixed width (pads short text, truncates long text)
(charm/style :width 20 :align :center)

;; Fixed height
(charm/style :height 5 :valign :center)
```

## Style Modifiers

Chainable functions for building styles:

```clojure
(-> (charm/style)
    (charm/with-fg charm/cyan)
    (charm/with-bg charm/black)
    (charm/with-bold)
    (charm/with-padding [1 2])
    (charm/with-border charm/rounded-border))
```

| Function | Description |
|----------|-------------|
| `with-fg` | Set foreground color |
| `with-bg` | Set background color |
| `with-bold` | Enable bold |
| `with-italic` | Enable italic |
| `with-underline` | Enable underline |
| `with-padding` | Set padding |
| `with-margin` | Set margin |
| `with-border` | Set border |
| `with-width` | Set fixed width |
| `with-height` | Set fixed height |
| `with-align` | Set horizontal alignment |
| `with-valign` | Set vertical alignment |

## Examples

### Styled Text

```clojure
;; Simple colored text
(charm/render (charm/style :fg charm/red) "Error")

;; Bold and colored
(charm/render (charm/style :fg charm/green :bold true) "Success!")

;; Multiple attributes
(charm/render
  (charm/style :fg charm/yellow :bg charm/blue :bold true :underline true)
  "Important")
```

### Boxes

```clojure
;; Simple box
(charm/render
  (charm/style :border charm/rounded-border)
  "Content")

;; Padded box
(charm/render
  (charm/style :border charm/normal-border
               :padding [1 2])
  "Padded content")

;; Styled box
(charm/render
  (charm/style :fg charm/white
               :bg charm/blue
               :border charm/double-border
               :border-fg charm/cyan
               :padding [1 3])
  "Styled box")
```

### Centered Content

```clojure
(charm/render
  (charm/style :width 40
               :align :center
               :border charm/rounded-border)
  "Centered text")
```
