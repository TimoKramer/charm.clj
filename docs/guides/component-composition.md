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
  [{:input (charm/text-input :prompt "Search: ")
    :list (charm/item-list ["Apple" "Banana" "Cherry"])
    :help (charm/help [["/" "search"] ["Enter" "select"] ["q" "quit"]])}
   nil])
```

## Pattern: Mode-Based Routing

Use a mode or focus field to route messages:

```clojure
(defn init []
  [{:mode :browse  ; :browse or :search
    :input (charm/text-input :prompt "Search: " :focused false)
    :list (charm/item-list items)}
   nil])

(defn update-fn [state msg]
  (case (:mode state)
    :search (handle-search-mode state msg)
    :browse (handle-browse-mode state msg)))

(defn handle-search-mode [state msg]
  (cond
    ;; Escape exits search mode
    (charm/key-match? msg "esc")
    [(-> state
         (assoc :mode :browse)
         (update :input charm/text-input-blur))
     nil]

    ;; Enter confirms search
    (charm/key-match? msg "enter")
    [(-> state
         (assoc :mode :browse)
         (update :input charm/text-input-blur)
         (filter-list-by-search))
     nil]

    ;; Pass to text input
    :else
    (let [[input cmd] (charm/text-input-update (:input state) msg)]
      [(assoc state :input input) cmd])))

(defn handle-browse-mode [state msg]
  (cond
    (charm/key-match? msg "q")
    [state charm/quit-cmd]

    ;; / enters search mode
    (charm/key-match? msg "/")
    [(-> state
         (assoc :mode :search)
         (update :input charm/text-input-focus))
     nil]

    ;; Pass to list
    :else
    (let [[list cmd] (charm/list-update (:list state) msg)]
      [(assoc state :list list) cmd])))
```

## Pattern: Component Coordination

Components often need to affect each other:

```clojure
(defn filter-list-by-search [state]
  (let [query (charm/text-input-value (:input state))
        all-items (:all-items state)
        filtered (if (empty? query)
                   all-items
                   (filter #(clojure.string/includes?
                             (clojure.string/lower-case (:title %))
                             (clojure.string/lower-case query))
                           all-items))]
    (update state :list charm/list-set-items filtered)))
```

## Example: File Browser

A complete example combining list, text-input for filtering, and help:

```clojure
(ns file-browser.core
  (:require [charm.core :as charm]
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
      :list (charm/item-list (files->list-items files) :height 15)
      :filter-input (charm/text-input :prompt "Filter: " :focused false)
      :help (charm/help [["j/k" "navigate"]
                         ["Enter" "open"]
                         ["/" "filter"]
                         ["q" "quit"]])}
     nil]))

(defn apply-filter [state]
  (let [query (str/lower-case (charm/text-input-value (:filter-input state)))
        files (if (empty? query)
                (:all-files state)
                (filter #(str/includes? (str/lower-case (:title %)) query)
                        (:all-files state)))]
    (update state :list charm/list-set-items (files->list-items files))))

(defn navigate-to [state path]
  (let [files (list-files path)]
    (-> state
        (assoc :path path)
        (assoc :all-files files)
        (assoc :list (charm/item-list (files->list-items files) :height 15))
        (update :filter-input charm/text-input-reset))))

(defn handle-browse [state msg]
  (cond
    (charm/key-match? msg "q")
    [state charm/quit-cmd]

    (charm/key-match? msg "/")
    [(-> state
         (assoc :mode :filter)
         (update :filter-input charm/text-input-focus))
     nil]

    (charm/key-match? msg "enter")
    (let [selected (:data (charm/list-selected-item (:list state)))]
      (if (:directory? selected)
        [(navigate-to state (:path selected)) nil]
        [state nil]))

    (charm/key-match? msg "backspace")
    (let [parent (.getParent (io/file (:path state)))]
      (if parent
        [(navigate-to state parent) nil]
        [state nil]))

    :else
    (let [[list cmd] (charm/list-update (:list state) msg)]
      [(assoc state :list list) cmd])))

(defn handle-filter [state msg]
  (cond
    (or (charm/key-match? msg "esc")
        (charm/key-match? msg "enter"))
    [(-> state
         (assoc :mode :browse)
         (update :filter-input charm/text-input-blur))
     nil]

    :else
    (let [[input cmd] (charm/text-input-update (:filter-input state) msg)]
      [(-> state
           (assoc :filter-input input)
           apply-filter)
       cmd])))

(defn update-fn [state msg]
  (case (:mode state)
    :filter (handle-filter state msg)
    :browse (handle-browse state msg)))

(defn view [state]
  (str (charm/render (charm/style :fg charm/cyan :bold true) "File Browser")
       "\n"
       (charm/render (charm/style :fg 240) (:path state))
       "\n\n"
       (charm/list-view (:list state))
       "\n\n"
       (when (= :filter (:mode state))
         (str (charm/text-input-view (:filter-input state)) "\n\n"))
       (charm/help-view (:help state))))

(defn -main [& _args]
  (charm/run {:init init
              :update update-fn
              :view view
              :alt-screen true}))
```

## Pattern: Tick-Based Components

When using spinner or timer, handle their ticks:

```clojure
(defn init []
  (let [[spinner cmd] (charm/spinner-init (charm/spinner :dots))
        [timer timer-cmd] (charm/timer-init (charm/timer :timeout 30000))]
    [{:spinner spinner
      :timer timer}
     (charm/batch cmd timer-cmd)]))

(defn update-fn [state msg]
  (cond
    (charm/key-match? msg "q")
    [state charm/quit-cmd]

    ;; Route spinner ticks
    (= :spinner-tick (:type msg))
    (let [[spinner cmd] (charm/spinner-update (:spinner state) msg)]
      [(assoc state :spinner spinner) cmd])

    ;; Route timer ticks
    (= :timer-tick (:type msg))
    (let [[timer cmd] (charm/timer-update (:timer state) msg)]
      [(assoc state :timer timer) cmd])

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
  (let [help (charm/help (get-help-bindings (:mode state)))]
    (str (main-content-view state)
         "\n\n"
         (charm/help-view help))))
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
(let [[spinner1 cmd1] (charm/spinner-init s1)
      [spinner2 cmd2] (charm/spinner-init s2)
      [timer cmd3] (charm/timer-init timer)]
  [{:s1 spinner1 :s2 spinner2 :timer timer}
   (charm/batch cmd1 cmd2 cmd3)])
```
