(ns charm.terminal-test
  (:require [clojure.test :refer [deftest is testing]]
            [charm.terminal :as term])
  (:import [org.jline.terminal Terminal]))

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
