(ns charm.components.paginator-test
  (:require
   [charm.components.paginator :as pag]
   [charm.message :as msg]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]))

(deftest paginator-creation-test
  (testing "create paginator with defaults"
    (let [p (pag/paginator)]
      (is (= :paginator (:type p)))
      (is (= 0 (pag/page p)))
      (is (= 1 (pag/total-pages p)))
      (is (= 1 (pag/per-page p)))
      (is (= :dots (:display-type p)))))

  (testing "create paginator with options"
    (let [p (pag/paginator :total-pages 5 :page 2 :type :arabic)]
      (is (= 2 (pag/page p)))
      (is (= 5 (pag/total-pages p)))
      (is (= :arabic (:display-type p)))))

  (testing "page clamped to valid range"
    (let [p (pag/paginator :total-pages 3 :page 10)]
      (is (= 2 (pag/page p))))))

(deftest paginator-accessors-test
  (testing "set-page"
    (let [p (pag/set-page (pag/paginator :total-pages 5) 3)]
      (is (= 3 (pag/page p)))))

  (testing "set-page clamped"
    (let [p (pag/set-page (pag/paginator :total-pages 5) 10)]
      (is (= 4 (pag/page p)))))

  (testing "set-total-pages"
    (let [p (pag/set-total-pages (pag/paginator) 10)]
      (is (= 10 (pag/total-pages p)))))

  (testing "set-total-pages adjusts page"
    (let [p (-> (pag/paginator :total-pages 10 :page 8)
                (pag/set-total-pages 3))]
      (is (= 2 (pag/page p)))))

  (testing "set-total-items"
    (let [p (-> (pag/paginator :per-page 10)
                (pag/set-total-items 25))]
      (is (= 3 (pag/total-pages p))))))

(deftest paginator-navigation-test
  (testing "on-first-page?"
    (is (pag/on-first-page? (pag/paginator)))
    (is (not (pag/on-first-page? (pag/paginator :total-pages 3 :page 1)))))

  (testing "on-last-page?"
    (is (pag/on-last-page? (pag/paginator)))
    (is (pag/on-last-page? (pag/paginator :total-pages 3 :page 2)))
    (is (not (pag/on-last-page? (pag/paginator :total-pages 3 :page 1)))))

  (testing "next-page"
    (let [p (pag/next-page (pag/paginator :total-pages 3))]
      (is (= 1 (pag/page p)))))

  (testing "next-page at end stays"
    (let [p (pag/next-page (pag/paginator :total-pages 3 :page 2))]
      (is (= 2 (pag/page p)))))

  (testing "prev-page"
    (let [p (pag/prev-page (pag/paginator :total-pages 3 :page 2))]
      (is (= 1 (pag/page p)))))

  (testing "prev-page at start stays"
    (let [p (pag/prev-page (pag/paginator :total-pages 3))]
      (is (= 0 (pag/page p)))))

  (testing "go-to-first"
    (let [p (pag/go-to-first (pag/paginator :total-pages 5 :page 3))]
      (is (= 0 (pag/page p)))))

  (testing "go-to-last"
    (let [p (pag/go-to-last (pag/paginator :total-pages 5))]
      (is (= 4 (pag/page p))))))

(deftest slice-bounds-test
  (testing "slice-bounds"
    (let [p (pag/paginator :per-page 10 :total-pages 3 :page 1)]
      (is (= [10 20] (pag/slice-bounds p 25)))))

  (testing "slice-bounds last page partial"
    (let [p (pag/paginator :per-page 10 :total-pages 3 :page 2)]
      (is (= [20 25] (pag/slice-bounds p 25)))))

  (testing "items-on-page"
    (let [p (pag/paginator :per-page 10 :total-pages 3 :page 2)]
      (is (= 5 (pag/items-on-page p 25))))))

(deftest paginator-update-test
  (testing "right arrow goes to next page"
    (let [p (pag/paginator :total-pages 3)
          msg (msg/key-press "right")
          [new-p _] (pag/paginator-update p msg)]
      (is (= 1 (pag/page new-p)))))

  (testing "left arrow goes to prev page"
    (let [p (pag/paginator :total-pages 3 :page 1)
          msg (msg/key-press "left")
          [new-p _] (pag/paginator-update p msg)]
      (is (= 0 (pag/page new-p)))))

  (testing "unhandled message"
    (let [p (pag/paginator :total-pages 3)
          msg (msg/key-press "x")
          [new-p cmd] (pag/paginator-update p msg)]
      (is (= p new-p))
      (is (nil? cmd)))))

(deftest paginator-view-test
  (testing "dots view"
    (let [p (pag/paginator :total-pages 5 :page 2)
          view (pag/paginator-view p)]
      (is (str/includes? view "○"))
      (is (str/includes? view "•"))))

  (testing "arabic view"
    (let [p (pag/paginator :total-pages 5 :page 2 :type :arabic)
          view (pag/paginator-view p)]
      (is (str/includes? view "3/5")))))

(deftest paginator-init-test
  (testing "init returns pager and nil cmd"
    (let [p (pag/paginator)
          [new-p cmd] (pag/paginator-init p)]
      (is (= p new-p))
      (is (nil? cmd)))))
