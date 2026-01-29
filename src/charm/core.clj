(ns charm.core
  "charm.clj - A Clojure TUI library inspired by Bubble Tea.

   This is the main entry point for charm.clj applications.

   Example usage:
   ```clojure
   (require '[charm.core :as charm])

   (defn update-fn [state msg]
     (cond
       (charm/key-match? msg \"k\") [(update state :count inc) nil]
       (charm/key-match? msg \"j\") [(update state :count dec) nil]
       (charm/key-match? msg \"q\") [state charm/quit-cmd]
       :else [state nil]))

   (defn view [state]
     (str \"Count: \" (:count state) \"\\n\\n(j/k to change, q to quit)\"))

   (charm/run {:init {:count 0}
               :update update-fn
               :view view})
   ```"
  (:require
   [charm.components.help :as help]
   [charm.components.list :as list-comp]
   [charm.components.paginator :as paginator]
   [charm.components.progress :as progress]
   [charm.components.spinner :as spinner]
   [charm.components.text-input :as text-input]
   [charm.components.timer :as timer]
   [charm.message :as msg]
   [charm.program :as prog]
   [charm.style.core :as style]
   [charm.terminal :as term]))

;; ---------------------------------------------------------------------------
;; Re-exported from charm.message
;; ---------------------------------------------------------------------------

(def key-press msg/key-press)
(def window-size msg/window-size)
(def quit msg/quit)
(def error msg/error)
(def mouse msg/mouse)
(def focus msg/focus)
(def blur msg/blur)

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
;; Re-exported from charm.program
;; ---------------------------------------------------------------------------

(def cmd prog/cmd)
(def batch prog/batch)
(def sequence-cmds prog/sequence-cmds)
(def quit-cmd prog/quit-cmd)
(def run prog/run)
(def run-simple prog/run-simple)

;; ---------------------------------------------------------------------------
;; Re-exported from charm.style
;; ---------------------------------------------------------------------------

(def style style/style)
(def render style/render)
(def styled style/styled)

;; Colors
(def rgb style/rgb)
(def hex style/hex)
(def ansi style/ansi)
(def ansi256 style/ansi256)

(def black style/black)
(def red style/red)
(def green style/green)
(def yellow style/yellow)
(def blue style/blue)
(def magenta style/magenta)
(def cyan style/cyan)
(def white style/white)

;; Borders
(def normal-border style/normal-border)
(def rounded-border style/rounded-border)
(def thick-border style/thick-border)
(def double-border style/double-border)

;; Layout
(def join-horizontal style/join-horizontal)
(def join-vertical style/join-vertical)

;; ---------------------------------------------------------------------------
;; Convenience Macros
;; ---------------------------------------------------------------------------

(defmacro defprogram
  "Define a TUI program with init, update, and view functions.

   Usage:
     (defprogram my-app
       :init {:count 0}
       :update (fn [state msg] ...)
       :view (fn [state] ...))"
  [name & {:keys [init update view] :as opts}]
  `(def ~name
     {:init ~init
      :update ~update
      :view ~view}))

;; ---------------------------------------------------------------------------
;; Re-exported from charm.components.spinner
;; ---------------------------------------------------------------------------

(def spinner spinner/spinner)
(def spinner-init spinner/spinner-init)
(def spinner-update spinner/spinner-update)
(def spinner-view spinner/spinner-view)
(def spinner-types spinner/spinner-types)
(def spinning? spinner/spinning?)

;; ---------------------------------------------------------------------------
;; Re-exported from charm.components.text-input
;; ---------------------------------------------------------------------------

(def text-input text-input/text-input)
(def text-input-init text-input/text-input-init)
(def text-input-update text-input/text-input-update)
(def text-input-view text-input/text-input-view)
(def text-input-value text-input/value)
(def text-input-set-value text-input/set-value)
(def text-input-focus text-input/focus)
(def text-input-blur text-input/blur)
(def text-input-reset text-input/reset)

;; Echo modes
(def echo-normal text-input/echo-normal)
(def echo-password text-input/echo-password)
(def echo-none text-input/echo-none)

;; ---------------------------------------------------------------------------
;; Re-exported from charm.components.list
;; ---------------------------------------------------------------------------

(def item-list list-comp/item-list)
(def list-init list-comp/list-init)
(def list-update list-comp/list-update)
(def list-view list-comp/list-view)
(def list-items list-comp/items)
(def list-selected-item list-comp/selected-item)
(def list-selected-index list-comp/selected-index)
(def list-set-items list-comp/set-items)
(def list-select list-comp/select)

;; ---------------------------------------------------------------------------
;; Re-exported from charm.components.paginator
;; ---------------------------------------------------------------------------

(def paginator paginator/paginator)
(def paginator-init paginator/paginator-init)
(def paginator-update paginator/paginator-update)
(def paginator-view paginator/paginator-view)
(def paginator-page paginator/page)
(def paginator-total-pages paginator/total-pages)
(def paginator-set-page paginator/set-page)
(def paginator-set-total-pages paginator/set-total-pages)
(def paginator-next-page paginator/next-page)
(def paginator-prev-page paginator/prev-page)

;; ---------------------------------------------------------------------------
;; Re-exported from charm.components.timer
;; ---------------------------------------------------------------------------

(def timer timer/timer)
(def timer-init timer/timer-init)
(def timer-update timer/timer-update)
(def timer-view timer/timer-view)
(def timer-timeout timer/timeout)
(def timer-running? timer/running?)
(def timer-timed-out? timer/timed-out?)
(def timer-start timer/start)
(def timer-stop timer/stop)
(def timer-toggle timer/toggle)

;; ---------------------------------------------------------------------------
;; Re-exported from charm.components.progress
;; ---------------------------------------------------------------------------

(def progress-bar progress/progress-bar)
(def progress-init progress/progress-init)
(def progress-update progress/progress-update)
(def progress-view progress/progress-view)
(def progress-percent progress/percent)
(def progress-set progress/set-progress)
(def progress-increment progress/increment)
(def progress-decrement progress/decrement)
(def progress-complete? progress/complete?)
(def progress-bar-styles progress/bar-styles)

;; ---------------------------------------------------------------------------
;; Re-exported from charm.components.help
;; ---------------------------------------------------------------------------

(def help help/help)
(def help-init help/help-init)
(def help-update help/help-update)
(def help-view help/help-view)
(def help-bindings help/bindings)
(def help-set-bindings help/set-bindings)
(def help-toggle-show-all help/toggle-show-all)
(def help-from-pairs help/from-pairs)
