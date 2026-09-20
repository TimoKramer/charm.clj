(ns charm.components.spinner-test
  (:require [clojure.test :refer [deftest testing is]]
            [charm.components.spinner :as spinner]))

(deftest spinner-creation-test
  (testing "create spinner with keyword type"
    (let [s (spinner/spinner :dots)]
      (is (= :spinner (:type s)))
      (is (= 0 (:frame s)))
      (is (= 0 (:tag s)))
      (is (some? (:id s)))
      (is (= 80 (get-in s [:spinner-type :interval])))))

  (testing "create spinner with map type"
    (let [s (spinner/spinner {:frames ["A" "B" "C"] :interval 200})]
      (is (= ["A" "B" "C"] (get-in s [:spinner-type :frames])))
      (is (= 200 (get-in s [:spinner-type :interval])))))

  (testing "create spinner with options"
    (let [s (spinner/spinner :line :id 123)]
      (is (= 123 (:id s))))))

(deftest spinner-types-test
  (testing "predefined spinner types exist"
    (is (contains? spinner/spinner-types :line))
    (is (contains? spinner/spinner-types :dots))
    (is (contains? spinner/spinner-types :moon))
    (is (contains? spinner/spinner-types :clock)))

  (testing "spinner types have required keys"
    (doseq [[name type] spinner/spinner-types]
      (is (vector? (:frames type)) (str name " should have frames vector"))
      (is (number? (:interval type)) (str name " should have interval")))))

(deftest tick-msg-test
  (testing "create tick message"
    (let [msg (spinner/tick-msg 42 1)]
      (is (= :spinner-tick (:type msg)))
      (is (= 42 (:spinner-id msg)))
      (is (= 1 (:tag msg)))))

  (testing "tick-msg? predicate"
    (is (spinner/tick-msg? {:type :spinner-tick}))
    (is (not (spinner/tick-msg? {:type :other})))))

(deftest spinner-init-test
  (testing "spinner-init returns spinner and command"
    (let [s (spinner/spinner :dots)
          [new-s cmd] (spinner/spinner-init s)]
      (is (= s new-s))
      (is (some? cmd))
      (is (= :cmd (:type cmd))))))

(deftest spinner-update-test
  (testing "update advances frame on matching tick"
    (let [s (spinner/spinner :dots :id 42)
          msg (spinner/tick-msg 42 0)
          [new-s cmd] (spinner/spinner-update s msg)]
      (is (= 1 (:frame new-s)))
      (is (= 1 (:tag new-s)))
      (is (some? cmd))))

  (testing "update ignores non-matching tick"
    (let [s (spinner/spinner :dots :id 42)
          msg (spinner/tick-msg 99 0)
          [new-s cmd] (spinner/spinner-update s msg)]
      (is (= 0 (:frame new-s)))
      (is (nil? cmd))))

  (testing "update ignores wrong tag"
    (let [s (spinner/spinner :dots :id 42)
          msg (spinner/tick-msg 42 5)
          [new-s cmd] (spinner/spinner-update s msg)]
      (is (= 0 (:frame new-s)))
      (is (nil? cmd))))

  (testing "frame wraps around"
    (let [s (-> (spinner/spinner :pulse :id 1)
                (assoc :frame 3 :tag 0))  ; pulse has 4 frames
          msg (spinner/tick-msg 1 0)
          [new-s _] (spinner/spinner-update s msg)]
      (is (= 0 (:frame new-s))))))

(deftest spinner-view-test
  (testing "view returns current frame"
    (let [s (spinner/spinner :line)]
      (is (= "|" (spinner/spinner-view s)))))

  (testing "view returns correct frame after update"
    (let [s (-> (spinner/spinner :line)
                (assoc :frame 1))]
      (is (= "/" (spinner/spinner-view s))))))

(deftest spinning?-test
  (testing "spinning? returns true for matching spinner"
    (let [s (spinner/spinner :dots :id 42)
          msg (spinner/tick-msg 42 0)]
      (is (spinner/spinning? s msg))))

  (testing "spinning? returns false for non-matching spinner"
    (let [s (spinner/spinner :dots :id 42)
          msg (spinner/tick-msg 99 0)]
      (is (not (spinner/spinning? s msg))))))

(deftest unique-ids-test
  (testing "each component gets its own id"
    ;; The spinner and the timer route their tick messages back to themselves by
    ;; comparing ids, so a collision makes two of them drive each other
    (let [ids (repeatedly 500 #(:id (spinner/spinner :dots)))]
      (is (= 500 (count (distinct ids))))))

  (testing "an explicit id is still honoured"
    (is (= 42 (:id (spinner/spinner :dots :id 42))))))
