(ns charm.input.keymap
  "JLine KeyMap-based escape sequence handling.

   Uses JLine's KeyMap for efficient O(1) escape sequence lookup
   with terminal capability awareness."
  (:import
   [org.jline.keymap KeyMap]
   [org.jline.terminal Terminal]
   [org.jline.utils InfoCmp$Capability]))

;; ---------------------------------------------------------------------------
;; Key Event Definitions
;; ---------------------------------------------------------------------------

(def ^:private navigation-keys
  "Navigation key definitions with their capabilities and fallback sequences."
  [{:event {:type :up}
    :cap InfoCmp$Capability/key_up
    :seqs ["[A" "OA"]}
   {:event {:type :down}
    :cap InfoCmp$Capability/key_down
    :seqs ["[B" "OB"]}
   {:event {:type :right}
    :cap InfoCmp$Capability/key_right
    :seqs ["[C" "OC"]}
   {:event {:type :left}
    :cap InfoCmp$Capability/key_left
    :seqs ["[D" "OD"]}
   {:event {:type :home}
    :cap InfoCmp$Capability/key_home
    :seqs ["[H" "OH" "[1~" "[7~"]}
   {:event {:type :end}
    :cap InfoCmp$Capability/key_end
    :seqs ["[F" "OF" "[4~" "[8~"]}
   {:event {:type :insert}
    :cap InfoCmp$Capability/key_ic
    :seqs ["[2~"]}
   {:event {:type :delete}
    :cap InfoCmp$Capability/key_dc
    :seqs ["[3~"]}
   {:event {:type :page-up}
    :cap InfoCmp$Capability/key_ppage
    :seqs ["[5~"]}
   {:event {:type :page-down}
    :cap InfoCmp$Capability/key_npage
    :seqs ["[6~"]}])

(def ^:private function-keys
  "Function key definitions."
  [{:event {:type :f1}  :cap InfoCmp$Capability/key_f1  :seqs ["OP" "[11~"]}
   {:event {:type :f2}  :cap InfoCmp$Capability/key_f2  :seqs ["OQ" "[12~"]}
   {:event {:type :f3}  :cap InfoCmp$Capability/key_f3  :seqs ["OR" "[13~"]}
   {:event {:type :f4}  :cap InfoCmp$Capability/key_f4  :seqs ["OS" "[14~"]}
   {:event {:type :f5}  :cap InfoCmp$Capability/key_f5  :seqs ["[15~"]}
   {:event {:type :f6}  :cap InfoCmp$Capability/key_f6  :seqs ["[17~"]}
   {:event {:type :f7}  :cap InfoCmp$Capability/key_f7  :seqs ["[18~"]}
   {:event {:type :f8}  :cap InfoCmp$Capability/key_f8  :seqs ["[19~"]}
   {:event {:type :f9}  :cap InfoCmp$Capability/key_f9  :seqs ["[20~"]}
   {:event {:type :f10} :cap InfoCmp$Capability/key_f10 :seqs ["[21~"]}
   {:event {:type :f11} :cap InfoCmp$Capability/key_f11 :seqs ["[23~"]}
   {:event {:type :f12} :cap InfoCmp$Capability/key_f12 :seqs ["[24~"]}])

(def ^:private extended-function-keys
  "Extended function keys (F13-F20)."
  [{:event {:type :f13} :seqs ["[25~"]}
   {:event {:type :f14} :seqs ["[26~"]}
   {:event {:type :f15} :seqs ["[28~"]}
   {:event {:type :f16} :seqs ["[29~"]}
   {:event {:type :f17} :seqs ["[31~"]}
   {:event {:type :f18} :seqs ["[32~"]}
   {:event {:type :f19} :seqs ["[33~"]}
   {:event {:type :f20} :seqs ["[34~"]}])

(def ^:private special-keys
  "Special key sequences (focus, paste)."
  [{:event {:type :focus}       :seqs ["[I"]}
   {:event {:type :blur}        :seqs ["[O"]}
   {:event {:type :paste-start} :seqs ["[200~"]}
   {:event {:type :paste-end}   :seqs ["[201~"]}])

;; ---------------------------------------------------------------------------
;; Modifier Sequence Generation
;; ---------------------------------------------------------------------------

(defn- modifier-code->modifiers
  "Convert xterm modifier code to modifier map.
   Code: 1=none, 2=shift, 3=alt, 4=shift+alt, 5=ctrl, 6=shift+ctrl, 7=alt+ctrl, 8=all"
  [code]
  (let [c (dec code)]
    (cond-> {}
      (pos? (bit-and c 1)) (assoc :shift true)
      (pos? (bit-and c 2)) (assoc :alt true)
      (pos? (bit-and c 4)) (assoc :ctrl true))))

(defn- generate-modified-sequences
  "Generate all modifier combinations for a base key.
   Returns pairs of [sequence event-with-modifiers]."
  [base-event base-seqs]
  (let [key-type (:type base-event)]
    (for [mod-code (range 2 9)  ; 2-8 = modifier codes
          base-seq base-seqs
          :let [modifiers (modifier-code->modifiers mod-code)
                event (merge base-event modifiers)
                ;; Generate modified sequence based on base sequence format
                seq (cond
                      ;; CSI 1;mod X format for arrow keys etc (e.g., "[A" -> "[1;2A")
                      (re-matches #"\[([A-Z])" base-seq)
                      (let [[_ final] (re-matches #"\[([A-Z])" base-seq)]
                        (str "[1;" mod-code final))

                      ;; SS3 format (e.g., "OP" -> "[1;2P")
                      (re-matches #"O([A-Z])" base-seq)
                      (let [[_ final] (re-matches #"O([A-Z])" base-seq)]
                        (str "[1;" mod-code final))

                      ;; CSI n~ format (e.g., "[15~" -> "[15;2~")
                      (re-matches #"\[(\d+)~" base-seq)
                      (let [[_ num] (re-matches #"\[(\d+)~" base-seq)]
                        (str "[" num ";" mod-code "~"))

                      :else nil)]
          :when seq]
      [seq event])))

;; ---------------------------------------------------------------------------
;; KeyMap Creation
;; ---------------------------------------------------------------------------

(defn- bind-key!
  "Bind a sequence to an event in the keymap.
   Sequence should be WITHOUT the ESC prefix (e.g., \"[A\" not \"\\e[A\")."
  [^KeyMap keymap seq event]
  (.bind keymap event seq))

(defn- bind-from-capability!
  "Bind a key from terminal capability, stripping ESC prefix.
   Returns nil if terminal is nil."
  [^KeyMap keymap ^Terminal terminal ^InfoCmp$Capability cap event]
  (when terminal
    (when-let [seq (KeyMap/key terminal cap)]
      ;; JLine's key() returns the full sequence including ESC
      ;; We need to strip the ESC since we read sequences after ESC
      (let [seq-str (String. ^chars seq)]
        (when (and (pos? (count seq-str))
                   (= (int (first seq-str)) 27))
          (bind-key! keymap (subs seq-str 1) event))))))

(defn- bind-fallback-sequences!
  "Bind fallback sequences for a key."
  [^KeyMap keymap seqs event]
  (doseq [seq seqs]
    (bind-key! keymap seq event)))

(defn- bind-key-definition!
  "Bind a key definition (capability + fallbacks) to the keymap."
  [^KeyMap keymap ^Terminal terminal {:keys [event cap seqs]}]
  ;; First try terminal capability for terminal-aware sequence
  (when cap
    (bind-from-capability! keymap terminal cap event))
  ;; Then bind fallback sequences
  (when seqs
    (bind-fallback-sequences! keymap seqs event)))

(defn create-keymap
  "Create a KeyMap for escape sequence lookup.

   When terminal is provided, uses terminal capabilities for sequences,
   falling back to standard sequences for terminals without capabilities."
  ([]
   (create-keymap nil))
  ([^Terminal terminal]
   (let [keymap (KeyMap.)]
     ;; Set ambiguous timeout for sequences
     (.setAmbiguousTimeout keymap 100)

     ;; Bind navigation keys
     (doseq [key-def navigation-keys]
       (bind-key-definition! keymap terminal key-def))

     ;; Bind function keys
     (doseq [key-def function-keys]
       (bind-key-definition! keymap terminal key-def))

     ;; Bind extended function keys (no capabilities)
     (doseq [key-def extended-function-keys]
       (bind-fallback-sequences! keymap (:seqs key-def) (:event key-def)))

     ;; Bind special keys
     (doseq [key-def special-keys]
       (bind-fallback-sequences! keymap (:seqs key-def) (:event key-def)))

     ;; Generate and bind modified key sequences
     (doseq [key-def (concat navigation-keys function-keys)]
       (doseq [[seq event] (generate-modified-sequences (:event key-def) (:seqs key-def))]
         (bind-key! keymap seq event)))

     keymap)))

;; ---------------------------------------------------------------------------
;; Sequence Lookup
;; ---------------------------------------------------------------------------

(defn lookup
  "Look up an escape sequence in the keymap.
   Returns the key event map or nil if not found.

   sequence should be WITHOUT the ESC prefix (e.g., \"[A\" not \"\\e[A\")."
  [^KeyMap keymap ^String sequence]
  (when-let [bound (.getBound keymap sequence)]
    (when (map? bound)
      bound)))

(defn lookup-or-unknown
  "Look up an escape sequence, returning unknown event if not found."
  [^KeyMap keymap ^String sequence]
  (or (lookup keymap sequence)
      {:type :unknown :sequence sequence}))

;; ---------------------------------------------------------------------------
;; Default KeyMap (no terminal, uses standard sequences)
;; ---------------------------------------------------------------------------

(def default-keymap
  "Default keymap using standard escape sequences."
  (delay (create-keymap)))
