# Component Composition

This guide shows how to combine multiple charm.clj components into a cohesive application.

## The Challenge

When building real applications, you need to:

1. Manage multiple component states
2. Route messages to the right component
3. Coordinate component interactions
4. Combine component views

## Pattern: Component State in App State

Store each component's state as a key in your app state:

```clojure
(defn init []
  [{:input (text-input/text-input :prompt "Search: ")
    :list (item-list/item-list ["Apple" "Banana" "Cherry"])
    :help (help/help [["/" "search"] ["Enter" "select"] ["q" "quit"]])}
   nil])
```

## Pattern: Mode-Based Routing

Use a mode or focus field to route messages:

```clojure
(defn init []
  [{:mode :browse  ; :browse or :search
    :input (text-input/text-input :prompt "Search: " :focused false)
    :list (item-list/item-list items)}
   nil])

(defn update-fn [state msg]
  (case (:mode state)
    :search (handle-search-mode state msg)
    :browse (handle-browse-mode state msg)))

(defn handle-search-mode [state msg]
  (cond
    ;; Escape exits search mode
    (msg/key-match? msg "esc")
    [(-> state
         (assoc :mode :browse)
         (update :input text-input/blur))
     nil]

    ;; Enter confirms search
    (msg/key-match? msg "enter")
    [(-> state
         (assoc :mode :browse)
         (update :input text-input/blur)
         (filter-list-by-search))
     nil]

    ;; Pass to text input
    :else
    (let [[input cmd] (text-input/text-input-update (:input state) msg)]
      [(assoc state :input input) cmd])))

(defn handle-browse-mode [state msg]
  (cond
    (msg/key-match? msg "q")
    [state program/quit-cmd]

    ;; / enters search mode
    (msg/key-match? msg "/")
    [(-> state
         (assoc :mode :search)
         (update :input text-input/focus))
     nil]

    ;; Pass to list
    :else
    (let [[list cmd] (item-list/list-update (:list state) msg)]
      [(assoc state :list list) cmd])))
```

## Pattern: Component Coordination

Components often need to affect each other:

```clojure
(defn filter-list-by-search [state]
  (let [query (text-input/value (:input state))
        all-items (:all-items state)
        filtered (if (empty? query)
                   all-items
                   (filter #(clojure.string/includes?
                             (clojure.string/lower-case (:title %))
                             (clojure.string/lower-case query))
                           all-items))]
    (update state :list item-list/set-items filtered)))
```

## Example: File Browser

A complete example combining list, text-input for filtering, and help:

```clojure
(ns file-browser.core
  (:require
   [charm.components.help :as help]
   [charm.components.list :as item-list]
   [charm.components.text-input :as text-input]
   [charm.message :as msg]
   [charm.program :as program]
   [charm.style.core :as style]
   [clojure.java.io :as io]
   [clojure.string :as str]))

;; State structure
;; {:mode :browse | :filter
;;  :path "/current/path"
;;  :all-files [...]
;;  :list <list-component>
;;  :filter-input <text-input-component>
;;  :help <help-component>}

(defn list-files [path]
  (->> (io/file path)
       (.listFiles)
       (map (fn [f]
              {:title (.getName f)
               :directory? (.isDirectory f)
               :path (.getAbsolutePath f)}))
       (sort-by (juxt (comp not :directory?) :title))
       vec))

(defn files->list-items [files]
  (mapv (fn [f]
          {:title (str (when (:directory? f) "/") (:title f))
           :data f})
        files))

(defn init []
  (let [path (System/getProperty "user.dir")
        files (list-files path)]
    [{:mode :browse
      :path path
      :all-files files
      :list (item-list/item-list (files->list-items files) :height 15)
      :filter-input (text-input/text-input :prompt "Filter: " :focused false)
      :help (help/help [["j/k" "navigate"]
                        ["Enter" "open"]
                        ["/" "filter"]
                        ["q" "quit"]])}
     nil]))

(defn apply-filter [state]
  (let [query (str/lower-case (text-input/value (:filter-input state)))
        files (if (empty? query)
                (:all-files state)
                (filter #(str/includes? (str/lower-case (:title %)) query)
                        (:all-files state)))]
    (update state :list item-list/set-items (files->list-items files))))

(defn navigate-to [state path]
  (let [files (list-files path)]
    (-> state
        (assoc :path path)
        (assoc :all-files files)
        (assoc :list (item-list/item-list (files->list-items files) :height 15))
        (update :filter-input text-input/reset))))

(defn handle-browse [state msg]
  (cond
    (msg/key-match? msg "q")
    [state program/quit-cmd]

    (msg/key-match? msg "/")
    [(-> state
         (assoc :mode :filter)
         (update :filter-input text-input/focus))
     nil]

    (msg/key-match? msg "enter")
    (let [selected (:data (item-list/selected-item (:list state)))]
      (if (:directory? selected)
        [(navigate-to state (:path selected)) nil]
        [state nil]))

    (msg/key-match? msg "backspace")
    (let [parent (.getParent (io/file (:path state)))]
      (if parent
        [(navigate-to state parent) nil]
        [state nil]))

    :else
    (let [[list cmd] (item-list/list-update (:list state) msg)]
      [(assoc state :list list) cmd])))

(defn handle-filter [state msg]
  (cond
    (or (msg/key-match? msg "esc")
        (msg/key-match? msg "enter"))
    [(-> state
         (assoc :mode :browse)
         (update :filter-input text-input/blur))
     nil]

    :else
    (let [[input cmd] (text-input/text-input-update (:filter-input state) msg)]
      [(-> state
           (assoc :filter-input input)
           apply-filter)
       cmd])))

(defn update-fn [state msg]
  (case (:mode state)
    :filter (handle-filter state msg)
    :browse (handle-browse state msg)))

(defn view [state]
  (str (style/render (style/style :fg style/cyan :bold true) "File Browser")
       "\n"
       (style/render (style/style :fg 240) (:path state))
       "\n\n"
       (item-list/list-view (:list state))
       "\n\n"
       (when (= :filter (:mode state))
         (str (text-input/text-input-view (:filter-input state)) "\n\n"))
       (help/short-help-view (:help state))))

(defn -main [& _args]
  (program/run {:init init
                :update update-fn
                :view view
                :alt-screen true}))
```

## Pattern: Tick-Based Components

When using spinner or timer, handle their ticks:

```clojure
(defn init []
  (let [[s cmd] (spinner/spinner-init (spinner/spinner :dots))
        [t timer-cmd] (timer/timer-init (timer/timer :timeout 30000))]
    [{:spinner s
      :timer t}
     (program/batch cmd timer-cmd)]))

(defn update-fn [state msg]
  (cond
    (msg/key-match? msg "q")
    [state program/quit-cmd]

    ;; Route spinner ticks
    (= :spinner-tick (:type msg))
    (let [[s cmd] (spinner/spinner-update (:spinner state) msg)]
      [(assoc state :spinner s) cmd])

    ;; Route timer ticks
    (= :timer-tick (:type msg))
    (let [[t cmd] (timer/timer-update (:timer state) msg)]
      [(assoc state :timer t) cmd])

    :else
    [state nil]))
```

## Pattern: Dynamic Help

Update help bindings based on mode:

```clojure
(defn get-help-bindings [mode]
  (case mode
    :browse [["j/k" "navigate"] ["Enter" "select"] ["/" "search"] ["q" "quit"]]
    :search [["Enter" "confirm"] ["Esc" "cancel"]]
    :edit [["Ctrl+S" "save"] ["Esc" "cancel"]]))

(defn view [state]
  (let [h (help/help (get-help-bindings (:mode state)))]
    (str (main-content-view state)
         "\n\n"
         (help/short-help-view h))))
```

## Tips

### Keep State Flat

Avoid deeply nested state:

```clojure
;; Good
{:list-cursor 0
 :list-items [...]
 :input-value ""}

;; Avoid
{:list {:cursor 0 :items [...]}
 :input {:value ""}}
```

### Extract Update Handlers

Split update logic by mode or component:

```clojure
(defn update-fn [state msg]
  (cond
    (global-key? msg) (handle-global state msg)
    (= :edit (:mode state)) (handle-edit state msg)
    (= :browse (:mode state)) (handle-browse state msg)
    :else [state nil]))
```

### Use Batch for Multiple Commands

When initializing multiple tick-based components:

```clojure
(let [[spinner1 cmd1] (spinner/spinner-init s1)
      [spinner2 cmd2] (spinner/spinner-init s2)
      [t cmd3] (timer/timer-init t)]
  [{:s1 spinner1 :s2 spinner2 :timer t}
   (program/batch cmd1 cmd2 cmd3)])
```
