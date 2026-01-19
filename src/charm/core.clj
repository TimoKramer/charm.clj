(ns charm.core
  "charm.clj - A Clojure TUI library inspired by Bubble Tea.

   This is the main entry point for charm.clj applications.

   Example usage:
   ```clojure
   (require '[charm.core :as charm])

   (defn init []
     {:count 0})

   (defn update-fn [state msg]
     (cond
       (charm/key-match? msg \"k\") [(update state :count inc) nil]
       (charm/key-match? msg \"j\") [(update state :count dec) nil]
       (charm/key-match? msg \"q\") [state charm/quit-cmd]
       :else [state nil]))

   (defn view [state]
     (str \"Count: \" (:count state) \"\\n\\n(j/k to change, q to quit)\"))

   (charm/run {:init init :update update-fn :view view})
   ```"
  (:require [charm.terminal :as term]
            [charm.message :as msg]))

;; ---------------------------------------------------------------------------
;; Re-exported from charm.message
;; ---------------------------------------------------------------------------

(def key-press msg/key-press)
(def window-size msg/window-size)
(def quit msg/quit)
(def error msg/error)
(def mouse msg/mouse)

(def key-press? msg/key-press?)
(def window-size? msg/window-size?)
(def quit? msg/quit?)
(def error? msg/error?)
(def mouse? msg/mouse?)

(def key-match? msg/key-match?)
(def ctrl? msg/ctrl?)
(def alt? msg/alt?)
(def shift? msg/shift?)

;; ---------------------------------------------------------------------------
;; Re-exported from charm.terminal
;; ---------------------------------------------------------------------------

(def create-terminal term/create-terminal)
(def get-size term/get-size)

;; ---------------------------------------------------------------------------
;; Commands (functions that return messages)
;; ---------------------------------------------------------------------------

(defn quit-cmd
  "Command that returns a quit message."
  []
  (msg/quit))

(defn window-size-cmd
  "Command that queries terminal size and returns a window-size message."
  [terminal]
  (fn []
    (let [{:keys [width height]} (term/get-size terminal)]
      (msg/window-size width height))))

;; ---------------------------------------------------------------------------
;; Program runner (placeholder for Phase 6)
;; ---------------------------------------------------------------------------

(defn run
  "Run a charm.clj TUI program.

   Options map:
     :init   - (fn [] state) - returns initial state
     :update - (fn [state msg] [new-state cmd]) - handles messages
     :view   - (fn [state] string) - renders state to string

   Optional:
     :alt-screen? - use alternate screen buffer (default true)
     :mouse?      - enable mouse support (default false)

   Note: Full implementation coming in Phase 6."
  [{:keys [init update view alt-screen? mouse?]
    :or {alt-screen? true mouse? false}}]
  ;; Placeholder - will be implemented in Phase 6
  (println "charm.core/run is a placeholder - full implementation in Phase 6")
  (println "For now, use charm.terminal directly for testing:"))
