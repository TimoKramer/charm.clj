(ns charm.components.list-test
  (:require
   [charm.components.list :as list]
   [charm.message :as msg]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]))

(deftest list-creation-test
  (testing "create list with strings"
    (let [lst (list/item-list ["Apple" "Banana" "Cherry"])]
      (is (= :list (:type lst)))
      (is (= ["Apple" "Banana" "Cherry"] (list/items lst)))
      (is (= 0 (list/selected-index lst)))
      (is (= "Apple" (list/selected-item lst)))))

  (testing "create list with options"
    (let [lst (list/item-list ["A" "B"] :cursor 1 :title "Fruits")]
      (is (= 1 (list/selected-index lst)))
      (is (= "B" (list/selected-item lst)))
      (is (= "Fruits" (:title lst)))))

  (testing "cursor clamped to valid range"
    (let [lst (list/item-list ["A" "B"] :cursor 10)]
      (is (= 1 (list/selected-index lst)))))

  (testing "empty list"
    (let [lst (list/item-list [])]
      (is (= 0 (list/item-count lst)))
      (is (nil? (list/selected-item lst))))))

(deftest list-item-protocol-test
  (testing "string items"
    (is (= "hello" (list/item-title "hello")))
    (is (nil? (list/item-description "hello"))))

  (testing "map items"
    (let [item {:title "Apple" :description "A fruit"}]
      (is (= "Apple" (list/item-title item)))
      (is (= "A fruit" (list/item-description item)))))

  (testing "map with name key"
    (let [item {:name "Banana"}]
      (is (= "Banana" (list/item-title item))))))

(deftest list-accessors-test
  (testing "items accessor"
    (let [lst (list/item-list ["A" "B" "C"])]
      (is (= ["A" "B" "C"] (list/items lst)))))

  (testing "item-count"
    (let [lst (list/item-list ["A" "B" "C"])]
      (is (= 3 (list/item-count lst)))))

  (testing "set-items"
    (let [lst (-> (list/item-list ["A" "B"] :cursor 1)
                  (list/set-items ["X" "Y" "Z"]))]
      (is (= ["X" "Y" "Z"] (list/items lst)))
      (is (= 1 (list/selected-index lst)))))

  (testing "set-items adjusts cursor"
    (let [lst (-> (list/item-list ["A" "B" "C"] :cursor 2)
                  (list/set-items ["X"]))]
      (is (= 0 (list/selected-index lst)))))

  (testing "select"
    (let [lst (list/select (list/item-list ["A" "B" "C"]) 2)]
      (is (= 2 (list/selected-index lst)))
      (is (= "C" (list/selected-item lst))))))

(deftest cursor-navigation-test
  (testing "cursor-up"
    (let [lst (-> (list/item-list ["A" "B" "C"])
                  (list/select 2)
                  list/cursor-up)]
      (is (= 1 (list/selected-index lst)))))

  (testing "cursor-up at top stays"
    (let [lst (list/cursor-up (list/item-list ["A" "B" "C"]))]
      (is (= 0 (list/selected-index lst)))))

  (testing "cursor-up with infinite scroll wraps"
    (let [lst (-> (list/item-list ["A" "B" "C"] :infinite-scroll true)
                  list/cursor-up)]
      (is (= 2 (list/selected-index lst)))))

  (testing "cursor-down"
    (let [lst (list/cursor-down (list/item-list ["A" "B" "C"]))]
      (is (= 1 (list/selected-index lst)))))

  (testing "cursor-down at bottom stays"
    (let [lst (-> (list/item-list ["A" "B" "C"])
                  (list/select 2)
                  list/cursor-down)]
      (is (= 2 (list/selected-index lst)))))

  (testing "cursor-down with infinite scroll wraps"
    (let [lst (-> (list/item-list ["A" "B" "C"] :infinite-scroll true)
                  (list/select 2)
                  list/cursor-down)]
      (is (= 0 (list/selected-index lst)))))

  (testing "go-to-start"
    (let [lst (-> (list/item-list ["A" "B" "C"])
                  (list/select 2)
                  list/go-to-start)]
      (is (= 0 (list/selected-index lst)))))

  (testing "go-to-end"
    (let [lst (list/go-to-end (list/item-list ["A" "B" "C"]))]
      (is (= 2 (list/selected-index lst)))))

  (testing "page-up"
    (let [lst (-> (list/item-list (map str (range 20)) :height 5)
                  (list/select 10)
                  list/page-up)]
      (is (= 5 (list/selected-index lst)))))

  (testing "page-down"
    (let [lst (-> (list/item-list (map str (range 20)) :height 5)
                  list/page-down)]
      (is (= 5 (list/selected-index lst))))))

(deftest list-update-test
  (testing "up arrow moves cursor up"
    (let [lst (list/select (list/item-list ["A" "B" "C"]) 1)
          msg (msg/key-press "up")
          [new-lst _] (list/list-update lst msg)]
      (is (= 0 (list/selected-index new-lst)))))

  (testing "down arrow moves cursor down"
    (let [lst (list/item-list ["A" "B" "C"])
          msg (msg/key-press "down")
          [new-lst _] (list/list-update lst msg)]
      (is (= 1 (list/selected-index new-lst)))))

  (testing "j moves cursor down"
    (let [lst (list/item-list ["A" "B" "C"])
          msg (msg/key-press "j")
          [new-lst _] (list/list-update lst msg)]
      (is (= 1 (list/selected-index new-lst)))))

  (testing "k moves cursor up"
    (let [lst (list/select (list/item-list ["A" "B" "C"]) 1)
          msg (msg/key-press "k")
          [new-lst _] (list/list-update lst msg)]
      (is (= 0 (list/selected-index new-lst)))))

  (testing "home moves to start"
    (let [lst (list/select (list/item-list ["A" "B" "C"]) 2)
          msg (msg/key-press "home")
          [new-lst _] (list/list-update lst msg)]
      (is (= 0 (list/selected-index new-lst)))))

  (testing "end moves to end"
    (let [lst (list/item-list ["A" "B" "C"])
          msg (msg/key-press "end")
          [new-lst _] (list/list-update lst msg)]
      (is (= 2 (list/selected-index new-lst)))))

  (testing "unhandled message returns list unchanged"
    (let [lst (list/item-list ["A" "B" "C"])
          msg (msg/key-press "x")
          [new-lst cmd] (list/list-update lst msg)]
      (is (= lst new-lst))
      (is (nil? cmd)))))

(deftest list-view-test
  (testing "view shows items"
    (let [lst (list/item-list ["Apple" "Banana"])
          view (list/list-view lst)]
      (is (str/includes? view "Apple"))
      (is (str/includes? view "Banana"))))

  (testing "view shows title"
    (let [lst (list/item-list ["A" "B"] :title "Fruits")
          view (list/list-view lst)]
      (is (str/includes? view "Fruits"))))

  (testing "view shows cursor prefix on selected"
    (let [lst (list/item-list ["A" "B"] :cursor-prefix "> " :item-prefix "  ")
          view (list/list-view lst)]
      (is (str/includes? view "> "))
      (is (str/includes? view "  B")))))

(deftest list-init-test
  (testing "init returns list and nil command"
    (let [lst (list/item-list ["A" "B"])
          [new-lst cmd] (list/list-init lst)]
      (is (= lst new-lst))
      (is (nil? cmd)))))

(deftest filter-convenience-test
  (testing "filter-items"
    (let [lst (-> (list/item-list ["Apple" "Apricot" "Banana"])
                  (list/filter-items #(str/starts-with? % "A")))]
      (is (= ["Apple" "Apricot"] (list/items lst)))))

  (testing "find-item"
    (let [lst (list/item-list ["Apple" "Banana" "Cherry"])]
      (is (= 1 (list/find-item lst #(= % "Banana"))))
      (is (nil? (list/find-item lst #(= % "Date"))))))

  (testing "select-first-match"
    (let [lst (list/select-first-match
               (list/item-list ["Apple" "Banana" "Cherry"])
               #(= % "Cherry"))]
      (is (= 2 (list/selected-index lst))))))
