(ns charm.components.progress-test
  (:require
   [charm.components.progress :as prog]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]))

(deftest progress-creation-test
  (testing "create progress bar with defaults"
    (let [p (prog/progress-bar)]
      (is (= :progress (:type p)))
      (is (= 40 (:width p)))
      (is (= 0.0 (prog/percent p)))
      (is (not (:show-percent p)))))

  (testing "create progress bar with options"
    (let [p (prog/progress-bar :width 20 :percent 0.5 :show-percent true)]
      (is (= 20 (:width p)))
      (is (= 0.5 (prog/percent p)))
      (is (:show-percent p))))

  (testing "create with bar style keyword"
    (let [p (prog/progress-bar :bar-style :ascii)]
      (is (= "#" (get-in p [:bar-style :full])))
      (is (= "-" (get-in p [:bar-style :empty])))))

  (testing "create with custom bar style"
    (let [p (prog/progress-bar :bar-style {:full "X" :empty "."})]
      (is (= "X" (get-in p [:bar-style :full])))
      (is (= "." (get-in p [:bar-style :empty]))))))

(deftest progress-accessors-test
  (testing "percent"
    (let [p (prog/progress-bar :percent 0.75)]
      (is (= 0.75 (prog/percent p)))))

  (testing "percent-int"
    (let [p (prog/progress-bar :percent 0.75)]
      (is (= 75 (prog/percent-int p)))))

  (testing "set-progress"
    (let [p (prog/set-progress (prog/progress-bar) 0.5)]
      (is (= 0.5 (prog/percent p)))))

  (testing "set-progress clamped to 0"
    (let [p (prog/set-progress (prog/progress-bar) -0.5)]
      (is (= 0.0 (prog/percent p)))))

  (testing "set-progress clamped to 1"
    (let [p (prog/set-progress (prog/progress-bar) 1.5)]
      (is (= 1.0 (prog/percent p)))))

  (testing "set-progress-int"
    (let [p (prog/set-progress-int (prog/progress-bar) 75)]
      (is (= 0.75 (prog/percent p))))))

(deftest progress-increment-test
  (testing "increment default"
    (let [p (prog/increment (prog/progress-bar :percent 0.5))]
      (is (= 0.51 (prog/percent p)))))

  (testing "increment with amount"
    (let [p (prog/increment (prog/progress-bar :percent 0.5) 0.1)]
      (is (= 0.6 (prog/percent p)))))

  (testing "decrement default"
    (let [p (prog/decrement (prog/progress-bar :percent 0.5))]
      (is (= 0.49 (prog/percent p)))))

  (testing "decrement with amount"
    (let [p (prog/decrement (prog/progress-bar :percent 0.5) 0.1)]
      (is (= 0.4 (prog/percent p))))))

(deftest progress-state-test
  (testing "complete? when 100%"
    (is (prog/complete? (prog/progress-bar :percent 1.0))))

  (testing "complete? when over 100%"
    (is (prog/complete? (prog/progress-bar :percent 1.5))))

  (testing "complete? false when under 100%"
    (is (not (prog/complete? (prog/progress-bar :percent 0.99)))))

  (testing "reset"
    (let [p (prog/reset (prog/progress-bar :percent 0.75))]
      (is (= 0.0 (prog/percent p))))))

(deftest progress-view-test
  (testing "view shows filled and empty"
    (let [p (prog/progress-bar :width 10 :percent 0.5 :bar-style :ascii)
          view (prog/progress-view p)]
      (is (str/includes? view "#"))
      (is (str/includes? view "-"))))

  (testing "view with brackets"
    (let [p (prog/progress-bar :width 12 :percent 0.5 :bar-style :brackets)
          view (prog/progress-view p)]
      (is (str/starts-with? view "["))
      (is (str/includes? view "]"))))

  (testing "view with percent"
    (let [p (prog/progress-bar :width 20 :percent 0.5 :show-percent true)
          view (prog/progress-view p)]
      (is (str/includes? view "50%"))))

  (testing "view at 0%"
    (let [p (prog/progress-bar :width 10 :percent 0 :bar-style :ascii)
          view (prog/progress-view p)]
      (is (not (str/includes? view "#")))
      (is (str/includes? view "-"))))

  (testing "view at 100%"
    (let [p (prog/progress-bar :width 10 :percent 1.0 :bar-style :ascii)
          view (prog/progress-view p)]
      (is (str/includes? view "#"))
      (is (not (str/includes? view "-"))))))

(deftest progress-update-test
  (testing "update returns bar unchanged"
    (let [p (prog/progress-bar :percent 0.5)
          [new-p cmd] (prog/progress-update p {:type :some-msg})]
      (is (= p new-p))
      (is (nil? cmd)))))

(deftest progress-init-test
  (testing "init returns bar and nil cmd"
    (let [p (prog/progress-bar)
          [new-p cmd] (prog/progress-init p)]
      (is (= p new-p))
      (is (nil? cmd)))))

(deftest bar-styles-test
  (testing "predefined styles exist"
    (is (contains? prog/bar-styles :default))
    (is (contains? prog/bar-styles :ascii))
    (is (contains? prog/bar-styles :thin))
    (is (contains? prog/bar-styles :blocks))
    (is (contains? prog/bar-styles :brackets)))

  (testing "styles have full and empty"
    (doseq [[name style] prog/bar-styles]
      (is (contains? style :full) (str name " should have :full"))
      (is (contains? style :empty) (str name " should have :empty")))))
