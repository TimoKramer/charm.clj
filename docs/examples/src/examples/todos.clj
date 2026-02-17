(ns examples.todos
  "Full todo application demonstrating component composition:
   list + text-input + help working together."
  (:require [charm.core :as charm]
            [charm.components.help :as help]
            [clojure.string :as str]))

(def title-style
  (charm/style :fg charm/magenta :bold true))

(def input-style
  (charm/style :fg charm/cyan))

(def done-style
  (charm/style :fg 240 :strikethrough true))

(def pending-style
  (charm/style :fg charm/white))

(def count-style
  (charm/style :fg charm/yellow))

(def hint-style
  (charm/style :fg 240))

;; Help bindings
(def help-bindings
  (charm/help-from-pairs
   "j/k" "up/down"
   "a" "add todo"
   "x" "toggle done"
   "d" "delete"
   "?" "help"
   "q" "quit"))

(defn make-todo
  "Create a todo item."
  [text]
  {:title text
   :done false
   :id (rand-int 1000000)})

(defn todo->list-item
  "Convert todo to list item display format."
  [todo]
  (let [checkbox (if (:done todo) "[x]" "[ ]")
        style (if (:done todo) done-style pending-style)]
    {:title (str checkbox " " (:title todo))
     :data todo}))

(defn update-list-items
  "Update the list component with current todos."
  [state]
  (let [todos (:todos state)
        items (mapv todo->list-item todos)]
    (assoc state :todo-list (charm/list-set-items (:todo-list state) items))))

(defn init []
  (let [initial-todos [(make-todo "Learn charm.clj")
                       (make-todo "Build a TUI app")
                       (make-todo "Have fun!")]
        items (mapv todo->list-item initial-todos)]
    [{:todos initial-todos
      :todo-list (charm/item-list items
                                  :height 10
                                  :cursor-prefix "> "
                                  :item-prefix "  ")
      :input (charm/text-input :prompt "New todo: "
                               :placeholder "What needs to be done?"
                               :focused false)
      :mode :browse  ; :browse or :add
      :help (charm/help help-bindings :width 60)}
     nil]))

(defn enter-add-mode
  "Switch to add mode."
  [state]
  (-> state
      (assoc :mode :add)
      (update :input charm/text-input-focus)))

(defn exit-add-mode
  "Switch back to browse mode."
  [state]
  (-> state
      (assoc :mode :browse)
      (update :input charm/text-input-blur)
      (update :input charm/text-input-reset)))

(defn add-todo
  "Add new todo from input."
  [state]
  (let [text (str/trim (charm/text-input-value (:input state)))]
    (if (str/blank? text)
      (exit-add-mode state)
      (-> state
          (update :todos conj (make-todo text))
          exit-add-mode
          update-list-items))))

(defn toggle-selected
  "Toggle done state of selected todo."
  [state]
  (let [idx (charm/list-selected-index (:todo-list state))]
    (if (and idx (< idx (count (:todos state))))
      (-> state
          (update-in [:todos idx :done] not)
          update-list-items)
      state)))

(defn delete-selected
  "Delete the selected todo."
  [state]
  (let [idx (charm/list-selected-index (:todo-list state))]
    (if (and idx (< idx (count (:todos state))))
      (-> state
          (update :todos (fn [todos]
                           (into (subvec todos 0 idx)
                                 (subvec todos (inc idx)))))
          update-list-items)
      state)))

(defn update-fn [state msg]
  (cond
    ;; Quit (only in browse mode, or ctrl+c/esc always)
    (or (charm/key-match? msg "ctrl+c")
        (and (= (:mode state) :browse)
             (charm/key-match? msg "q")))
    [state charm/quit-cmd]

    ;; Escape always exits add mode or quits in browse mode
    (charm/key-match? msg "esc")
    (if (= (:mode state) :add)
      [(exit-add-mode state) nil]
      [state charm/quit-cmd])

    ;; In add mode
    (= (:mode state) :add)
    (cond
      ;; Enter to submit
      (charm/key-match? msg "enter")
      [(add-todo state) nil]

      ;; Pass to text input
      :else
      (let [[new-input cmd] (charm/text-input-update (:input state) msg)]
        [(assoc state :input new-input) cmd]))

    ;; In browse mode
    :else
    (cond
      ;; A to add new todo
      (charm/key-match? msg "a")
      [(enter-add-mode state) nil]

      ;; X to toggle done
      (charm/key-match? msg "x")
      [(toggle-selected state) nil]

      ;; D to delete
      (charm/key-match? msg "d")
      [(delete-selected state) nil]

      ;; ? to toggle help
      (charm/key-match? msg "?")
      [(update state :help charm/help-toggle-show-all) nil]

      ;; Pass navigation to list
      :else
      (let [[new-list cmd] (charm/list-update (:todo-list state) msg)]
        [(assoc state :todo-list new-list) cmd]))))

(defn count-todos
  "Count pending and done todos."
  [todos]
  (let [done (count (filter :done todos))
        pending (- (count todos) done)]
    {:pending pending :done done :total (count todos)}))

(defn view [state]
  (let [{:keys [todos todo-list input mode help]} state
        counts (count-todos todos)
        show-full-help? (:show-all help)]
    (str (charm/render title-style "Todo List") "\n"
         (charm/render count-style
                       (format "%d pending, %d done"
                               (:pending counts)
                               (:done counts)))
         "\n\n"

         ;; Todo list
         (if (empty? todos)
           (charm/render hint-style "No todos yet. Press 'a' to add one!")
           (charm/list-view todo-list))
         "\n\n"

         ;; Input (visible in add mode)
         (when (= mode :add)
           (str (charm/text-input-view input) "\n\n"))

         ;; Help
         (if show-full-help?
           (str (charm/render (charm/style :bold true) "Keyboard Shortcuts") "\n"
                (help/full-help-view help) "\n\n"
                (charm/render hint-style "Press ? to hide help"))
           (help/short-help-view help)))))

(defn -main [& _args]
  (charm/run {:init init
              :update update-fn
              :view view
              :alt-screen true}))
