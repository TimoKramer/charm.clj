(ns charm.test.screen
  "Render a frame into an in-memory terminal emulator and read back the screen.

   The other integration tests assert on the escape bytes the renderer wrote,
   which is the right level for a question like \"did this OSC reach the
   terminal\" but the wrong one for \"what does the user see\": those assertions
   break whenever JLine's `Display` changes how it moves the cursor, even though
   the screen is identical.

   `org.jline.utils.ScreenTerminal` is a VT emulator. Feeding it the renderer's
   output and reading the resulting cells tests the screen instead of the wire.

   This namespace lives under `test-jvm` rather than `test` because
   `ScreenTerminal` is not in babashka's image, and a namespace that merely names
   it fails to load there - at analysis time, so no runtime guard would help."
  (:require
   [charm.render.core :as render]
   [charm.terminal :as term]
   [clojure.string :as str])
  (:import
   [java.io ByteArrayInputStream ByteArrayOutputStream]
   [java.nio.charset StandardCharsets]
   [org.jline.terminal Size TerminalBuilder]
   [org.jline.utils ScreenTerminal]))

(defn- build-terminal
  "A terminal that writes to `out` and reports the size we asked for.

   The size has to be given to the builder as well as to the renderer: `Display`
   takes its idea of the width from the terminal, and a `Display` that thinks the
   screen is wider pads with a cursor-forward, which lands the next line in the
   wrong column."
  [^ByteArrayOutputStream out cols rows]
  (-> (TerminalBuilder/builder)
      (.streams (ByteArrayInputStream. (byte-array 0)) out)
      (.system false)
      (.type "xterm-256color")
      (.encoding StandardCharsets/UTF_8)
      (.size (Size. cols rows))
      (.build)))

(defn render-screen
  "Render `content` onto a `cols` x `rows` screen and return what it looks like.

     {:grid   [\"line 0\" \"line 1\" ...]   ; one string per row, space-padded
      :cursor [x y]
      :cols n :rows n
      :cells <long[]>}                    ; for `cell`

   Options after the size are passed to `create-renderer`, so `:sanitize`,
   `:overflow` and the rest can be exercised."
  [content [cols rows] & renderer-opts]
  (let [out (ByteArrayOutputStream.)
        terminal (build-terminal out cols rows)
        renderer (apply render/create-renderer terminal renderer-opts)]
    (try
      (render/update-size! renderer cols rows)
      (render/render! renderer content)
      (.flush (.writer terminal))
      (finally
        (term/close terminal)))
    (let [screen (ScreenTerminal. cols rows)
          cells (long-array (* cols rows))
          cursor (int-array 2)]
      (.write screen (.toString out "UTF-8"))
      (.dump screen cells cursor)
      {:grid (vec (for [y (range rows)]
                    (apply str (for [x (range cols)]
                                 (let [cp (ScreenTerminal/cellCodePoint
                                           (aget cells (+ (* y cols) x)))]
                                   (if (zero? cp) \space (char cp)))))))
       :cursor (vec cursor)
       :cols cols
       :rows rows
       :cells cells})))

(defn cell
  "What is on screen at `row`, `col` - the character and its attributes."
  [{:keys [^longs cells cols]} row col]
  (let [c (aget cells (+ (* row cols) col))
        cp (ScreenTerminal/cellCodePoint c)]
    {:char (if (zero? cp) \space (char cp))
     :bold? (ScreenTerminal/cellBold c)
     :italic? (ScreenTerminal/cellItalic c)
     :underline? (ScreenTerminal/cellUnderline c)
     :inverse? (ScreenTerminal/cellInverse c)
     :fg (when (ScreenTerminal/cellHasForeground c) (ScreenTerminal/cellForeground c))
     :bg (when (ScreenTerminal/cellHasBackground c) (ScreenTerminal/cellBackground c))}))

(defn row-text
  "One row of the screen, with trailing blanks removed."
  [screen row]
  (str/trimr (get (:grid screen) row)))

(defn screen-text
  "The whole screen as one string, trailing blanks and blank rows removed."
  [screen]
  (->> (:grid screen)
       (map str/trimr)
       (reverse)
       (drop-while str/blank?)
       (reverse)
       (str/join "\n")))
