# Viewport

A scrollable window onto text that is taller than the space available.

## Quick Example

```clojure
(require '[charm.components.viewport :as viewport])

(def vp (viewport/viewport long-text :width 60 :height 20))

;; In update function
(let [[vp cmd] (viewport/viewport-update vp msg)]
  ;; vp has scrolled if msg was a navigation key
  )

;; In view function
(viewport/viewport-view vp)  ; => the 20 visible lines
```

## Creation Options

```clojure
(viewport/viewport content & options)
```

`content` is a string, which may contain ANSI escape sequences — styled text is
measured by display width, so colours do not throw the layout off.

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:width` | int | `0` | Display width; `0` leaves lines as they are |
| `:height` | int | `0` | Visible height in lines; `0` shows everything |
| `:y-offset` | int | `0` | Initial scroll position, in lines |
| `:keys` | map | defaults | Custom key bindings |
| `:id` | any | counter | Unique identifier |

With `:width` set, every visible line is padded or truncated to exactly that
width, so the viewport always occupies a rectangle. With `:height` set, short
content is padded with blank lines to fill it.

## Key Bindings

| Action | Keys |
|--------|------|
| Line up | `Up`, `k` |
| Line down | `Down`, `j` |
| Half page up | `Ctrl+U` |
| Half page down | `Ctrl+D` |
| Page up | `PageUp` |
| Page down | `PageDown` |
| Top | `Home`, `g` |
| Bottom | `End`, `G` |

Override any of them with `:keys`; what you pass is merged over the defaults:

```clojure
(viewport/viewport text :keys {:line-up ["up" "w"] :line-down ["down" "s"]})
```

## Functions

### viewport-update

```clojure
(viewport/viewport-update vp msg) ; => [vp cmd]
```

Handle a navigation key. Returns the viewport unchanged for messages it does not
recognise, so it is safe to pass everything through it.

### viewport-view

```clojure
(viewport/viewport-view vp) ; => string
```

Render the visible lines.

### Content

```clojure
(viewport/viewport-content vp)              ; the raw content string
(viewport/viewport-set-content vp new-text) ; replace it, scrolling back to the top
(viewport/viewport-line-count vp)           ; total lines, not visible lines
```

### Dimensions

```clojure
(viewport/viewport-set-dimensions vp 80 24)
```

Set width and height together — useful on a `:window-size` message. The scroll
position is clamped so a taller viewport cannot leave you past the end.

### Scrolling

```clojure
(viewport/viewport-scroll-to vp 100)   ; jump to a line number (clamped)
(viewport/viewport-scroll-percent vp)  ; => 0.0-1.0, for a scrollbar or indicator
(viewport/viewport-at-top? vp)
(viewport/viewport-at-bottom? vp)

;; The same movements the key bindings drive
(viewport/scroll-up vp)         (viewport/scroll-down vp)
(viewport/scroll-half-page-up vp) (viewport/scroll-half-page-down vp)
(viewport/scroll-page-up vp)    (viewport/scroll-page-down vp)
(viewport/scroll-to-top vp)     (viewport/scroll-to-bottom vp)
```

## Full Example

```clojure
(ns my-app
  (:require
   [charm.components.viewport :as viewport]
   [charm.message :as msg]
   [charm.program :as program]
   [clojure.string :as str]))

(def document (str/join "\n" (map #(str "Line " %) (range 1 201))))

(defn init []
  [{:vp (viewport/viewport document :width 60 :height 20)} nil])

(defn update-fn [state msg]
  (cond
    (msg/key-match? msg "q")
    [state program/quit-cmd]

    ;; Keep the viewport the size of the window, less the chrome below
    (msg/window-size? msg)
    [(update state :vp viewport/viewport-set-dimensions
             (:width msg) (- (:height msg) 2))
     nil]

    :else
    (let [[vp cmd] (viewport/viewport-update (:vp state) msg)]
      [(assoc state :vp vp) cmd])))

(defn view [state]
  (let [vp (:vp state)]
    (str (viewport/viewport-view vp) "\n\n"
         (format "%d%%  j/k to scroll, q to quit"
                 (int (* 100 (viewport/viewport-scroll-percent vp)))))))

(defn -main [& _]
  (program/run {:init init :update update-fn :view view :alt-screen true}))
```

## Scrolling versus trimming

A viewport scrolls; the renderer does not. If a view is taller than the terminal
the renderer trims it — see [`:overflow`](../api/program.md#views-taller-than-the-terminal).
Put long content in a viewport and the renderer never has to.
