(ns ^:no-doc charm.render.core
  "Terminal renderer using JLine's Display for efficient diffing.

   Provides a high-level rendering API that efficiently updates
   the terminal by only redrawing changed content."
  (:require [charm.ansi.sanitize :as san]
            [charm.render.screen :as scr]
            [charm.terminal :as term])
  (:import [java.util ArrayList Collection]
           [org.jline.terminal Terminal]
           [org.jline.utils Display AttributedString]))

;; ---------------------------------------------------------------------------
;; Renderer State
;; ---------------------------------------------------------------------------

(defn create-renderer
  "Create a renderer for a terminal.

   Options:
     :fps         - Target frames per second (default: 60)
     :alt-screen  - Use alternate screen buffer (default: false)
     :hide-cursor - Hide cursor during rendering (default: true)
     :sanitize    - Drop everything but SGR styling from rendered content
                    (default: true)
     :bracketed-paste - Ask the terminal to bracket pasted text (default: false)
     :overflow    - Which end to drop lines from when the view is taller than
                    the terminal: :top (default) or :bottom"
  [^Terminal terminal & {:keys [fps alt-screen hide-cursor sanitize bracketed-paste
                                overflow]
                         :or {fps 60 alt-screen false hide-cursor true
                              sanitize true bracketed-paste false
                              overflow :top}}]
  (let [{:keys [width height]} (term/get-size terminal)
        display (doto (Display. terminal false)
                  (.resize height width))]
    (atom {:terminal terminal
           :display display
           :fps fps
           :alt-screen alt-screen
           :in-alt-screen false
           :hide-cursor hide-cursor
           :sanitize sanitize
           :bracketed-paste bracketed-paste
           :overflow overflow
           :width width
           :height height
           :running false})))

;; ---------------------------------------------------------------------------
;; Terminal Output
;; ---------------------------------------------------------------------------

(defn- write-terminal!
  "Write directly to terminal."
  [renderer ^String s]
  (let [^Terminal terminal (:terminal @renderer)
        ^java.io.PrintWriter writer (.writer terminal)]
    (.print writer s)
    (.flush writer)))

;; ---------------------------------------------------------------------------
;; Cursor Control
;; ---------------------------------------------------------------------------

(defn show-cursor!
  "Show the terminal cursor."
  [renderer]
  (term/show-cursor (:terminal @renderer)))

(defn hide-cursor!
  "Hide the terminal cursor."
  [renderer]
  (term/hide-cursor (:terminal @renderer)))

(defn move-cursor!
  "Move cursor to position (0-indexed)."
  [renderer col row]
  (term/move-cursor (:terminal @renderer) col row))

;; ---------------------------------------------------------------------------
;; Screen Control
;; ---------------------------------------------------------------------------

(defn enter-alt-screen!
  "Enter the alternate screen buffer."
  [renderer]
  (when-not (:in-alt-screen @renderer)
    (let [terminal (:terminal @renderer)]
      (term/enter-alt-screen terminal)
      (swap! renderer assoc :in-alt-screen true)
      (term/clear-screen terminal)
      (term/cursor-home terminal))))

(defn exit-alt-screen!
  "Exit the alternate screen buffer."
  [renderer]
  (when (:in-alt-screen @renderer)
    (term/exit-alt-screen (:terminal @renderer))
    (swap! renderer assoc :in-alt-screen false)))

(defn clear-screen!
  "Clear the screen."
  [renderer]
  (let [terminal (:terminal @renderer)]
    (term/clear-screen terminal)
    (term/cursor-home terminal)))

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

(defn render!
  "Render content to the terminal using JLine's Display for efficient diffing.

   Content can be a string (multi-line) which will be split
   and rendered line by line.

   Unless the renderer was created with `:sanitize false`, the content is
   stripped of every escape sequence but SGR styling first. A view is usually
   part application text and part data the application did not author, and the
   renderer is the one place that sees all of it."
  [renderer content]
  (let [{:keys [^Display display width height sanitize overflow]} @renderer
        content (if (empty? content) " " content)
        content (if sanitize (san/sanitize content) content)
        lines (scr/content->lines content)
        ;; Drop whichever end :overflow names when the view is too tall. Dropping
        ;; from the top suits an inline program, where the newest output is at the
        ;; bottom; a full-screen one usually wants its first lines kept, or a view
        ;; one line too tall loses its title without saying so.
        lines (if (and (pos? height) (> (count lines) height))
                (if (= :bottom overflow)
                  (subvec lines 0 height)
                  (subvec lines (- (count lines) height)))
                lines)
        ;; One parse per line. Going through a string-returning truncate meant
        ;; parsing each line up to three times a frame - once to measure, once
        ;; to cut, once to hand to Display - and the cut lost the line's styling
        ;; on the way back through a plain string.
        attributed (mapv (fn [line]
                           (let [^AttributedString parsed (AttributedString/fromAnsi line)]
                             (if (and (pos? width) (> (.columnLength parsed) width))
                               (.columnSubSequence parsed 0 (int width))
                               parsed)))
                         lines)]
    ;; Display.update handles all the diffing internally.
    ;; Convert to ArrayList because JLine mutates the list internally
    ;; (e.g. calling .remove) and Clojure vectors are immutable.
    (.update display (ArrayList. ^Collection attributed) -1)))

(defn repaint!
  "Force a full repaint on next render."
  [renderer]
  (let [^Display display (:display @renderer)]
    (.clear display)))

;; ---------------------------------------------------------------------------
;; Size Updates
;; ---------------------------------------------------------------------------

(defn update-size!
  "Update the renderer's size (call on window resize)."
  [renderer width height]
  (let [^Display display (:display @renderer)]
    (.resize display height width)
    (swap! renderer assoc :width width :height height)))

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
  (let [{:keys [alt-screen hide-cursor bracketed-paste]} @renderer]
    (when hide-cursor
      (hide-cursor! renderer))
    (when alt-screen
      (enter-alt-screen! renderer))
    (when bracketed-paste
      (enable-bracketed-paste! renderer))
    (swap! renderer assoc :running true)))

(defn stop!
  "Stop the renderer and restore terminal state."
  [renderer]
  (let [{:keys [in-alt-screen hide-cursor bracketed-paste]} @renderer]
    (when bracketed-paste
      (disable-bracketed-paste! renderer))
    (when in-alt-screen
      (exit-alt-screen! renderer))
    (when hide-cursor
      (show-cursor! renderer))
    (disable-mouse! renderer)
    (disable-focus-reporting! renderer)
    (swap! renderer assoc :running false)))
