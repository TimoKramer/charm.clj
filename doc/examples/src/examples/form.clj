(ns examples.form
  "Login form demonstrating text-input with multiple fields and echo modes."
  (:require
   [charm.components.text-input :as text-input]
   [charm.message :as msg]
   [charm.program :as program]
   [charm.style.core :as style]
   [clojure.string :as str]))

(def title-style
  (style/style :fg style/magenta :bold true))

(def label-style
  (style/style :fg style/cyan :bold true))

(def hint-style
  (style/style :fg 240))

(def success-style
  (style/style :fg style/green :bold true))

(def error-style
  (style/style :fg style/red))

(defn init []
  [{:username (text-input/text-input :prompt ""
                                     :placeholder "Enter username"
                                     :focused true)
    :password (text-input/text-input :prompt ""
                                     :placeholder "Enter password"
                                     :echo-mode text-input/echo-password
                                     :focused false)
    :focused-field :username
    :submitted false
    :message nil}
   nil])

(defn focus-field
  "Focus a specific field and blur others."
  [state field]
  (-> state
      (update :username (if (= field :username) text-input/focus text-input/blur))
      (update :password (if (= field :password) text-input/focus text-input/blur))
      (assoc :focused-field field)))

(defn next-field
  "Move to the next field."
  [state]
  (case (:focused-field state)
    :username (focus-field state :password)
    :password (focus-field state :username)))

(defn validate-form
  "Validate the form and return error message or nil."
  [state]
  (let [username (text-input/value (:username state))
        password (text-input/value (:password state))]
    (cond
      (str/blank? username) "Username is required"
      (< (count username) 3) "Username must be at least 3 characters"
      (str/blank? password) "Password is required"
      (< (count password) 4) "Password must be at least 4 characters"
      :else nil)))

(defn submit-form
  "Attempt to submit the form."
  [state]
  (if-let [error (validate-form state)]
    (assoc state :message {:type :error :text error})
    (assoc state
           :submitted true
           :message {:type :success
                     :text (str "Welcome, " (text-input/value (:username state)) "!")})))

(defn update-fn [state msg]
  (cond
    ;; Quit on Ctrl+C or Esc
    (or (msg/key-match? msg "ctrl+c")
        (msg/key-match? msg "esc"))
    [state program/quit-cmd]

    ;; Quit on q only when submitted
    (and (:submitted state) (msg/key-match? msg "q"))
    [state program/quit-cmd]

    ;; Already submitted, ignore other input
    (:submitted state)
    [state nil]

    ;; Tab or Down to next field
    (or (msg/key-match? msg "tab")
        (msg/key-match? msg :down))
    [(next-field state) nil]

    ;; Shift+Tab or Up to previous field
    (or (msg/key-match? msg "shift+tab")
        (msg/key-match? msg :up))
    [(next-field state) nil]

    ;; Enter to submit
    (msg/key-match? msg "enter")
    [(submit-form state) nil]

    ;; Pass input to focused field
    :else
    (let [field (:focused-field state)
          input (get state field)
          [new-input cmd] (text-input/text-input-update input msg)]
      [(-> state
           (assoc field new-input)
           (assoc :message nil))
       cmd])))

(defn render-field
  "Render a form field with label and focus indicator."
  [label input focused?]
  (let [indicator (if focused? "> " "  ")
        label-str (style/render label-style (format "%-10s" label))]
    (str indicator label-str (text-input/text-input-view input))))

(defn view [state]
  (let [{:keys [username password focused-field submitted message]} state]
    (str (style/render title-style "Login Form") "\n\n"

         (render-field "Username:" username (= focused-field :username)) "\n"
         (render-field "Password:" password (= focused-field :password)) "\n\n"

         (when message
           (str (style/render (if (= (:type message) :error) error-style success-style)
                              (:text message))
                "\n\n"))

         (if submitted
           (style/render hint-style "Press q to quit")
           (style/render hint-style "Tab: next field  Enter: submit  Esc: quit")))))

(defn -main [& _args]
  (program/run {:init init
                :update update-fn
                :view view
                :alt-screen true}))
