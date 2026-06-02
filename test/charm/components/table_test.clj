(ns charm.components.table-test
  (:require
   [charm.components.table :as table]
   [charm.message :as msg]
   [clojure.string :as str]
   [clojure.walk :refer [stringify-keys]]
   [clojure.set :refer [difference]]
   [clojure.test :refer [deftest is testing]]))

(def sample-columns
  [{:title "Name" :width 10} {:title "Value" :width 15}])

(def sample-rows
  [["foo" "bar"]
   ["baz" "qux"]
   ["hello" "world"]])

(def sample-tabular-map
  [{:name "John" :family-name "Doe"}
   {:name "Jane" :family-name "Douglass"}])

(deftest table-creation-test
  (testing "create table"
    (let [tbl (table/table sample-columns sample-rows)]
      (is (= :table (:type tbl)))
      (is (= sample-rows (table/table-rows tbl)))
      (is (nil? (table/table-cursor tbl)))))

  (testing "create table with cursor"
    (let [tbl (table/table sample-columns sample-rows :cursor 0)]
      (is (= 0 (table/table-cursor tbl)))
      (is (= ["foo" "bar"] (table/table-selected-row tbl)))))

  (testing "create with options"
    (let [tbl (table/table sample-columns sample-rows :height 2 :header? false)]
      (is (= 2 (:height tbl)))
      (is (false? (:header? tbl))))))

(deftest table-accessors-test
  (testing "table-rows"
    (let [tbl (table/table sample-columns sample-rows)]
      (is (= 3 (table/table-row-count tbl)))))

  (testing "set-rows"
    (let [tbl (-> (table/table sample-columns sample-rows :cursor 2)
                  (table/table-set-rows [["a" "b"]]))]
      (is (= [["a" "b"]] (table/table-rows tbl)))
      (is (= 0 (table/table-cursor tbl)))))

  (testing "set-cursor"
    (let [tbl (-> (table/table sample-columns sample-rows :cursor 0)
                  (table/table-set-cursor 2))]
      (is (= 2 (table/table-cursor tbl)))
      (is (= ["hello" "world"] (table/table-selected-row tbl)))))

  (testing "set-cursor clamped"
    (let [tbl (-> (table/table sample-columns sample-rows :cursor 0)
                  (table/table-set-cursor 100))]
      (is (= 2 (table/table-cursor tbl))))))

(deftest table-navigation-test
  (testing "cursor-down"
    (let [tbl (-> (table/table sample-columns sample-rows :cursor 0)
                  table/cursor-down)]
      (is (= 1 (table/table-cursor tbl)))))

  (testing "cursor-down at bottom stays"
    (let [tbl (-> (table/table sample-columns sample-rows :cursor 2)
                  table/cursor-down)]
      (is (= 2 (table/table-cursor tbl)))))

  (testing "cursor-up"
    (let [tbl (-> (table/table sample-columns sample-rows :cursor 1)
                  table/cursor-up)]
      (is (= 0 (table/table-cursor tbl)))))

  (testing "cursor-up at top stays"
    (let [tbl (-> (table/table sample-columns sample-rows :cursor 0)
                  table/cursor-up)]
      (is (= 0 (table/table-cursor tbl)))))

  (testing "go-to-start"
    (let [tbl (-> (table/table sample-columns sample-rows :cursor 2)
                  table/go-to-start)]
      (is (= 0 (table/table-cursor tbl)))))

  (testing "go-to-end"
    (let [tbl (-> (table/table sample-columns sample-rows :cursor 0)
                  table/go-to-end)]
      (is (= 2 (table/table-cursor tbl)))))

  (testing "page-up"
    (let [rows (mapv (fn [i] [(str "row" i) (str i)]) (range 20))
          tbl (-> (table/table sample-columns rows :cursor 10 :height 5)
                  table/page-up)]
      (is (= 5 (table/table-cursor tbl)))))

  (testing "page-down"
    (let [rows (mapv (fn [i] [(str "row" i) (str i)]) (range 20))
          tbl (-> (table/table sample-columns rows :cursor 0 :height 5)
                  table/page-down)]
      (is (= 5 (table/table-cursor tbl))))))

(deftest table-update-test
  (testing "no cursor means no navigation"
    (let [tbl (table/table sample-columns sample-rows)
          [new-tbl cmd] (table/table-update tbl (msg/key-press "down"))]
      (is (= tbl new-tbl))
      (is (nil? cmd))))

  (testing "down arrow moves cursor"
    (let [tbl (table/table sample-columns sample-rows :cursor 0)
          [new-tbl _] (table/table-update tbl (msg/key-press "down"))]
      (is (= 1 (table/table-cursor new-tbl)))))

  (testing "up arrow moves cursor"
    (let [tbl (table/table sample-columns sample-rows :cursor 1)
          [new-tbl _] (table/table-update tbl (msg/key-press "up"))]
      (is (= 0 (table/table-cursor new-tbl)))))

  (testing "j moves cursor down"
    (let [tbl (table/table sample-columns sample-rows :cursor 0)
          [new-tbl _] (table/table-update tbl (msg/key-press "j"))]
      (is (= 1 (table/table-cursor new-tbl)))))

  (testing "k moves cursor up"
    (let [tbl (table/table sample-columns sample-rows :cursor 1)
          [new-tbl _] (table/table-update tbl (msg/key-press "k"))]
      (is (= 0 (table/table-cursor new-tbl)))))

  (testing "unhandled message returns table unchanged"
    (let [tbl (table/table sample-columns sample-rows :cursor 0)
          [new-tbl cmd] (table/table-update tbl (msg/key-press "x"))]
      (is (= tbl new-tbl))
      (is (nil? cmd)))))

(deftest table-view-test
  (testing "view shows header and rows"
    (let [tbl (table/table sample-columns sample-rows)
          view (table/table-view tbl)]
      (is (str/includes? view "Name"))
      (is (str/includes? view "Value"))
      (is (str/includes? view "foo"))
      (is (str/includes? view "world"))))

  (testing "view without header"
    (let [tbl (table/table sample-columns sample-rows :header? false)
          view (table/table-view tbl)]
      (is (not (str/includes? view "Name")))
      (is (str/includes? view "foo"))))

  (testing "view has correct number of lines"
    (let [tbl (table/table sample-columns sample-rows)
          view (table/table-view tbl)
          lines (str/split-lines view)]
      ;; header + 3 rows = 4 lines
      (is (= 4 (count lines)))))

  (testing "view without header has correct number of lines"
    (let [tbl (table/table sample-columns sample-rows :header? false)
          view (table/table-view tbl)
          lines (str/split-lines view)]
      (is (= 3 (count lines))))))

(deftest table-init-test
  (testing "init returns table and nil command"
    (let [tbl (table/table sample-columns sample-rows)
          [new-tbl cmd] (table/table-init tbl)]
      (is (= tbl new-tbl))
      (is (nil? cmd)))))

(deftest table-map->cols+rows-test
  (testing "returns correct output for correct input"
    (let [[columnsv rowsv] (table/table-map->cols+rows sample-tabular-map)]
      ;; Columns are correctly parsed
      (is (empty? (difference
                   (set columnsv)
                   (set (keys (stringify-keys (first sample-tabular-map)))))))
      ;; Rows are correctly parsed
      (is (and
           (= (val (ffirst sample-tabular-map)) (ffirst rowsv))
           (= (val (last (last sample-tabular-map))) (last (last rowsv))))))))
