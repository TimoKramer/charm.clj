(ns charm.terminal
  "JLine terminal wrapper for charm.clj"
  (:import [org.jline.terminal Terminal TerminalBuilder]
           [org.jline.utils InfoCmp$Capability]))

(defn create-terminal
  "Create a JLine terminal with system I/O and JNI support."
  []
  (-> (TerminalBuilder/builder)
      (.system true)
      (.jni true)
      (.build)))

(defn enter-raw-mode
  "Put terminal in raw mode for character-by-character input.
   Returns the previous Attributes for restoration."
  [^Terminal terminal]
  (.enterRawMode terminal))

(defn get-size
  "Get terminal dimensions as {:width cols :height rows}."
  [^Terminal terminal]
  (let [size (.getSize terminal)]
    {:width (.getColumns size)
     :height (.getRows size)}))

(defn get-reader
  "Get the terminal's non-blocking reader."
  [^Terminal terminal]
  (.reader terminal))

(defn get-writer
  "Get the terminal's print writer."
  [^Terminal terminal]
  (.writer terminal))

(defn flush-output
  "Flush the terminal output."
  [^Terminal terminal]
  (.flush terminal))

(defn close
  "Close the terminal and release resources."
  [^Terminal terminal]
  (.close terminal))

(defn hide-cursor
  "Hide the terminal cursor."
  [^Terminal terminal]
  (.puts terminal InfoCmp$Capability/cursor_invisible)
  (flush-output terminal))

(defn show-cursor
  "Show the terminal cursor."
  [^Terminal terminal]
  (.puts terminal InfoCmp$Capability/cursor_visible)
  (flush-output terminal))

(defn clear-screen
  "Clear the terminal screen."
  [^Terminal terminal]
  (.puts terminal InfoCmp$Capability/clear_screen)
  (flush-output terminal))

(defn move-cursor
  "Move cursor to position (0-indexed)."
  [^Terminal terminal col row]
  (.puts terminal InfoCmp$Capability/cursor_address row col)
  (flush-output terminal))
