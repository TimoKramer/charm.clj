(ns charm.components.help-test
  (:require
   [charm.components.help :as help]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]))

(deftest help-creation-test
  (testing "create help with binding maps"
    (let [h (help/help [{:key "j/k" :desc "up/down"}
                        {:key "q" :desc "quit"}])]
      (is (= :help (:type h)))
      (is (= 2 (count (help/bindings h))))))

  (testing "create help with binding vectors"
    (let [h (help/help [["j/k" "up/down"]
                        ["q" "quit"]])]
      (is (= 2 (count (help/bindings h))))
      (is (= "j/k" (:key (first (help/bindings h)))))))

  (testing "create help with options"
    (let [h (help/help [] :width 80 :separator " | " :show-all true)]
      (is (= 80 (:width h)))
      (is (= " | " (:separator h)))
      (is (help/show-all? h)))))

(deftest help-accessors-test
  (testing "bindings accessor"
    (let [h (help/help [{:key "q" :desc "quit"}])]
      (is (= [{:key "q" :desc "quit"}] (help/bindings h)))))

  (testing "set-bindings"
    (let [h (help/set-bindings (help/help [])
                               [{:key "x" :desc "exit"}])]
      (is (= [{:key "x" :desc "exit"}] (help/bindings h)))))

  (testing "add-binding"
    (let [h (help/add-binding (help/help []) "q" "quit")]
      (is (= [{:key "q" :desc "quit"}] (help/bindings h)))))

  (testing "set-width"
    (let [h (help/set-width (help/help []) 100)]
      (is (= 100 (:width h)))))

  (testing "show-all?"
    (is (not (help/show-all? (help/help []))))
    (is (help/show-all? (help/help [] :show-all true))))

  (testing "set-show-all"
    (let [h (help/set-show-all (help/help []) true)]
      (is (help/show-all? h))))

  (testing "toggle-show-all"
    (let [h (help/toggle-show-all (help/help []))]
      (is (help/show-all? h)))
    (let [h (help/toggle-show-all (help/help [] :show-all true))]
      (is (not (help/show-all? h))))))

(deftest help-view-test
  (testing "short view shows bindings"
    (let [h (help/help [{:key "j/k" :desc "up/down"}
                        {:key "q" :desc "quit"}])
          view (help/help-view h)]
      (is (str/includes? view "j/k"))
      (is (str/includes? view "up/down"))
      (is (str/includes? view "q"))
      (is (str/includes? view "quit"))))

  (testing "short view includes separator"
    (let [h (help/help [{:key "a" :desc "one"}
                        {:key "b" :desc "two"}]
                       :separator " | ")
          view (help/help-view h)]
      (is (str/includes? view "|"))))

  (testing "full view is multi-line"
    (let [h (help/help [{:key "j/k" :desc "up/down"}
                        {:key "q" :desc "quit"}]
                       :show-all true)
          view (help/help-view h)]
      (is (str/includes? view "\n"))))

  (testing "empty bindings"
    (let [h (help/help [])
          view (help/help-view h)]
      (is (= "" view)))))

(deftest help-update-test
  (testing "update returns help unchanged"
    (let [h (help/help [{:key "q" :desc "quit"}])
          [new-h cmd] (help/help-update h {:type :some-msg})]
      (is (= h new-h))
      (is (nil? cmd)))))

(deftest help-init-test
  (testing "init returns help and nil cmd"
    (let [h (help/help [])
          [new-h cmd] (help/help-init h)]
      (is (= h new-h))
      (is (nil? cmd)))))

(deftest from-pairs-test
  (testing "from-pairs with vectors"
    (let [bindings (help/from-pairs ["a" "one"] ["b" "two"])]
      (is (= [{:key "a" :desc "one"}
              {:key "b" :desc "two"}]
             bindings))))

  (testing "from-pairs with flat args"
    (let [bindings (help/from-pairs "a" "one" "b" "two")]
      (is (= [{:key "a" :desc "one"}
              {:key "b" :desc "two"}]
             bindings))))

  (testing "from-pairs with nested vector"
    (let [bindings (help/from-pairs [["a" "one"] ["b" "two"]])]
      (is (= [{:key "a" :desc "one"}
              {:key "b" :desc "two"}]
             bindings)))))
