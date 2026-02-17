(ns charm.components.viewport
  "Scrollable text area component.

   Displays a window into pre-rendered (possibly styled) text content.

   Usage:
     (def vp (viewport \"Long text content...\" :height 10))

     ;; In update function:
     (viewport-update vp msg)

     ;; In view function:
     (viewport-view vp)"
  (:require
   [charm.ansi.width :as w]
   [charm.message :as msg]
   [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Key Bindings
;; ---------------------------------------------------------------------------

(def ^:private default-keys
  "Default key bindings for viewport navigation."
  {:line-up       ["up" "k"]
   :line-down     ["down" "j"]
   :half-page-up  ["ctrl+u"]
   :half-page-down ["ctrl+d"]
   :page-up       ["pgup"]
   :page-down     ["pgdown"]
   :top           ["home" "g"]
   :bottom        ["end" "G"]})

(defn- matches-binding?
  "Check if a message matches any key in a binding."
  [m binding]
  (some #(msg/key-match? m %) binding))

;; ---------------------------------------------------------------------------
;; Viewport Creation
;; ---------------------------------------------------------------------------

(defn- split-content
  "Split content string into lines."
  [content]
  (if (or (nil? content) (empty? content))
    []
    (str/split-lines content)))

(defn viewport
  "Create a viewport component.

   Content is a string (may contain ANSI escape sequences).

   Options:
     :width   - Display width (0 = no constraint)
     :height  - Visible height in lines (0 = show all)
     :y-offset - Initial scroll position (default 0)
     :keys    - Custom key bindings
     :id      - Unique ID"
  [content & {:keys [width height y-offset keys id]
              :or {width 0
                   height 0
                   y-offset 0
                   id (rand-int 1000000)}}]
  (let [lines (split-content content)]
    {:type :viewport
     :id id
     :content (or content "")
     :lines lines
     :width width
     :height height
     :y-offset (min y-offset (max 0 (- (count lines) (max 1 height))))
     :keys (merge default-keys keys)}))

;; ---------------------------------------------------------------------------
;; Viewport Accessors
;; ---------------------------------------------------------------------------

(defn viewport-content
  "Get the raw content string."
  [vp]
  (:content vp))

(defn viewport-set-content
  "Set the content, resetting scroll position."
  [vp content]
  (let [lines (split-content content)]
    (-> vp
        (assoc :content (or content ""))
        (assoc :lines lines)
        (assoc :y-offset 0))))

(defn viewport-line-count
  "Get the total number of lines in the content."
  [vp]
  (count (:lines vp)))

(defn viewport-set-dimensions
  "Set the viewport width and height."
  [vp width height]
  (let [max-offset (max 0 (- (viewport-line-count vp) (max 1 height)))]
    (-> vp
        (assoc :width width)
        (assoc :height height)
        (update :y-offset min max-offset))))

(defn viewport-scroll-to
  "Scroll to a specific line number."
  [vp line]
  (let [h (:height vp)
        max-offset (if (pos? h)
                     (max 0 (- (viewport-line-count vp) h))
                     0)]
    (assoc vp :y-offset (max 0 (min line max-offset)))))

(defn viewport-scroll-percent
  "Get the current scroll position as a percentage (0.0-1.0)."
  [vp]
  (let [h (:height vp)
        total (viewport-line-count vp)
        max-offset (if (pos? h)
                     (max 0 (- total h))
                     0)]
    (if (zero? max-offset)
      1.0
      (double (/ (:y-offset vp) max-offset)))))

(defn viewport-at-top?
  "Check if the viewport is scrolled to the top."
  [vp]
  (zero? (:y-offset vp)))

(defn viewport-at-bottom?
  "Check if the viewport is scrolled to the bottom."
  [vp]
  (let [h (:height vp)
        total (viewport-line-count vp)]
    (or (zero? h)
        (<= total h)
        (>= (:y-offset vp) (- total h)))))

;; ---------------------------------------------------------------------------
;; Viewport Navigation
;; ---------------------------------------------------------------------------

(defn- max-offset
  "Calculate the maximum scroll offset."
  [vp]
  (let [h (:height vp)
        total (viewport-line-count vp)]
    (if (pos? h)
      (max 0 (- total h))
      0)))

(defn- clamp-offset
  "Clamp an offset to valid range."
  [vp offset]
  (max 0 (min offset (max-offset vp))))

(defn scroll-up
  "Scroll up by n lines (default 1)."
  ([vp] (scroll-up vp 1))
  ([vp n]
   (update vp :y-offset #(max 0 (- % n)))))

(defn scroll-down
  "Scroll down by n lines (default 1)."
  ([vp] (scroll-down vp 1))
  ([vp n]
   (update vp :y-offset #(clamp-offset vp (+ % n)))))

(defn scroll-half-page-up
  "Scroll up by half the viewport height."
  [vp]
  (let [half (max 1 (quot (:height vp) 2))]
    (scroll-up vp half)))

(defn scroll-half-page-down
  "Scroll down by half the viewport height."
  [vp]
  (let [half (max 1 (quot (:height vp) 2))]
    (scroll-down vp half)))

(defn scroll-page-up
  "Scroll up by one full page."
  [vp]
  (scroll-up vp (max 1 (:height vp))))

(defn scroll-page-down
  "Scroll down by one full page."
  [vp]
  (scroll-down vp (max 1 (:height vp))))

(defn scroll-to-top
  "Scroll to the top."
  [vp]
  (assoc vp :y-offset 0))

(defn scroll-to-bottom
  "Scroll to the bottom."
  [vp]
  (assoc vp :y-offset (max-offset vp)))

;; ---------------------------------------------------------------------------
;; Viewport Update
;; ---------------------------------------------------------------------------

(defn viewport-update
  "Update viewport state based on a message.
   Returns [new-viewport cmd] or [viewport nil] if message not handled."
  [vp msg]
  (let [keys (:keys vp)]
    (cond
      (matches-binding? msg (:line-up keys))
      [(scroll-up vp) nil]

      (matches-binding? msg (:line-down keys))
      [(scroll-down vp) nil]

      (matches-binding? msg (:half-page-up keys))
      [(scroll-half-page-up vp) nil]

      (matches-binding? msg (:half-page-down keys))
      [(scroll-half-page-down vp) nil]

      (matches-binding? msg (:page-up keys))
      [(scroll-page-up vp) nil]

      (matches-binding? msg (:page-down keys))
      [(scroll-page-down vp) nil]

      (matches-binding? msg (:top keys))
      [(scroll-to-top vp) nil]

      (matches-binding? msg (:bottom keys))
      [(scroll-to-bottom vp) nil]

      :else
      [vp nil])))

;; ---------------------------------------------------------------------------
;; Viewport View
;; ---------------------------------------------------------------------------

(defn viewport-view
  "Render the viewport to a string.

   Shows only the visible lines based on y-offset and height.
   If width is set, lines are padded/truncated to fit."
  [vp]
  (let [{:keys [lines width height y-offset]} vp
        total (count lines)
        ;; Determine visible range
        visible-lines (if (or (zero? height) (<= total height))
                        lines
                        (subvec (vec lines) y-offset
                                (min total (+ y-offset height))))
        ;; Apply width constraint
        visible-lines (if (pos? width)
                        (mapv (fn [line]
                                (let [line-width (w/string-width line)]
                                  (cond
                                    (> line-width width)
                                    (w/truncate line width :tail "")

                                    (< line-width width)
                                    (w/pad-right line width)

                                    :else line)))
                              visible-lines)
                        visible-lines)
        ;; Pad with empty lines if content is shorter than height
        visible-lines (if (and (pos? height) (< (count visible-lines) height))
                        (let [pad-count (- height (count visible-lines))
                              empty-line (if (pos? width)
                                           (apply str (repeat width " "))
                                           "")]
                          (into (vec visible-lines) (repeat pad-count empty-line)))
                        visible-lines)]
    (str/join "\n" visible-lines)))

;; ---------------------------------------------------------------------------
;; Viewport Init
;; ---------------------------------------------------------------------------

(defn viewport-init
  "Initialize the viewport, returns [viewport cmd]."
  [vp]
  [vp nil])
