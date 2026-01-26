(ns charm.components.spinner
  "Animated spinner component.

   Usage:
     (def my-spinner (spinner :dots))

     ;; In update function:
     (spinner-update my-spinner msg)

     ;; In view function:
     (spinner-view my-spinner)"
  (:require [charm.style.core :as style]))

;; ---------------------------------------------------------------------------
;; Spinner Types
;; ---------------------------------------------------------------------------

(def spinner-types
  "Predefined spinner animations with frames and interval."
  {:line      {:frames ["|" "/" "-" "\\"]
               :interval 100}

   :dots      {:frames ["⠋" "⠙" "⠹" "⠸" "⠼" "⠴" "⠦" "⠧" "⠇" "⠏"]
               :interval 80}

   :dot       {:frames ["⣾" "⣽" "⣻" "⢿" "⡿" "⣟" "⣯" "⣷"]
               :interval 100}

   :jump      {:frames ["⢄" "⢂" "⢁" "⡁" "⡈" "⡐" "⡠"]
               :interval 100}

   :pulse     {:frames ["█" "▓" "▒" "░"]
               :interval 125}

   :points    {:frames ["∙∙∙" "●∙∙" "∙●∙" "∙∙●"]
               :interval 140}

   :globe     {:frames ["🌍" "🌎" "🌏"]
               :interval 250}

   :moon      {:frames ["🌑" "🌒" "🌓" "🌔" "🌕" "🌖" "🌗" "🌘"]
               :interval 125}

   :monkey    {:frames ["🙈" "🙉" "🙊"]
               :interval 300}

   :meter     {:frames ["▱▱▱" "▰▱▱" "▰▰▱" "▰▰▰" "▰▰▱" "▰▱▱" "▱▱▱"]
               :interval 140}

   :hamburger {:frames ["☱" "☲" "☴" "☲"]
               :interval 300}

   :ellipsis  {:frames ["" "." ".." "..."]
               :interval 300}

   :arrows    {:frames ["←" "↖" "↑" "↗" "→" "↘" "↓" "↙"]
               :interval 100}

   :bouncing-bar {:frames ["[    ]" "[=   ]" "[==  ]" "[=== ]" "[ ===]" "[  ==]" "[   =]" "[    ]"]
                  :interval 100}

   :clock     {:frames ["🕐" "🕑" "🕒" "🕓" "🕔" "🕕" "🕖" "🕗" "🕘" "🕙" "🕚" "🕛"]
               :interval 100}})

;; ---------------------------------------------------------------------------
;; Tick Message
;; ---------------------------------------------------------------------------

(defn tick-msg
  "Create a spinner tick message."
  [spinner-id tag]
  {:type :spinner-tick
   :spinner-id spinner-id
   :tag tag})

(defn tick-msg?
  "Check if a message is a spinner tick."
  [msg]
  (= :spinner-tick (:type msg)))

;; ---------------------------------------------------------------------------
;; Spinner Creation
;; ---------------------------------------------------------------------------

(defn spinner
  "Create a spinner component.

   Type can be a keyword like :dots, :line, :moon, etc.
   or a map with :frames and :interval keys.

   Options:
     :style - Style to apply to spinner (optional)
     :id    - Unique ID for this spinner (optional)"
  [type & {:keys [style id] :or {id (rand-int 1000000)}}]
  (let [spinner-type (if (keyword? type)
                       (get spinner-types type (:dots spinner-types))
                       type)]
    {:type :spinner
     :spinner-type spinner-type
     :frame 0
     :tag 0
     :id id
     :style style}))

;; ---------------------------------------------------------------------------
;; Spinner Commands
;; ---------------------------------------------------------------------------

(defn- tick-cmd
  "Create a command that sends a tick message after the interval."
  [spinner]
  (let [{:keys [id tag spinner-type]} spinner
        interval (long (:interval spinner-type))]
    {:type :cmd
     :fn (fn []
           (Thread/sleep interval)
           (tick-msg id tag))}))

(defn spinner-init
  "Initialize the spinner, returns [spinner cmd].
   Call this to start the animation."
  [spinner]
  [spinner (tick-cmd spinner)])

;; ---------------------------------------------------------------------------
;; Spinner Update
;; ---------------------------------------------------------------------------

(defn spinner-update
  "Update spinner state based on a message.
   Returns [new-spinner cmd] or [spinner nil] if message not handled."
  [spinner msg]
  (if (tick-msg? msg)
    (let [{msg-id :spinner-id msg-tag :tag} msg
          {:keys [id spinner-type]} spinner
          spinner-tag (:tag spinner)]
      ;; Only handle ticks for this spinner with matching tag
      (if (and (= msg-id id)
               (= msg-tag spinner-tag))
        (let [frames (:frames spinner-type)
              next-frame (mod (inc (:frame spinner)) (count frames))
              new-spinner (-> spinner
                              (assoc :frame next-frame)
                              (update :tag inc))]
          [new-spinner (tick-cmd new-spinner)])
        [spinner nil]))
    [spinner nil]))

;; ---------------------------------------------------------------------------
;; Spinner View
;; ---------------------------------------------------------------------------

(defn spinner-view
  "Render the spinner to a string."
  [spinner]
  (let [{:keys [frame spinner-type style]} spinner
        frames (:frames spinner-type)
        current-frame (get frames frame (first frames))]
    (if style
      (style/render style current-frame)
      current-frame)))

;; ---------------------------------------------------------------------------
;; Convenience
;; ---------------------------------------------------------------------------

(defn spinning?
  "Check if a message is for this spinner."
  [spinner msg]
  (and (tick-msg? msg)
       (= (:id spinner) (:spinner-id msg))))
