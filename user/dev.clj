(ns dev)

(comment
  (require '[clojure.test :refer [run-all-tests]])
  (run-all-tests #"charm.*-test"))

(comment
  (require '[portal.api :as p])
  (def p (p/open))
  (add-tap #'p/submit))

(comment
  (require '[charm.program :as program]
           '[charm.message :as msg])

  (defn update-fn [state msg]
    (tap> {:msg msg})
    (cond
      (msg/key-match? msg :q) [state program/quit-cmd]
      (msg/key-match? msg :k) [(update state :count inc) nil]
      (msg/key-match? msg :j) [(update state :count dec) nil]
      :else [state nil]))

  ;; Redef and watch:
  (defn view-fn [state]
    (str "🎯 Count: " (:count state) "\n[j/k] count  [q] quit"))


  (def p (program/run-async {:init {:count 0}
                             :update #'update-fn
                             :view #'view-fn}))

  ((:quit! p)))
