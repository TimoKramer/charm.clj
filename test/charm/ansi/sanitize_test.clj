(ns charm.ansi.sanitize-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [charm.ansi.sanitize :as san]))

(def ^:private ESC "\u001b")
(def ^:private BEL "\u0007")

(deftest sanitize-keeps-text-and-styling-test
  (testing "plain text is returned as the same object"
    (let [s "hello world"]
      (is (identical? s (san/sanitize s)))))

  (testing "SGR styling survives, and costs no copy"
    (let [s (str ESC "[1;31mred" ESC "[0m")]
      (is (identical? s (san/sanitize s)))))

  (testing "newline and tab survive"
    (is (= "a\tb\nc" (san/sanitize "a\tb\nc"))))

  (testing "nil is empty"
    (is (= "" (san/sanitize nil)))
    (is (= "" (san/strip nil)))))

(deftest sanitize-drops-injected-sequences-test
  (testing "OSC 52 cannot reach the clipboard"
    (is (= "file.txt" (san/sanitize (str "file" ESC "]52;c;ZXZpbA==" BEL ".txt")))))

  (testing "OSC 2 cannot rewrite the window title"
    (is (= "ab" (san/sanitize (str "a" ESC "]2;PWNED" BEL "b")))))

  (testing "OSC 8 cannot hyperlink the text"
    (is (= "click" (san/sanitize (str ESC "]8;;http://evil" BEL "click" ESC "]8;;" BEL)))))

  (testing "DCS, APC, PM and SOS are dropped, ST-terminated"
    (doseq [intro ["P" "_" "^" "X"]]
      (is (= "ab" (san/sanitize (str "a" ESC intro "payload" ESC "\\b"))))))

  (testing "an unterminated string sequence swallows its tail, as a terminal does"
    (is (= "a" (san/sanitize (str "a" ESC "]52;c;never-closed")))))

  (testing "non-SGR CSI cannot erase or move"
    (is (= "visiblehidden" (san/sanitize (str "visible" ESC "[2K\rhidden"))))
    (is (= "ab" (san/sanitize (str "a" ESC "[10;10Hb")))))

  (testing "a lone ESC and two-character escapes are dropped"
    (is (= "a" (san/sanitize (str "a" ESC))))
    (is (= "ab" (san/sanitize (str "a" ESC "cb")))))

  (testing "C0 other than newline and tab is dropped"
    (is (= "ab" (san/sanitize "a\rb")))
    (is (= "ab" (san/sanitize (str "a" BEL "b"))))
    (is (= "ab" (san/sanitize "a\u0008b"))))

  (testing "C1 controls are dropped - 0x9b and 0x9d are CSI and OSC"
    (is (= "abc" (san/sanitize "a\u009db\u009bc")))))

(deftest strip-test
  (testing "strip removes styling too"
    (is (= "red" (san/strip (str ESC "[31mred" ESC "[0m"))))
    (is (= "file.txt" (san/strip (str "file" ESC "]52;c;ZXZpbA==" BEL ".txt")))))

  (testing "plain text is returned as the same object"
    (let [s "hello"]
      (is (identical? s (san/strip s))))))

(deftest strip-controls-test
  (testing "removes every control character, newline and tab included"
    (is (= "ab" (san/strip-controls "a\nb")))
    (is (= "ab" (san/strip-controls "a\tb")))
    (is (= "" (san/strip-controls (str ESC BEL)))))

  (testing "a title cannot open a sequence of its own"
    (let [title (san/strip-controls (str "hi" ESC "]52;c;ZXZpbA=="))]
      (is (not (re-find #"\u001b" title)))
      (is (not (re-find #"\u0007" title)))))

  (testing "plain text is returned as the same object"
    (let [s "My Window"]
      (is (identical? s (san/strip-controls s))))))
