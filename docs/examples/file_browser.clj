(ns examples.file-browser
  "File browser demonstrating list component with a details pane."
  (:require [charm.core :as charm]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io File]
           [java.text SimpleDateFormat]
           [java.util Date]))

(def title-style
  (charm/style :fg charm/magenta :bold true))

(def path-style
  (charm/style :fg charm/cyan))

(def dir-style
  (charm/style :fg charm/blue :bold true))

(def file-style
  (charm/style :fg charm/white))

(def detail-label-style
  (charm/style :fg 240))

(def detail-value-style
  (charm/style :fg charm/green))

(def hint-style
  (charm/style :fg 240))

(defn format-size
  "Format file size in human-readable format."
  [bytes]
  (cond
    (< bytes 1024) (str bytes " B")
    (< bytes (* 1024 1024)) (format "%.1f KB" (/ bytes 1024.0))
    (< bytes (* 1024 1024 1024)) (format "%.1f MB" (/ bytes 1024.0 1024.0))
    :else (format "%.1f GB" (/ bytes 1024.0 1024.0 1024.0))))

(defn format-date
  "Format timestamp as date string."
  [timestamp]
  (.format (SimpleDateFormat. "yyyy-MM-dd HH:mm") (Date. timestamp)))

(defn file-info
  "Get info map for a file."
  [^File f]
  {:name (.getName f)
   :path (.getAbsolutePath f)
   :directory? (.isDirectory f)
   :size (.length f)
   :modified (.lastModified f)
   :readable? (.canRead f)
   :writable? (.canWrite f)
   :hidden? (.isHidden f)})

(defn list-directory
  "List files in a directory."
  [path]
  (let [dir (io/file path)
        files (.listFiles dir)]
    (when files
      (->> files
           (map file-info)
           (sort-by (juxt (comp not :directory?) :name))
           vec))))

(defn file->list-item
  "Convert file info to list item format."
  [info]
  (let [icon (if (:directory? info) "/" "")]
    {:title (str (:name info) icon)
     :description (if (:directory? info)
                    "Directory"
                    (format-size (:size info)))
     :data info}))

(defn init []
  (let [start-path (System/getProperty "user.dir")
        files (list-directory start-path)
        items (mapv file->list-item files)]
    [{:current-path start-path
      :files files
      :file-list (charm/item-list items
                                  :height 15
                                  :show-descriptions true
                                  :cursor-style (charm/style :fg charm/cyan :bold true))}
     nil]))

(defn navigate-to
  "Navigate to a directory."
  [state path]
  (let [files (list-directory path)]
    (if files
      (let [items (mapv file->list-item files)]
        (-> state
            (assoc :current-path path)
            (assoc :files files)
            (assoc :file-list (charm/item-list items
                                               :height 15
                                               :show-descriptions true
                                               :cursor-style (charm/style :fg charm/cyan :bold true)))))
      state)))

(defn go-up
  "Navigate to parent directory."
  [state]
  (let [parent (.getParent (io/file (:current-path state)))]
    (if parent
      (navigate-to state parent)
      state)))

(defn enter-selected
  "Enter selected directory or do nothing for files."
  [state]
  (let [selected (charm/list-selected-item (:file-list state))]
    (when-let [info (:data selected)]
      (if (:directory? info)
        (navigate-to state (:path info))
        state))))

(defn update-fn [state msg]
  (cond
    ;; Quit
    (or (charm/key-match? msg "q")
        (charm/key-match? msg "ctrl+c")
        (charm/key-match? msg "esc"))
    [state charm/quit-cmd]

    ;; Go up directory
    (or (charm/key-match? msg "backspace")
        (charm/key-match? msg "h")
        (charm/key-match? msg :left))
    [(go-up state) nil]

    ;; Enter directory
    (or (charm/key-match? msg "enter")
        (charm/key-match? msg "l")
        (charm/key-match? msg :right))
    [(or (enter-selected state) state) nil]

    ;; Pass to list for navigation
    :else
    (let [[new-list cmd] (charm/list-update (:file-list state) msg)]
      [(assoc state :file-list new-list) cmd])))

(defn render-details
  "Render the details pane for selected file."
  [state]
  (if-let [selected (charm/list-selected-item (:file-list state))]
    (let [info (:data selected)]
      (str (charm/render detail-label-style "Name:     ")
           (charm/render detail-value-style (:name info)) "\n"
           (charm/render detail-label-style "Type:     ")
           (charm/render detail-value-style (if (:directory? info) "Directory" "File")) "\n"
           (charm/render detail-label-style "Size:     ")
           (charm/render detail-value-style (format-size (:size info))) "\n"
           (charm/render detail-label-style "Modified: ")
           (charm/render detail-value-style (format-date (:modified info))) "\n"
           (charm/render detail-label-style "Access:   ")
           (charm/render detail-value-style
                         (str (when (:readable? info) "r")
                              (when (:writable? info) "w")
                              (when (:hidden? info) " (hidden)")))))
    "No file selected"))

(defn view [state]
  (let [file-list-view (charm/list-view (:file-list state))
        details-view (render-details state)]
    (str (charm/render title-style "File Browser") "\n"
         (charm/render path-style (:current-path state)) "\n\n"
         (charm/join-horizontal :top
                                file-list-view
                                "    "
                                (str (charm/render (charm/style :bold true) "Details") "\n"
                                     (charm/render (charm/style :fg 240) (str (repeat 20 "-")) "\n")
                                     details-view))
         "\n\n"
         (charm/render hint-style "j/k: navigate  Enter/l: open  Backspace/h: back  q: quit"))))

(defn -main [& _args]
  (charm/run {:init init
              :update update-fn
              :view view
              :alt-screen true}))
