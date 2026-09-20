(ns charm.render.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [charm.render.core :as r]
            [charm.terminal :as term]))

;; Note: Most renderer tests require a real terminal.
;; These tests focus on the logic that can be tested without terminal I/O.

(deftest create-renderer-test
  (testing "creates renderer with defaults"
    (let [terminal (term/create-terminal)
          renderer (r/create-renderer terminal)]
      (try
        (is (some? @renderer))
        (is (= 60 (:fps @renderer)))
        (is (false? (:alt-screen @renderer)))
        (is (false? (:in-alt-screen @renderer)))
        (is (true? (:hide-cursor @renderer)))
        (is (true? (:sanitize @renderer)))
        (is (nat-int? (:width @renderer)))
        (is (nat-int? (:height @renderer)))
        (finally
          (term/close terminal)))))

  (testing "creates renderer with options"
    (let [terminal (term/create-terminal)
          renderer (r/create-renderer terminal :fps 30 :alt-screen true)]
      (try
        (is (= 30 (:fps @renderer)))
        (is (true? (:alt-screen @renderer)))
        (is (false? (:in-alt-screen @renderer)))
        (finally
          (term/close terminal))))))

(deftest update-size-test
  (testing "updates renderer size"
    (let [terminal (term/create-terminal)
          renderer (r/create-renderer terminal)]
      (try
        (r/update-size! renderer 100 50)
        (is (= [100 50] (r/get-size renderer)))
        (finally
          (term/close terminal))))))

(deftest get-size-test
  (testing "returns current size"
    (let [terminal (term/create-terminal)
          renderer (r/create-renderer terminal)]
      (try
        (let [[w h] (r/get-size renderer)]
          (is (nat-int? w))
          (is (nat-int? h)))
        (finally
          (term/close terminal))))))

(deftest alt-screen-lifecycle-test
  (testing "start!/stop! use configured alt-screen and track active state separately"
    (let [calls (atom [])
          renderer (atom {:terminal :fake
                          :alt-screen true
                          :in-alt-screen false
                          :hide-cursor false
                          :running false})]
      (with-redefs [term/enter-alt-screen (fn [_] (swap! calls conj :enter))
                    term/clear-screen (fn [_] (swap! calls conj :clear))
                    term/cursor-home (fn [_] (swap! calls conj :home))
                    term/exit-alt-screen (fn [_] (swap! calls conj :exit))
                    r/disable-mouse! (fn [_] (swap! calls conj :disable-mouse))
                    r/disable-focus-reporting! (fn [_] (swap! calls conj :disable-focus))
                    r/show-cursor! (fn [_] (swap! calls conj :show-cursor))]
        ;; Stop before start should not send exit alt-screen.
        (r/stop! renderer)
        (is (= [:disable-mouse :disable-focus] @calls))
        (reset! calls [])

        (r/start! renderer)
        (is (= [:enter :clear :home] @calls))
        (is (true? (:in-alt-screen @renderer)))

        ;; Enter should be idempotent once active.
        (r/start! renderer)
        (is (= [:enter :clear :home] @calls))

        (r/stop! renderer)
        (is (= [:enter :clear :home :exit :disable-mouse :disable-focus] @calls))
        (is (false? (:in-alt-screen @renderer)))))))

(defn- render-to-bytes
  "Render `content` into a dumb terminal and return what was written.

   Pass `:size [width height]` to fix the renderer's dimensions."
  [content & opts]
  (let [input (java.io.ByteArrayInputStream. (byte-array 0))
        output (java.io.ByteArrayOutputStream.)
        terminal (-> (org.jline.terminal.TerminalBuilder/builder)
                     (.dumb true)
                     (.streams input output)
                     (.build))
        opts (apply hash-map opts)
        [w h] (:size opts)
        renderer (apply r/create-renderer terminal (apply concat (dissoc opts :size)))]
    (when w (swap! renderer assoc :width w :height h))
    (try
      (r/render! renderer content)
      (.flush (.writer terminal))
      (.toString output "UTF-8")
      (finally
        (term/close terminal)))))

(deftest render-sanitizes-content-test
  ;; JLine's own fromAnsi drops OSC, DCS and standard CSI as of 4.4.5, so the
  ;; sequences that still reach a terminal unaided are the ones it keeps: ESC c
  ;; (RIS, a full terminal reset), carriage return, and private CSI, which it
  ;; turns into visible text rather than removing.
  (testing "ESC c cannot reset the terminal"
    (let [written (render-to-bytes "a\u001bcb")]
      (is (re-find #"ab" written))
      (is (not (re-find #"\u001bc" written)))))

  (testing "a carriage return cannot overwrite what was just drawn"
    (let [written (render-to-bytes "visible\rhidden")]
      (is (not (re-find #"\r" written)))))

  (testing "private CSI is removed, not turned into visible text"
    (let [written (render-to-bytes "a\u001b[?1049hb")]
      (is (not (re-find #"1049h" written)))))

  (testing ":sanitize false lets the application write its own sequences"
    (let [written (render-to-bytes "a\u001bcb" :sanitize false)]
      (is (re-find #"\u001bc" written))))

  (testing "styling still reaches the terminal"
    (let [written (render-to-bytes "\u001b[31mred\u001b[0m")]
      (is (re-find #"\u001b\[" written))
      (is (re-find #"red" written)))))

(deftest render-truncation-test
  (testing "a line wider than the terminal is cut to the width"
    (let [written (render-to-bytes "0123456789" :size [4 1])]
      (is (re-find #"0123" written))
      (is (not (re-find #"456789" written)))))

  (testing "a cut line keeps its styling"
    ;; The renderer used to truncate through a plain string, which dropped the
    ;; styling of every line too wide for the terminal
    (let [written (render-to-bytes "\u001b[31m0123456789\u001b[0m" :size [4 1])]
      (is (re-find #"\u001b\[31m" written))
      (is (re-find #"0123" written))
      (is (not (re-find #"456789" written)))))

  (testing "a line that fits is untouched"
    (let [written (render-to-bytes "\u001b[31mred\u001b[0m" :size [40 1])]
      (is (re-find #"\u001b\[31m" written))
      (is (re-find #"red" written))))

  (testing "more lines than the height keeps the last ones"
    (let [written (render-to-bytes "one\ntwo\nthree" :size [40 2])]
      (is (not (re-find #"one" written)))
      (is (re-find #"three" written)))))
