(ns charm.components.text-input-test
  (:require
   [charm.components.text-input :as ti]
   [charm.message :as msg]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]))

(deftest text-input-creation-test
  (testing "create text input with defaults"
    (let [input (ti/text-input)]
      (is (= :text-input (:type input)))
      (is (= "> " (:prompt input)))
      (is (= [] (:value input)))
      (is (= 0 (:pos input)))
      (is (= :normal (:echo-mode input)))
      (is (:focused input))))

  (testing "create text input with options"
    (let [input (ti/text-input :prompt "Name: "
                               :placeholder "Enter name"
                               :value "hello"
                               :echo-mode :password)]
      (is (= "Name: " (:prompt input)))
      (is (= "Enter name" (:placeholder input)))
      (is (= [\h \e \l \l \o] (:value input)))
      (is (= 5 (:pos input)))
      (is (= :password (:echo-mode input))))))

(deftest value-accessors-test
  (testing "get value as string"
    (let [input (ti/text-input :value "hello")]
      (is (= "hello" (ti/value input)))))

  (testing "set value"
    (let [input (ti/set-value (ti/text-input) "world")]
      (is (= "world" (ti/value input)))
      (is (= 5 (ti/position input)))))

  (testing "set value respects char limit"
    (let [input (-> (ti/text-input :char-limit 3)
                    (ti/set-value "hello"))]
      (is (= "hel" (ti/value input))))))

(deftest focus-blur-test
  (testing "focus and blur"
    (let [input (ti/text-input :focused false)]
      (is (not (ti/focused? input)))
      (is (ti/focused? (ti/focus input)))
      (is (not (ti/focused? (ti/blur (ti/focus input)))))))

  (testing "reset clears value"
    (let [input (ti/reset (ti/text-input :value "hello"))]
      (is (= "" (ti/value input)))
      (is (= 0 (ti/position input))))))

(deftest cursor-movement-test
  (testing "cursor-start moves to beginning"
    (let [input (ti/cursor-start (ti/text-input :value "hello"))]
      (is (= 0 (ti/position input)))))

  (testing "cursor-end moves to end"
    (let [input (-> (ti/text-input :value "hello")
                    ti/cursor-start
                    ti/cursor-end)]
      (is (= 5 (ti/position input))))))

(deftest text-input-update-test
  (testing "left arrow moves cursor left"
    (let [input (ti/text-input :value "hello")
          msg (msg/key-press "left")
          [new-input _] (ti/text-input-update input msg)]
      (is (= 4 (ti/position new-input)))))

  (testing "right arrow moves cursor right"
    (let [input (-> (ti/text-input :value "hello")
                    (assoc :pos 2))
          msg (msg/key-press "right")
          [new-input _] (ti/text-input-update input msg)]
      (is (= 3 (ti/position new-input)))))

  (testing "backspace deletes character"
    (let [input (ti/text-input :value "hello")
          msg (msg/key-press "backspace")
          [new-input _] (ti/text-input-update input msg)]
      (is (= "hell" (ti/value new-input)))
      (is (= 4 (ti/position new-input)))))

  (testing "delete key deletes forward"
    (let [input (-> (ti/text-input :value "hello")
                    (assoc :pos 2))
          msg (msg/key-press "delete")
          [new-input _] (ti/text-input-update input msg)]
      (is (= "helo" (ti/value new-input)))
      (is (= 2 (ti/position new-input)))))

  (testing "typing inserts characters"
    (let [input (-> (ti/text-input :value "hlo")
                    (assoc :pos 1))
          msg (msg/key-press "el")
          [new-input _] (ti/text-input-update input msg)]
      (is (= "hello" (ti/value new-input)))
      (is (= 3 (ti/position new-input)))))

  (testing "home moves to start"
    (let [input (ti/text-input :value "hello")
          msg (msg/key-press "home")
          [new-input _] (ti/text-input-update input msg)]
      (is (= 0 (ti/position new-input)))))

  (testing "end moves to end"
    (let [input (-> (ti/text-input :value "hello")
                    (assoc :pos 0))
          msg (msg/key-press "end")
          [new-input _] (ti/text-input-update input msg)]
      (is (= 5 (ti/position new-input)))))

  (testing "unfocused input ignores messages"
    (let [input (ti/text-input :value "hello" :focused false)
          msg (msg/key-press "x")
          [new-input _] (ti/text-input-update input msg)]
      (is (= "hello" (ti/value new-input))))))

(deftest char-limit-test
  (testing "char limit prevents insertion"
    (let [input (ti/text-input :value "hel" :char-limit 5)
          msg (msg/key-press "loworld")
          [new-input _] (ti/text-input-update input msg)]
      (is (= "hello" (ti/value new-input)))
      (is (= 5 (ti/position new-input))))))

(deftest text-input-view-test
  (testing "view shows prompt and value"
    (let [input (ti/text-input :prompt "> " :value "hi" :focused false)]
      (is (str/includes? (ti/text-input-view input) "> "))
      (is (str/includes? (ti/text-input-view input) "hi"))))

  (testing "view shows placeholder when empty"
    (let [input (ti/text-input :placeholder "type here" :focused false)]
      (is (str/includes? (ti/text-input-view input) "type here"))))

  (testing "password mode hides text"
    (let [input (ti/text-input :value "secret" :echo-mode :password :focused false)
          view (ti/text-input-view input)]
      (is (not (str/includes? view "secret")))
      (is (str/includes? view "******")))))

(deftest text-input-init-test
  (testing "init returns input and nil command"
    (let [input (ti/text-input)
          [new-input cmd] (ti/text-input-init input)]
      (is (= input new-input))
      (is (nil? cmd)))))
