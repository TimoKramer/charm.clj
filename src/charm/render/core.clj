(ns charm.render.core
  "Terminal renderer with efficient diffing.

   Provides a high-level rendering API that efficiently updates
   the terminal by only redrawing changed content."
  (:require [charm.render.screen :as scr]
            [charm.terminal :as term])
  (:import [org.jline.terminal Terminal]))

;; ---------------------------------------------------------------------------
;; Renderer State
;; ---------------------------------------------------------------------------

(defn create-renderer
  "Create a renderer for a terminal.

   Options:
     :fps         - Target frames per second (default: 60)
     :alt-screen  - Use alternate screen buffer (default: false)
     :hide-cursor - Hide cursor during rendering (default: true)"
  [^Terminal terminal & {:keys [fps alt-screen hide-cursor]
                         :or {fps 60 alt-screen false hide-cursor true}}]
  (let [{:keys [width height]} (term/get-size terminal)]
    (atom {:terminal terminal
           :fps fps
           :frame-time (/ 1000 fps)
           :alt-screen alt-screen
           :hide-cursor hide-cursor
           :width width
           :height height
           :last-lines []
           :lines-rendered 0
           :running false
           :needs-render true})))

;; ---------------------------------------------------------------------------
;; Terminal Output
;; ---------------------------------------------------------------------------

(def ^"[Ljava.lang.Object;" empty-args (object-array 0))

(defn- write-terminal!
  "Write directly to terminal."
  [renderer ^String s]
  (let [^Terminal terminal (:terminal @renderer)
        ^java.io.PrintWriter writer (.writer terminal)]
    (.print writer s)
    (.flush writer)))

(defn- puts!
  "Write terminal capability."
  [renderer capability]
  (let [^Terminal terminal (:terminal @renderer)]
    (.puts terminal capability empty-args)
    (.flush terminal)))

;; ---------------------------------------------------------------------------
;; Cursor Control
;; ---------------------------------------------------------------------------

(defn show-cursor!
  "Show the terminal cursor."
  [renderer]
  (write-terminal! renderer scr/cursor-show))

(defn hide-cursor!
  "Hide the terminal cursor."
  [renderer]
  (write-terminal! renderer scr/cursor-hide))

(defn move-cursor!
  "Move cursor to position (1-indexed)."
  [renderer row col]
  (write-terminal! renderer (scr/cursor-to row col)))

;; ---------------------------------------------------------------------------
;; Screen Control
;; ---------------------------------------------------------------------------

(defn enter-alt-screen!
  "Enter the alternate screen buffer."
  [renderer]
  (when-not (:alt-screen @renderer)
    (write-terminal! renderer scr/enter-alt-screen)
    (write-terminal! renderer scr/clear-screen)
    (write-terminal! renderer scr/cursor-home)
    (swap! renderer assoc
           :alt-screen true
           :last-lines []
           :lines-rendered 0
           :needs-render true)))

(defn exit-alt-screen!
  "Exit the alternate screen buffer."
  [renderer]
  (when (:alt-screen @renderer)
    (write-terminal! renderer scr/exit-alt-screen)
    (swap! renderer assoc
           :alt-screen false
           :last-lines []
           :lines-rendered 0)))

(defn clear-screen!
  "Clear the screen."
  [renderer]
  (write-terminal! renderer scr/clear-screen)
  (write-terminal! renderer scr/cursor-home)
  (swap! renderer assoc :last-lines [] :lines-rendered 0 :needs-render true))

;; ---------------------------------------------------------------------------
;; Mouse Control
;; ---------------------------------------------------------------------------

(defn enable-mouse!
  "Enable mouse tracking.

   Mode can be:
     :normal     - Button events only
     :cell       - Button and movement while pressed
     :all        - All mouse events including motion"
  [renderer mode]
  (case mode
    :normal (do
              (write-terminal! renderer scr/enable-mouse-normal)
              (write-terminal! renderer scr/enable-mouse-sgr))
    :cell (do
            (write-terminal! renderer scr/enable-mouse-cell-motion)
            (write-terminal! renderer scr/enable-mouse-sgr))
    :all (do
           (write-terminal! renderer scr/enable-mouse-all-motion)
           (write-terminal! renderer scr/enable-mouse-sgr))
    nil))

(defn disable-mouse!
  "Disable mouse tracking."
  [renderer]
  (write-terminal! renderer scr/disable-mouse-sgr)
  (write-terminal! renderer scr/disable-mouse-normal)
  (write-terminal! renderer scr/disable-mouse-cell-motion)
  (write-terminal! renderer scr/disable-mouse-all-motion))

;; ---------------------------------------------------------------------------
;; Focus Reporting
;; ---------------------------------------------------------------------------

(defn enable-focus-reporting!
  "Enable focus in/out reporting."
  [renderer]
  (write-terminal! renderer scr/enable-focus-reporting))

(defn disable-focus-reporting!
  "Disable focus reporting."
  [renderer]
  (write-terminal! renderer scr/disable-focus-reporting))

;; ---------------------------------------------------------------------------
;; Bracketed Paste
;; ---------------------------------------------------------------------------

(defn enable-bracketed-paste!
  "Enable bracketed paste mode."
  [renderer]
  (write-terminal! renderer scr/enable-bracketed-paste))

(defn disable-bracketed-paste!
  "Disable bracketed paste mode."
  [renderer]
  (write-terminal! renderer scr/disable-bracketed-paste))

;; ---------------------------------------------------------------------------
;; Window Title
;; ---------------------------------------------------------------------------

(defn set-window-title!
  "Set the terminal window title."
  [renderer title]
  (write-terminal! renderer (scr/set-window-title title)))

;; ---------------------------------------------------------------------------
;; Clipboard
;; ---------------------------------------------------------------------------

(defn copy-to-clipboard!
  "Copy text to system clipboard (if terminal supports OSC 52)."
  [renderer text]
  (write-terminal! renderer (scr/copy-to-clipboard text)))

;; ---------------------------------------------------------------------------
;; Rendering
;; ---------------------------------------------------------------------------

(defn- render-diff!
  "Render content using line diffing for efficiency."
  [renderer new-lines]
  (let [{:keys [width height last-lines lines-rendered]} @renderer
        ;; Truncate to height
        new-lines (if (and (pos? height) (> (count new-lines) height))
                    (subvec new-lines (- (count new-lines) height))
                    new-lines)
        ;; Truncate each line to width
        new-lines (if (pos? width)
                    (mapv #(scr/truncate-line % width) new-lines)
                    new-lines)
        output (StringBuilder.)]

    ;; Move cursor up to start of previous render
    (when (> lines-rendered 1)
      (.append output (scr/cursor-up (dec lines-rendered))))

    ;; Render each line
    (doseq [i (range (count new-lines))]
      (let [old-line (get last-lines i)
            new-line (get new-lines i)
            can-skip (and old-line (= old-line new-line))]
        (if can-skip
          ;; Skip unchanged line, just move down
          (when (< i (dec (count new-lines)))
            (.append output (scr/cursor-down 1)))
          ;; Render changed line
          (do
            (.append output "\r")
            (.append output new-line)
            (.append output scr/clear-line)
            (when (< i (dec (count new-lines)))
              (.append output "\n"))))))

    ;; Clear any remaining lines from previous render
    (when (> lines-rendered (count new-lines))
      (.append output scr/clear-below))

    (.append output "\r")

    ;; Write to terminal
    (write-terminal! renderer (str output))

    ;; Update state
    (swap! renderer assoc
           :last-lines new-lines
           :lines-rendered (count new-lines)
           :needs-render false)))

(defn render!
  "Render content to the terminal.

   Content can be a string (multi-line) which will be split
   and rendered line by line with efficient diffing."
  [renderer content]
  (let [content (if (empty? content) " " content)
        lines (vec (scr/content->lines content))]
    (render-diff! renderer lines)))

(defn repaint!
  "Force a full repaint on next render."
  [renderer]
  (swap! renderer assoc :last-lines [] :lines-rendered 0 :needs-render true))

;; ---------------------------------------------------------------------------
;; Size Updates
;; ---------------------------------------------------------------------------

(defn update-size!
  "Update the renderer's size (call on window resize)."
  [renderer width height]
  (swap! renderer assoc :width width :height height)
  (repaint! renderer))

(defn get-size
  "Get the current terminal size [width height]."
  [renderer]
  [(:width @renderer) (:height @renderer)])

;; ---------------------------------------------------------------------------
;; Lifecycle
;; ---------------------------------------------------------------------------

(defn start!
  "Start the renderer."
  [renderer]
  (let [{:keys [alt-screen hide-cursor]} @renderer]
    (when hide-cursor
      (hide-cursor! renderer))
    (when alt-screen
      (enter-alt-screen! renderer))
    (swap! renderer assoc :running true)))

(defn stop!
  "Stop the renderer and restore terminal state."
  [renderer]
  (let [{:keys [alt-screen hide-cursor]} @renderer]
    (when alt-screen
      (exit-alt-screen! renderer))
    (when hide-cursor
      (show-cursor! renderer))
    (disable-mouse! renderer)
    (disable-focus-reporting! renderer)
    (swap! renderer assoc :running false)))
