(ns charm.components.paginator
  "Pagination component for displaying page indicators.

   Usage:
     (def pager (paginator :total-pages 5))

     ;; In update function:
     (paginator-update pager msg)

     ;; In view function:
     (paginator-view pager)"
  (:require [charm.style.core :as style]
            [charm.message :as msg]))

;; ---------------------------------------------------------------------------
;; Key Bindings
;; ---------------------------------------------------------------------------

(def ^:private default-keys
  {:next-page ["right" "l" "pgdown"]
   :prev-page ["left" "h" "pgup"]})

(defn- matches-binding?
  "Check if a message matches any key in a binding."
  [m binding]
  (some #(msg/key-match? m %) binding))

;; ---------------------------------------------------------------------------
;; Paginator Creation
;; ---------------------------------------------------------------------------

(defn paginator
  "Create a paginator component.

   Options:
     :total-pages   - Total number of pages (default 1)
     :per-page      - Items per page (default 1)
     :page          - Current page, 0-indexed (default 0)
     :type          - :dots or :arabic (default :dots)
     :active-dot    - String for active page dot (default \"•\")
     :inactive-dot  - String for inactive page dot (default \"○\")
     :arabic-format - Format string for arabic type (default \"%d/%d\")
     :active-style  - Style for active indicator
     :inactive-style - Style for inactive indicator
     :id            - Unique ID"
  [& {:keys [total-pages per-page page type
             active-dot inactive-dot arabic-format
             active-style inactive-style id]
      :or {total-pages 1
           per-page 1
           page 0
           type :dots
           active-dot "•"
           inactive-dot "○"
           arabic-format "%d/%d"
           id (rand-int 1000000)}}]
  {:type :paginator
   :id id
   :total-pages (max 1 total-pages)
   :per-page (max 1 per-page)
   :page (max 0 (min page (dec (max 1 total-pages))))
   :display-type type
   :active-dot active-dot
   :inactive-dot inactive-dot
   :arabic-format arabic-format
   :active-style (or active-style (style/style :bold true))
   :inactive-style inactive-style
   :keys default-keys})

;; ---------------------------------------------------------------------------
;; Paginator Accessors
;; ---------------------------------------------------------------------------

(defn page
  "Get current page (0-indexed)."
  [pager]
  (:page pager))

(defn total-pages
  "Get total number of pages."
  [pager]
  (:total-pages pager))

(defn per-page
  "Get items per page."
  [pager]
  (:per-page pager))

(defn set-page
  "Set current page."
  [pager p]
  (assoc pager :page (max 0 (min p (dec (:total-pages pager))))))

(defn set-total-pages
  "Set total pages."
  [pager n]
  (let [total (max 1 n)]
    (-> pager
        (assoc :total-pages total)
        (update :page #(min % (dec total))))))

(defn set-per-page
  "Set items per page."
  [pager n]
  (assoc pager :per-page (max 1 n)))

(defn set-total-items
  "Set total pages based on item count."
  [pager total-items]
  (let [per (:per-page pager)
        pages (if (pos? total-items)
                (+ (quot total-items per)
                   (if (pos? (rem total-items per)) 1 0))
                1)]
    (set-total-pages pager pages)))

;; ---------------------------------------------------------------------------
;; Paginator Navigation
;; ---------------------------------------------------------------------------

(defn on-first-page?
  "Check if on first page."
  [pager]
  (zero? (:page pager)))

(defn on-last-page?
  "Check if on last page."
  [pager]
  (= (:page pager) (dec (:total-pages pager))))

(defn prev-page
  "Go to previous page."
  [pager]
  (if (on-first-page? pager)
    pager
    (update pager :page dec)))

(defn next-page
  "Go to next page."
  [pager]
  (if (on-last-page? pager)
    pager
    (update pager :page inc)))

(defn go-to-first
  "Go to first page."
  [pager]
  (assoc pager :page 0))

(defn go-to-last
  "Go to last page."
  [pager]
  (assoc pager :page (dec (:total-pages pager))))

;; ---------------------------------------------------------------------------
;; Slice Bounds
;; ---------------------------------------------------------------------------

(defn slice-bounds
  "Get [start end] bounds for slicing items for current page."
  [pager total-items]
  (let [{:keys [page per-page]} pager
        start (* page per-page)
        end (min (+ start per-page) total-items)]
    [start end]))

(defn items-on-page
  "Get number of items on current page."
  [pager total-items]
  (let [[start end] (slice-bounds pager total-items)]
    (- end start)))

;; ---------------------------------------------------------------------------
;; Paginator Update
;; ---------------------------------------------------------------------------

(defn paginator-update
  "Update paginator state based on a message.
   Returns [new-pager cmd] or [pager nil] if message not handled."
  [pager msg]
  (let [keys (:keys pager)]
    (cond
      (matches-binding? msg (:next-page keys))
      [(next-page pager) nil]

      (matches-binding? msg (:prev-page keys))
      [(prev-page pager) nil]

      :else
      [pager nil])))

;; ---------------------------------------------------------------------------
;; Paginator View
;; ---------------------------------------------------------------------------

(defn- dots-view
  "Render paginator as dots."
  [pager]
  (let [{:keys [page total-pages active-dot inactive-dot
                active-style inactive-style]} pager]
    (apply str
           (for [i (range total-pages)]
             (if (= i page)
               (if active-style
                 (style/render active-style active-dot)
                 active-dot)
               (if inactive-style
                 (style/render inactive-style inactive-dot)
                 inactive-dot))))))

(defn- arabic-view
  "Render paginator as page numbers."
  [pager]
  (let [{:keys [page total-pages arabic-format active-style]} pager
        text (format arabic-format (inc page) total-pages)]
    (if active-style
      (style/render active-style text)
      text)))

(defn paginator-view
  "Render the paginator to a string."
  [pager]
  (if (= :dots (:display-type pager))
    (dots-view pager)
    (arabic-view pager)))

;; ---------------------------------------------------------------------------
;; Paginator Init
;; ---------------------------------------------------------------------------

(defn paginator-init
  "Initialize the paginator, returns [pager cmd]."
  [pager]
  [pager nil])
