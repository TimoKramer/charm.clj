(ns charm.terminal-test
  (:require [clojure.test :refer [deftest is testing]]
            [charm.terminal :as term])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [org.jline.terminal Terminal Terminal$Signal TerminalBuilder]))

(deftest create-terminal-test
  (testing "create-terminal returns a JLine Terminal"
    (let [t (term/create-terminal)]
      (try
        (is (instance? Terminal t))
        (finally
          (term/close t))))))

(deftest get-size-test
  (testing "get-size returns map with width and height"
    (let [t (term/create-terminal)]
      (try
        (let [size (term/get-size t)]
          (is (map? size))
          (is (contains? size :width))
          (is (contains? size :height))
          ;; In non-interactive environments (CI, tests), JLine creates a "dumb terminal"
          ;; which may return 0 for dimensions. We just verify they're non-negative integers.
          (is (nat-int? (:width size)))
          (is (nat-int? (:height size))))
        (finally
          (term/close t))))))

(deftest enter-raw-mode-test
  (testing "enter-raw-mode returns previous attributes"
    (let [t (term/create-terminal)]
      (try
        ;; Note: enterRawMode returns Attributes object
        (let [attrs (term/enter-raw-mode t)]
          (is (some? attrs)))
        (finally
          (term/close t))))))

(deftest dark-color?-test
  (testing "dark colors are detected by luminance"
    (is (term/dark-color? 0x000000))
    (is (term/dark-color? 0x1e1e2e))
    (is (not (term/dark-color? 0xffffff)))
    (is (not (term/dark-color? 0xfafafa))))

  (testing "luminance weights green heaviest"
    (is (not (term/dark-color? 0x00ff00)))
    (is (term/dark-color? 0x0000ff))))

(deftest dark-background?-test
  (testing "returns a boolean, true when the background is unknown"
    ;; In non-interactive environments JLine creates a dumb terminal,
    ;; which cannot answer the OSC 11 background query.
    (let [t (term/create-terminal)]
      (try
        (is (boolean? (term/dark-background? t)))
        (finally
          (term/close t))))))

(deftest reader-writer-test
  (testing "get-reader returns non-nil"
    (let [t (term/create-terminal)]
      (try
        (is (some? (term/get-reader t)))
        (finally
          (term/close t)))))

  (testing "get-writer returns non-nil"
    (let [t (term/create-terminal)]
      (try
        (is (some? (term/get-writer t)))
        (finally
          (term/close t))))))

(defn- dumb-terminal
  ^Terminal []
  (-> (TerminalBuilder/builder)
      (.dumb true)
      (.streams (ByteArrayInputStream. (byte-array 0)) (ByteArrayOutputStream.))
      (.build)))

(deftest signal-handling-test
  (testing "a handler sees the signal raised on the terminal"
    ;; JLine's Windows terminal has no SIGWINCH and raises WINCH on the terminal
    ;; itself when the console reports a resize - only a handler installed on
    ;; the terminal sees that
    (let [t (dumb-terminal)
          fired (atom 0)]
      (try
        (term/handle-signal t :winch #(swap! fired inc))
        (.raise t Terminal$Signal/WINCH)
        (is (= 1 @fired))
        (finally
          (term/close t)))))

  (testing "handle-signal returns the handler it replaced, so it can be put back"
    (let [t (dumb-terminal)
          fired (atom 0)]
      (try
        (let [original (term/handle-signal t :winch #(swap! fired inc))
              ours (term/handle-signal t :winch #(swap! fired inc))]
          ;; The second call hands back the handler the first one installed
          (is (some? ours))
          (is (not= original ours))
          (term/restore-signal! t :winch original)
          ;; Restoring put the original back, so it comes round again
          (is (= original (term/handle-signal t :winch #(swap! fired inc)))))
        (finally
          (term/close t)))))

  (testing "restore-signal! tolerates a nil handler"
    (let [t (dumb-terminal)]
      (try
        (is (nil? (term/restore-signal! t :int nil)))
        (finally
          (term/close t)))))

  (testing "every signal can be handled and restored on the system terminal"
    (is (= #{:int :quit :tstp :cont :winch} (set (keys term/signals))))
    (let [t (term/create-terminal)]
      (try
        (doseq [signal (keys term/signals)]
          (let [previous (term/handle-signal t signal (fn []))]
            (is (nil? (term/restore-signal! t signal previous)))))
        (finally
          (term/close t))))))
