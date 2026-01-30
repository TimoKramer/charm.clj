(ns charm.terminal
  "JLine terminal wrapper for charm.clj"
  (:import [org.jline.terminal Terminal TerminalBuilder Attributes]
           [org.jline.utils InfoCmp$Capability]
           [java.nio.charset Charset]))

(defn create-terminal
  "Create a JLine terminal with system I/O and FFM as native interface."
  []
  (-> (TerminalBuilder/builder)
      (.system true)
      (.ffm true)
      (.build)))

(defn enter-raw-mode
  "Put terminal in raw mode for character-by-character input.
   Returns the previous Attributes for restoration."
  ^Attributes [^Terminal terminal]
  (.enterRawMode terminal))

(defn get-attributes
  "Get the terminal's current attributes.
   Use this to save terminal state before modifications."
  ^Attributes [^Terminal terminal]
  (.getAttributes terminal))

(defn set-attributes
  "Set the terminal's attributes.
   Use this to restore saved terminal state."
  [^Terminal terminal ^Attributes attrs]
  (.setAttributes terminal attrs))

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

(def ^"[Ljava.lang.Object;" empty-args (object-array 0))

(defn hide-cursor
  "Hide the terminal cursor."
  [^Terminal terminal]
  (.puts terminal InfoCmp$Capability/cursor_invisible empty-args)
  (flush-output terminal))

(defn show-cursor
  "Show the terminal cursor."
  [^Terminal terminal]
  (.puts terminal InfoCmp$Capability/cursor_visible empty-args)
  (flush-output terminal))

(defn clear-screen
  "Clear the terminal screen."
  [^Terminal terminal]
  (.puts terminal InfoCmp$Capability/clear_screen empty-args)
  (flush-output terminal))

(defn move-cursor
  "Move cursor to position (0-indexed)."
  [^Terminal terminal col row]
  (.puts terminal InfoCmp$Capability/cursor_address (object-array [row col]))
  (flush-output terminal))

(defn cursor-up
  "Move cursor up n lines."
  [^Terminal terminal n]
  (.puts terminal InfoCmp$Capability/parm_up_cursor (object-array [n]))
  (flush-output terminal))

(defn cursor-down
  "Move cursor down n lines."
  [^Terminal terminal n]
  (.puts terminal InfoCmp$Capability/parm_down_cursor (object-array [n]))
  (flush-output terminal))

(defn cursor-home
  "Move cursor to home position (0,0)."
  [^Terminal terminal]
  (.puts terminal InfoCmp$Capability/cursor_home empty-args)
  (flush-output terminal))

(defn clear-to-end-of-line
  "Clear from cursor to end of line."
  [^Terminal terminal]
  (.puts terminal InfoCmp$Capability/clr_eol empty-args)
  (flush-output terminal))

(defn clear-to-end-of-screen
  "Clear from cursor to end of screen."
  [^Terminal terminal]
  (.puts terminal InfoCmp$Capability/clr_eos empty-args)
  (flush-output terminal))

(defn enter-alt-screen
  "Enter alternate screen buffer."
  [^Terminal terminal]
  (.puts terminal InfoCmp$Capability/enter_ca_mode empty-args)
  (flush-output terminal))

(defn exit-alt-screen
  "Exit alternate screen buffer."
  [^Terminal terminal]
  (.puts terminal InfoCmp$Capability/exit_ca_mode empty-args)
  (flush-output terminal))
