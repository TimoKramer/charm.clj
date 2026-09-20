(ns charm.input.keyparser-test
  "Cross-check charm's escape-sequence table against JLine's own parser.

   `org.jline.terminal.KeyParser` appeared after charm's keymap was written and
   parses many of the same sequences. It cannot replace the keymap - see the
   addendum to ADR 004 - but where both understand a sequence they should agree,
   which makes JLine an independent check on a table charm generates itself,
   including the 224 xterm modifier combinations.

   Lives under test-jvm because KeyParser is not in babashka's image."
  (:require
   [charm.input.keymap :as km]
   [clojure.test :refer [deftest is testing]])
  (:import
   [org.jline.terminal KeyEvent KeyParser]))

(def ^:private specials
  {"Enter" :enter "Tab" :tab "Escape" :escape "Backspace" :backspace
   "Delete" :delete "Home" :home "End" :end "PageUp" :page-up
   "PageDown" :page-down "Insert" :insert})

(def ^:private arrows
  {"Up" :up "Down" :down "Left" :left "Right" :right})

(defn- jline-event
  "Parse an escape sequence with JLine and describe it the way charm would.

   Returns nil when JLine does not recognise the sequence."
  [seq-without-esc]
  (let [^KeyEvent e (KeyParser/parse (str \u001b seq-without-esc))
        mods (set (map str (.getModifiers e)))
        base (case (str (.getType e))
               "Arrow" (get arrows (str (.getArrow e)))
               "Special" (get specials (str (.getSpecial e)))
               "Function" (keyword (str "f" (.getFunctionKey e)))
               nil)]
    (when base
      (cond-> {:type base}
        (mods "Shift") (assoc :shift true)
        (mods "Alt") (assoc :alt true)
        (mods "Control") (assoc :ctrl true)))))

(def ^:private charm-corpus
  "Every escape sequence charm binds, with the event it produces.

   Read from the keymap's own tables, so it cannot drift out of step with them."
  (let [navigation @#'km/navigation-keys
        function @#'km/function-keys
        generate @#'km/generate-modified-sequences]
    (distinct
     (concat
      (for [t (concat navigation function
                      @#'km/extended-function-keys
                      @#'km/special-keys)
            s (:seqs t)]
        [s (:event t)])
      (for [t (concat navigation function)
            [s event] (generate (:event t) (:seqs t))]
        [s event])))))

(deftest charm-agrees-with-jline-test
  (let [checked (for [[s charm-event] charm-corpus
                      :let [jline (jline-event s)]
                      :when jline]
                  [s charm-event jline])]
    (testing "enough of the corpus is cross-checked for this to mean something"
      ;; Guards against the test quietly going vacuous if the tables move
      (is (<= 200 (count checked))
          (str "only " (count checked) " of " (count charm-corpus) " were checked")))

    (testing "charm and JLine agree on every sequence both understand"
      (doseq [[s charm-event jline] checked]
        (is (= charm-event jline)
            (str "disagree on " (pr-str s)))))))

(deftest jline-cannot-replace-the-keymap-test
  ;; The gaps recorded in ADR 004's addendum. If JLine closes one of these, this
  ;; test fails and the addendum should be revisited - that is the point of it.
  (testing "JLine does not parse the sequences charm needs beyond it"
    (doseq [[s what] {"OA" "SS3 up, sent in application keypad mode"
                      "OH" "SS3 home"
                      "[1~" "home, the VT-style variant"
                      "[4~" "end, the VT-style variant"
                      "[25~" "F13"
                      "[I" "focus in"
                      "[200~" "bracketed paste start"}]
      (is (nil? (jline-event s))
          (str "JLine now parses " (pr-str s) " (" what ") - revisit ADR 004")))))
