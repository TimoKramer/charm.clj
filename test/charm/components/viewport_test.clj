(ns charm.components.viewport-test
  (:require
   [charm.components.viewport :as vp]
   [charm.message :as msg]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]))

(deftest viewport-creation-test
  (testing "create with content"
    (let [v (vp/viewport "line1\nline2\nline3")]
      (is (= :viewport (:type v)))
      (is (= "line1\nline2\nline3" (vp/viewport-content v)))
      (is (= 3 (vp/viewport-line-count v)))
      (is (= 0 (:y-offset v)))))

  (testing "create with options"
    (let [v (vp/viewport "a\nb\nc" :height 2 :width 10)]
      (is (= 2 (:height v)))
      (is (= 10 (:width v)))))

  (testing "create with empty content"
    (let [v (vp/viewport "")]
      (is (= 0 (vp/viewport-line-count v)))))

  (testing "create with nil content"
    (let [v (vp/viewport nil)]
      (is (= 0 (vp/viewport-line-count v)))))

  (testing "y-offset clamped to valid range"
    (let [v (vp/viewport "a\nb\nc" :height 2 :y-offset 100)]
      (is (= 1 (:y-offset v))))))

(deftest viewport-content-test
  (testing "set-content resets scroll"
    (let [v (-> (vp/viewport "a\nb\nc\nd\ne" :height 2)
                (vp/scroll-down)
                (vp/scroll-down)
                (vp/viewport-set-content "x\ny\nz"))]
      (is (= 0 (:y-offset v)))
      (is (= "x\ny\nz" (vp/viewport-content v)))
      (is (= 3 (vp/viewport-line-count v))))))

(deftest viewport-scrolling-test
  (let [content (str/join "\n" (map #(str "line" %) (range 20)))
        v (vp/viewport content :height 5)]

    (testing "scroll-down"
      (let [v2 (vp/scroll-down v)]
        (is (= 1 (:y-offset v2)))))

    (testing "scroll-up"
      (let [v2 (-> v (vp/scroll-down) (vp/scroll-down) (vp/scroll-up))]
        (is (= 1 (:y-offset v2)))))

    (testing "scroll-up at top stays"
      (let [v2 (vp/scroll-up v)]
        (is (= 0 (:y-offset v2)))))

    (testing "scroll-down at bottom stays"
      (let [v2 (-> v (vp/scroll-to-bottom) (vp/scroll-down))]
        (is (= 15 (:y-offset v2)))))

    (testing "half-page-down"
      (let [v2 (vp/scroll-half-page-down v)]
        (is (= 2 (:y-offset v2)))))

    (testing "half-page-up"
      (let [v2 (-> v (vp/scroll-to-bottom) (vp/scroll-half-page-up))]
        (is (= 13 (:y-offset v2)))))

    (testing "page-down"
      (let [v2 (vp/scroll-page-down v)]
        (is (= 5 (:y-offset v2)))))

    (testing "page-up"
      (let [v2 (-> v (vp/scroll-to-bottom) (vp/scroll-page-up))]
        (is (= 10 (:y-offset v2)))))

    (testing "scroll-to-top"
      (let [v2 (-> v (vp/scroll-down) (vp/scroll-down) (vp/scroll-to-top))]
        (is (= 0 (:y-offset v2)))))

    (testing "scroll-to-bottom"
      (let [v2 (vp/scroll-to-bottom v)]
        (is (= 15 (:y-offset v2)))))

    (testing "scroll-to specific line"
      (let [v2 (vp/viewport-scroll-to v 10)]
        (is (= 10 (:y-offset v2)))))

    (testing "scroll-to clamped to max"
      (let [v2 (vp/viewport-scroll-to v 100)]
        (is (= 15 (:y-offset v2)))))))

(deftest viewport-predicates-test
  (let [content (str/join "\n" (map #(str "line" %) (range 20)))
        v (vp/viewport content :height 5)]

    (testing "at-top?"
      (is (vp/viewport-at-top? v))
      (is (not (vp/viewport-at-top? (vp/scroll-down v)))))

    (testing "at-bottom?"
      (is (not (vp/viewport-at-bottom? v)))
      (is (vp/viewport-at-bottom? (vp/scroll-to-bottom v))))

    (testing "at-bottom? when content fits"
      (let [short-v (vp/viewport "a\nb\nc" :height 5)]
        (is (vp/viewport-at-bottom? short-v))))

    (testing "scroll-percent"
      (is (= 0.0 (vp/viewport-scroll-percent v)))
      (is (= 1.0 (vp/viewport-scroll-percent (vp/scroll-to-bottom v)))))))

(deftest viewport-dimensions-test
  (testing "set-dimensions"
    (let [v (-> (vp/viewport "a\nb\nc\nd\ne" :height 2)
                (vp/scroll-to-bottom)
                (vp/viewport-set-dimensions 20 3))]
      (is (= 20 (:width v)))
      (is (= 3 (:height v)))
      ;; y-offset adjusted: max-offset = 5-3 = 2
      (is (<= (:y-offset v) 2)))))

(deftest viewport-view-test
  (testing "view shows all when no height constraint"
    (let [v (vp/viewport "a\nb\nc")
          view (vp/viewport-view v)]
      (is (= "a\nb\nc" view))))

  (testing "view shows only visible lines"
    (let [v (vp/viewport "a\nb\nc\nd\ne" :height 3)
          view (vp/viewport-view v)]
      (is (= "a\nb\nc" view))))

  (testing "view respects scroll offset"
    (let [v (-> (vp/viewport "a\nb\nc\nd\ne" :height 3)
                (vp/scroll-down)
                (vp/scroll-down))]
      (is (= "c\nd\ne" (vp/viewport-view v)))))

  (testing "view pads short content"
    (let [v (vp/viewport "a\nb" :height 4 :width 5)
          view (vp/viewport-view v)
          lines (str/split-lines view)]
      (is (= 4 (count lines)))))

  (testing "view pads to width"
    (let [v (vp/viewport "ab\ncde" :width 5)
          view (vp/viewport-view v)
          lines (str/split-lines view)]
      (is (= 5 (count (first lines))))
      (is (= 5 (count (second lines)))))))

(deftest viewport-update-test
  (let [content (str/join "\n" (map #(str "line" %) (range 20)))
        v (vp/viewport content :height 5)]

    (testing "down arrow scrolls down"
      (let [[v2 _] (vp/viewport-update v (msg/key-press "down"))]
        (is (= 1 (:y-offset v2)))))

    (testing "up arrow scrolls up"
      (let [v1 (vp/scroll-down v)
            [v2 _] (vp/viewport-update v1 (msg/key-press "up"))]
        (is (= 0 (:y-offset v2)))))

    (testing "j scrolls down"
      (let [[v2 _] (vp/viewport-update v (msg/key-press "j"))]
        (is (= 1 (:y-offset v2)))))

    (testing "k scrolls up"
      (let [v1 (vp/scroll-down v)
            [v2 _] (vp/viewport-update v1 (msg/key-press "k"))]
        (is (= 0 (:y-offset v2)))))

    (testing "ctrl+d scrolls half page down"
      (let [[v2 _] (vp/viewport-update v (msg/key-press "d" :ctrl true))]
        (is (= 2 (:y-offset v2)))))

    (testing "ctrl+u scrolls half page up"
      (let [v1 (vp/scroll-to-bottom v)
            [v2 _] (vp/viewport-update v1 (msg/key-press "u" :ctrl true))]
        (is (= 13 (:y-offset v2)))))

    (testing "home scrolls to top"
      (let [v1 (vp/scroll-to-bottom v)
            [v2 _] (vp/viewport-update v1 (msg/key-press "home"))]
        (is (= 0 (:y-offset v2)))))

    (testing "end scrolls to bottom"
      (let [[v2 _] (vp/viewport-update v (msg/key-press "end"))]
        (is (= 15 (:y-offset v2)))))

    (testing "unhandled message returns viewport unchanged"
      (let [[v2 cmd] (vp/viewport-update v (msg/key-press "x"))]
        (is (= v v2))
        (is (nil? cmd))))))

(deftest viewport-init-test
  (testing "init returns viewport and nil command"
    (let [v (vp/viewport "test")
          [new-v cmd] (vp/viewport-init v)]
      (is (= v new-v))
      (is (nil? cmd)))))
