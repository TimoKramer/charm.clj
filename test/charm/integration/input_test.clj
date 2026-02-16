(ns charm.integration.input-test
  "Integration tests for terminal input reading.

   Uses JLine's dumb terminal with ByteArrayInputStream to simulate
   user input and verify the full input → parse → event pipeline."
  (:require
   [clojure.test :refer [deftest is testing]]
   [charm.input.handler :as input]
   [charm.input.mouse :as mouse])
  (:import
   [java.io ByteArrayInputStream ByteArrayOutputStream]
   [org.jline.terminal TerminalBuilder]))

;; ---------------------------------------------------------------------------
;; Test Helpers
;; ---------------------------------------------------------------------------

(defn make-test-terminal
  "Creates a dumb terminal with the given input bytes.
   Returns {:terminal terminal :output output-stream}."
  [^String input-str]
  (let [input (ByteArrayInputStream. (.getBytes input-str "UTF-8"))
        output (ByteArrayOutputStream.)]
    {:terminal (-> (TerminalBuilder/builder)
                   (.streams input output)
                   (.system false)
                   (.type "dumb")
                   (.build))
     :output output}))

(defmacro with-test-terminal
  "Execute body with a test terminal bound to `terminal`.
   Ensures terminal is closed after execution."
  [[terminal-sym input-str] & body]
  `(let [{terminal# :terminal} (make-test-terminal ~input-str)
         ~terminal-sym terminal#]
     (try
       ~@body
       (finally
         (.close terminal#)))))

;; ---------------------------------------------------------------------------
;; Basic Character Reading
;; ---------------------------------------------------------------------------

(deftest read-printable-characters-test
  (testing "single printable character"
    (with-test-terminal [terminal "a"]
      (is (= {:type :runes :runes "a"}
             (input/read-event terminal)))))

  (testing "uppercase character"
    (with-test-terminal [terminal "Z"]
      (is (= {:type :runes :runes "Z"}
             (input/read-event terminal)))))

  (testing "digit"
    (with-test-terminal [terminal "5"]
      (is (= {:type :runes :runes "5"}
             (input/read-event terminal)))))

  (testing "special character"
    (with-test-terminal [terminal "!"]
      (is (= {:type :runes :runes "!"}
             (input/read-event terminal))))))

(deftest read-control-characters-test
  (testing "enter key (newline)"
    (with-test-terminal [terminal "\n"]
      (is (= {:type :enter}
             (input/read-event terminal)))))

  (testing "tab"
    (with-test-terminal [terminal "\t"]
      (is (= {:type :tab}
             (input/read-event terminal)))))

  (testing "backspace (DEL)"
    (with-test-terminal [terminal "\u007f"]
      (is (= {:type :backspace}
             (input/read-event terminal)))))

  (testing "ctrl+c is intercepted by jline"
    (with-test-terminal [terminal "\u0003"]
      (is (= nil
             (input/read-event terminal)))))

  (testing "ctrl+d is intercepted by jline"
    (with-test-terminal [terminal "\u0004"]
      (is (= {:type :runes :runes "d" :ctrl true}
             (input/read-event terminal)))))

  (testing "ctrl+z is intercepted by jline"
    (with-test-terminal [terminal "\u001a"]
      (is (= nil
             (input/read-event terminal))))))

;; ---------------------------------------------------------------------------
;; Escape Sequences - Arrow Keys
;; ---------------------------------------------------------------------------

(deftest read-arrow-keys-test
  (testing "up arrow"
    (with-test-terminal [terminal "\u001b[A"]
      (is (= {:type :up}
             (input/read-event terminal)))))

  (testing "down arrow"
    (with-test-terminal [terminal "\u001b[B"]
      (is (= {:type :down}
             (input/read-event terminal)))))

  (testing "right arrow"
    (with-test-terminal [terminal "\u001b[C"]
      (is (= {:type :right}
             (input/read-event terminal)))))

  (testing "left arrow"
    (with-test-terminal [terminal "\u001b[D"]
      (is (= {:type :left}
             (input/read-event terminal))))))

(deftest read-arrow-keys-with-modifiers-test
  (testing "shift+up"
    (with-test-terminal [terminal "\u001b[1;2A"]
      (is (= {:type :up :shift true}
             (input/read-event terminal)))))

  (testing "ctrl+down"
    (with-test-terminal [terminal "\u001b[1;5B"]
      (is (= {:type :down :ctrl true}
             (input/read-event terminal)))))

  (testing "alt+right"
    (with-test-terminal [terminal "\u001b[1;3C"]
      (is (= {:type :right :alt true}
             (input/read-event terminal)))))

  (testing "ctrl+shift+left"
    (with-test-terminal [terminal "\u001b[1;6D"]
      (is (= {:type :left :ctrl true :shift true}
             (input/read-event terminal))))))

;; ---------------------------------------------------------------------------
;; Escape Sequences - Navigation Keys
;; ---------------------------------------------------------------------------

(deftest read-navigation-keys-test
  (testing "home"
    (with-test-terminal [terminal "\u001b[H"]
      (is (= {:type :home}
             (input/read-event terminal)))))

  (testing "end"
    (with-test-terminal [terminal "\u001b[F"]
      (is (= {:type :end}
             (input/read-event terminal)))))

  (testing "page up"
    (with-test-terminal [terminal "\u001b[5~"]
      (is (= {:type :page-up}
             (input/read-event terminal)))))

  (testing "page down"
    (with-test-terminal [terminal "\u001b[6~"]
      (is (= {:type :page-down}
             (input/read-event terminal)))))

  (testing "insert"
    (with-test-terminal [terminal "\u001b[2~"]
      (is (= {:type :insert}
             (input/read-event terminal)))))

  (testing "delete"
    (with-test-terminal [terminal "\u001b[3~"]
      (is (= {:type :delete}
             (input/read-event terminal))))))

;; ---------------------------------------------------------------------------
;; Escape Sequences - Function Keys
;; ---------------------------------------------------------------------------

(deftest read-function-keys-test
  (testing "F1 (SS3)"
    (with-test-terminal [terminal "\u001bOP"]
      (is (= {:type :f1}
             (input/read-event terminal)))))

  (testing "F2 (SS3)"
    (with-test-terminal [terminal "\u001bOQ"]
      (is (= {:type :f2}
             (input/read-event terminal)))))

  (testing "F3 (SS3)"
    (with-test-terminal [terminal "\u001bOR"]
      (is (= {:type :f3}
             (input/read-event terminal)))))

  (testing "F4 (SS3)"
    (with-test-terminal [terminal "\u001bOS"]
      (is (= {:type :f4}
             (input/read-event terminal)))))

  (testing "F5"
    (with-test-terminal [terminal "\u001b[15~"]
      (is (= {:type :f5}
             (input/read-event terminal)))))

  (testing "F6"
    (with-test-terminal [terminal "\u001b[17~"]
      (is (= {:type :f6}
             (input/read-event terminal)))))

  (testing "F7"
    (with-test-terminal [terminal "\u001b[18~"]
      (is (= {:type :f7}
             (input/read-event terminal)))))

  (testing "F8"
    (with-test-terminal [terminal "\u001b[19~"]
      (is (= {:type :f8}
             (input/read-event terminal)))))

  (testing "F9"
    (with-test-terminal [terminal "\u001b[20~"]
      (is (= {:type :f9}
             (input/read-event terminal)))))

  (testing "F10"
    (with-test-terminal [terminal "\u001b[21~"]
      (is (= {:type :f10}
             (input/read-event terminal)))))

  (testing "F11"
    (with-test-terminal [terminal "\u001b[23~"]
      (is (= {:type :f11}
             (input/read-event terminal)))))

  (testing "F12"
    (with-test-terminal [terminal "\u001b[24~"]
      (is (= {:type :f12}
             (input/read-event terminal))))))

;; ---------------------------------------------------------------------------
;; Alt+Key Combinations
;; ---------------------------------------------------------------------------

(deftest read-alt-key-test
  (testing "alt+a"
    (with-test-terminal [terminal "\u001ba"]
      (is (= {:type :runes :runes "a" :alt true}
             (input/read-event terminal)))))

  (testing "alt+x"
    (with-test-terminal [terminal "\u001bx"]
      (is (= {:type :runes :runes "x" :alt true}
             (input/read-event terminal)))))

  (testing "alt+1"
    (with-test-terminal [terminal "\u001b1"]
      (is (= {:type :runes :runes "1" :alt true}
             (input/read-event terminal))))))

;; ---------------------------------------------------------------------------
;; Escape Key
;; ---------------------------------------------------------------------------

(deftest read-escape-key-test
  (testing "bare escape (with timeout)"
    (with-test-terminal [terminal "\u001b"]
      ;; Escape alone should return :escape after timeout
      (is (= {:type :escape}
             (input/read-event terminal :timeout-ms 10))))))

;; ---------------------------------------------------------------------------
;; Mouse Events (SGR encoding)
;; ---------------------------------------------------------------------------

(deftest read-sgr-mouse-test
  (testing "mouse click at position"
    (with-test-terminal [terminal "\u001b[<0;10;5M"]
      (let [event (input/read-event terminal)]
        (is (mouse/mouse-event? event))
        (is (= 10 (:x event)))
        (is (= 5 (:y event)))
        (is (= :press (:action event)))
        (is (mouse/left-click? event)))))

  (testing "mouse release"
    (with-test-terminal [terminal "\u001b[<0;10;5m"]
      (let [event (input/read-event terminal)]
        (is (mouse/mouse-event? event))
        (is (= :release (:action event))))))

  (testing "right mouse button"
    (with-test-terminal [terminal "\u001b[<2;15;20M"]
      (let [event (input/read-event terminal)]
        (is (mouse/mouse-event? event))
        (is (mouse/right-click? event))
        (is (= 15 (:x event)))
        (is (= 20 (:y event))))))

  (testing "middle mouse button"
    (with-test-terminal [terminal "\u001b[<1;5;10M"]
      (let [event (input/read-event terminal)]
        (is (mouse/mouse-event? event))
        (is (mouse/middle-click? event)))))

  (testing "mouse scroll up"
    (with-test-terminal [terminal "\u001b[<64;10;10M"]
      (let [event (input/read-event terminal)]
        (is (mouse/mouse-event? event))
        (is (mouse/wheel? event))
        (is (mouse/wheel-up? event)))))

  (testing "mouse scroll down"
    (with-test-terminal [terminal "\u001b[<65;10;10M"]
      (let [event (input/read-event terminal)]
        (is (mouse/mouse-event? event))
        (is (mouse/wheel? event))
        (is (mouse/wheel-down? event))))))

;; ---------------------------------------------------------------------------
;; Multiple Sequential Events
;; ---------------------------------------------------------------------------

(deftest read-multiple-events-test
  (testing "reading multiple characters in sequence"
    (with-test-terminal [terminal "abc"]
      (is (= {:type :runes :runes "a"} (input/read-event terminal)))
      (is (= {:type :runes :runes "b"} (input/read-event terminal)))
      (is (= {:type :runes :runes "c"} (input/read-event terminal)))))

  (testing "reading mixed input types"
    (with-test-terminal [terminal "a\u001b[Ab"]
      (is (= {:type :runes :runes "a"} (input/read-event terminal)))
      (is (= {:type :up} (input/read-event terminal)))
      (is (= {:type :runes :runes "b"} (input/read-event terminal))))))

;; ---------------------------------------------------------------------------
;; Timeout Behavior
;; ---------------------------------------------------------------------------

(deftest read-timeout-test
  (testing "returns nil on empty input with timeout"
    (with-test-terminal [terminal ""]
      (is (nil? (input/read-event terminal :timeout-ms 10))))))
