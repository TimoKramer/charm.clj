(ns charm.program-test
  (:require [clojure.test :refer [deftest is testing]]
            [charm.program :as p]
            [charm.message :as msg]
            [charm.render.core :as render]
            [clojure.core.async :as a]
            [charm.style.color :as color]))

;; Note: Full program tests require terminal interaction.
;; These tests focus on command and message helper functions.

(deftest cmd-test
  (testing "creates command from function"
    (let [c (p/cmd (fn [] (msg/quit)))]
      (is (= :cmd (:type c)))
      (is (fn? (:fn c))))))

(deftest batch-test
  (testing "combines multiple commands"
    (let [c1 (p/cmd (fn [] :a))
          c2 (p/cmd (fn [] :b))
          batch (p/batch c1 c2)]
      (is (= :batch (:type batch)))
      (is (= 2 (count (:cmds batch))))))

  (testing "filters nil commands"
    (let [c1 (p/cmd (fn [] :a))
          batch (p/batch c1 nil nil)]
      (is (= 1 (count (:cmds batch)))))))

(deftest sequence-cmds-test
  (testing "creates sequence of commands"
    (let [c1 (p/cmd (fn [] :a))
          c2 (p/cmd (fn [] :b))
          seq (p/sequence-cmds c1 c2)]
      (is (= :sequence (:type seq)))
      (is (= 2 (count (:cmds seq)))))))

(deftest quit-cmd-test
  (testing "quit-cmd is a command"
    (is (= :cmd (:type p/quit-cmd)))
    (is (fn? (:fn p/quit-cmd))))

  (testing "quit-cmd produces quit message"
    (let [result ((:fn p/quit-cmd))]
      (is (msg/quit? result)))))

(deftest window-size-msg-test
  (testing "creates window size message"
    (let [m (p/window-size-msg 80 24)]
      (is (= :window-size (:type m)))
      (is (= 80 (:width m)))
      (is (= 24 (:height m))))))

(deftest resolve-color-profile-test
  (let [resolve-profile #'p/resolve-color-profile]
    (testing "a pinned profile is used as given"
      (doseq [profile color/color-profiles]
        (is (= profile (resolve-profile profile)))))

    (testing "nil detects from the environment"
      (is (contains? color/color-profiles (resolve-profile nil))))

    (testing "an unknown profile throws, naming the value"
      (let [e (is (thrown? clojure.lang.ExceptionInfo (resolve-profile :256color)))]
        (is (= :256color (:color-profile (ex-data e))))))))

(defn- ctx
  "A loop context whose renderer and view are stand-ins - handle-msg! only
   touches the renderer for a resize, and never the view."
  [update state-atom & {:as overrides}]
  (merge {:renderer ::renderer
          :update update
          :view (constantly "")
          :state state-atom
          :msg-chan nil
          :running? (atom true)
          :fps 60
          :ctrl-c :quit}
         overrides))

(deftest handle-msg-test
  (let [handle-msg! #'p/handle-msg!]
    (testing "a changed state dirties the frame"
      (let [state (atom {:n 0})]
        (is (true? (handle-msg! (ctx (fn [st _] [(update st :n inc) nil]) state)
                                (msg/key-press "j"))))
        (is (= {:n 1} @state))))

    (testing "an unchanged state does not"
      (let [state (atom {:n 0})]
        (is (false? (handle-msg! (ctx (fn [st _] [st nil]) state)
                                 (msg/key-press "x"))))
        (is (= {:n 0} @state))))

    (testing "an equal but not identical state does"
      (let [state (atom {:n 0})]
        (is (true? (handle-msg! (ctx (fn [st _] [(into {} st) nil]) state)
                                (msg/key-press "x"))))))

    (testing "a resize dirties the frame even when the app ignores it"
      (let [state (atom {:n 0})
            resized (atom nil)]
        (with-redefs [render/update-size! (fn [_ w h] (reset! resized [w h]))]
          (is (true? (handle-msg! (ctx (fn [st _] [st nil]) state)
                                  (p/window-size-msg 80 24))))
          (is (= [80 24] @resized)))))

    (testing "ctrl+c quits by default, without reaching update"
      (let [running? (atom true)
            seen (atom [])]
        (is (false? (handle-msg! (ctx (fn [st m] (swap! seen conj m) [st nil])
                                      (atom {}) :running? running?)
                                 (msg/key-press "c" :ctrl true))))
        (is (false? @running?))
        (is (empty? @seen))))

    (testing ":ctrl-c :message delivers it to update instead"
      (let [running? (atom true)
            seen (atom [])]
        (handle-msg! (ctx (fn [st m] (swap! seen conj m) [st nil])
                          (atom {}) :running? running? :ctrl-c :message)
                     (msg/key-press "c" :ctrl true))
        (is (true? @running?))
        (is (= 1 (count @seen)))
        (is (msg/key-match? (first @seen) "ctrl+c"))))

    (testing "a plain c is not ctrl+c"
      (let [running? (atom true)]
        (handle-msg! (ctx (fn [st _] [st nil]) (atom {}) :running? running?)
                     (msg/key-press "c"))
        (is (true? @running?))))

    (testing "quit stops the loop"
      (let [running? (atom true)]
        (is (false? (handle-msg! (ctx (fn [st _] [st nil]) (atom {}) :running? running?)
                                 (msg/quit))))
        (is (false? @running?))))

    (testing "an error stops the loop and is rethrown"
      (let [running? (atom true)
            boom (ex-info "boom" {})]
        (is (thrown? clojure.lang.ExceptionInfo
                     (handle-msg! (ctx (fn [st _] [st nil]) (atom {}) :running? running?)
                                  (msg/error boom))))
        (is (false? @running?))))))

(deftest drain-test
  (let [drain! #'p/drain!]
    (testing "handles the message in hand and everything queued behind it"
      (let [state (atom {:seen []})
            msg-chan (a/chan 8)
            c (ctx (fn [st m] [(update st :seen conj (:key m)) nil]) state :msg-chan msg-chan)]
        (a/>!! msg-chan (msg/key-press "b"))
        (a/>!! msg-chan (msg/key-press "c"))
        (is (true? (drain! c (msg/key-press "a"))))
        (is (= ["a" "b" "c"] (:seen @state)))
        ;; Everything was taken
        (is (nil? (a/poll! msg-chan)))))

    (testing "one dirty message in a batch dirties the batch"
      (let [state (atom {:n 0})
            msg-chan (a/chan 8)
            c (ctx (fn [st m]
                     (if (= "j" (:key m)) [(update st :n inc) nil] [st nil]))
                   state :msg-chan msg-chan)]
        (a/>!! msg-chan (msg/key-press "j"))
        (a/>!! msg-chan (msg/key-press "x"))
        (is (true? (drain! c (msg/key-press "x"))))))

    (testing "nothing is processed after a quit"
      (let [state (atom {:seen []})
            msg-chan (a/chan 8)
            c (ctx (fn [st m] [(update st :seen conj (:key m)) nil]) state :msg-chan msg-chan)]
        (a/>!! msg-chan (msg/quit))
        (a/>!! msg-chan (msg/key-press "late"))
        (drain! c (msg/key-press "a"))
        (is (= ["a"] (:seen @state)))))

    (testing "a nil message - the frame tick - is a no-op"
      (is (false? (drain! (ctx (fn [st _] [st nil]) (atom {})) nil))))))

(deftest run-event-loop-test
  (let [run-event-loop! #'p/run-event-loop!]
    (testing "a burst of messages costs one render, not one per message"
      (let [renders (atom 0)
            state (atom {:n 0})
            msg-chan (a/chan 64)
            running? (atom true)
            c (ctx (fn [st _] [(update st :n inc) nil]) state
                   :msg-chan msg-chan :running? running? :fps 60)]
        (dotimes [_ 50] (a/>!! msg-chan (msg/key-press "j")))
        (a/>!! msg-chan (msg/quit))
        (with-redefs [render/render! (fn [_ _] (swap! renders inc))]
          (run-event-loop! c))
        ;; All 50 were handled
        (is (= 50 (:n @state)))
        ;; ...and coalesced into a single frame
        (is (= 1 @renders))))

    (testing "the frame a program quits on is still drawn"
      (let [rendered (atom [])
            state (atom {:label "working"})
            msg-chan (a/chan 8)
            running? (atom true)
            c (ctx (fn [st _] [(assoc st :label "done") nil]) state
                   :msg-chan msg-chan :running? running? :fps 60
                   :view (fn [st] (:label st)))]
        ;; The state change and the quit arrive in the same batch, well inside
        ;; one frame - the old loop rendered per message, this one must not lose it
        (a/>!! msg-chan (msg/key-press "d"))
        (a/>!! msg-chan (msg/quit))
        (with-redefs [render/render! (fn [_ content] (swap! rendered conj content))]
          (run-event-loop! c))
        (is (= ["done"] @rendered))))

    (testing "the loop returns when running? is flipped from outside"
      (let [state (atom {:n 0})
            msg-chan (a/chan 8)
            running? (atom true)
            c (ctx (fn [st _] [st nil]) state
                   :msg-chan msg-chan :running? running? :fps 60)
            done (future (run-event-loop! c) :returned)]
        (Thread/sleep 30)
        (reset! running? false)
        (is (= :returned (deref done 2000 :timed-out)))))

    (testing "a dirty frame is still rendered when the next batch changes nothing"
      (let [renders (atom 0)
            state (atom {:n 0})
            msg-chan (a/chan 8)
            running? (atom true)
            ;; "j" changes the state, "x" does not
            c (ctx (fn [st m]
                     (if (= "j" (:key m)) [(update st :n inc) nil] [st nil]))
                   state :msg-chan msg-chan :running? running? :fps 60)]
        (a/>!! msg-chan (msg/key-press "j"))
        (with-redefs [render/render! (fn [_ _] (swap! renders inc))]
          (let [done (future (run-event-loop! c))]
            ;; A clean batch arrives before the frame is due
            (a/>!! msg-chan (msg/key-press "x"))
            (Thread/sleep 100)
            (reset! running? false)
            (deref done 2000 nil)))
        (is (= 1 @renders))))))

(deftest execute-cmd-test
  (let [execute-cmd! #'p/execute-cmd!
        take! (fn [ch] (first (a/alts!! [ch (a/timeout 5000)])))]
    (testing "a command's message reaches the channel"
      (let [ch (a/chan 4)]
        (execute-cmd! (p/cmd (fn [] (msg/key-press "a"))) ch)
        (is (= "a" (:key (take! ch))))))

    (testing "a command that throws becomes an error message"
      (let [ch (a/chan 4)]
        (execute-cmd! (p/cmd (fn [] (throw (ex-info "boom" {})))) ch)
        (is (msg/error? (take! ch)))))

    (testing "a command returning nil sends nothing"
      (let [ch (a/chan 4)]
        (execute-cmd! (p/cmd (fn [] nil)) ch)
        (execute-cmd! (p/cmd (fn [] (msg/key-press "b"))) ch)
        ;; Only the second command's message is there
        (is (= "b" (:key (take! ch))))))

    (testing "a sequence runs its commands in order"
      (let [ch (a/chan 8)]
        (execute-cmd! (p/sequence-cmds (p/cmd (fn [] (msg/key-press "1")))
                                       (p/cmd (fn [] (msg/key-press "2")))
                                       (p/cmd (fn [] (msg/key-press "3"))))
                      ch)
        (is (= ["1" "2" "3"] (mapv (fn [_] (:key (take! ch))) (range 3))))))

    (testing "the color environment reaches a command body"
      ;; a/thread conveys the binding frame; a plain Thread would not, and a
      ;; command that renders styled text needs it
      (let [ch (a/chan 4)]
        (binding [color/*color-profile* :ascii
                  color/*dark-background?* false]
          (execute-cmd! (p/cmd (fn [] {:type :probe
                                       :profile color/*color-profile*
                                       :dark? color/*dark-background?*}))
                        ch))
        (is (= {:type :probe :profile :ascii :dark? false} (take! ch)))))

    (testing "blocking commands do not queue behind core.async's dispatch pool"
      ;; Eight threads in that pool, so twenty sleeping commands would take
      ;; three times as long if they ran there instead of on a/thread
      (let [ch (a/chan 32)
            n 20
            sleep-ms 100
            start (System/nanoTime)]
        (dotimes [_ n]
          (execute-cmd! (p/cmd (fn [] (Thread/sleep sleep-ms) (msg/key-press "x"))) ch))
        (dotimes [_ n] (take! ch))
        (let [elapsed-ms (quot (- (System/nanoTime) start) 1000000)]
          (is (< elapsed-ms (* sleep-ms 2))
              (str n " commands sleeping " sleep-ms "ms each took " elapsed-ms "ms")))))))

(deftest event->msg-test
  (let [event->msg #'p/event->msg]
    (testing "typed characters become a key press"
      (let [m (event->msg {:type :runes :runes "a"})]
        (is (msg/key-press? m))
        (is (= "a" (:key m)))))

    (testing "a named key carries its type as the key"
      (is (= :page-up (:key (event->msg {:type :page-up})))))

    (testing "modifiers are carried through"
      (let [m (event->msg {:type :runes :runes "c" :ctrl true})]
        (is (true? (:ctrl m)))
        (is (false? (:alt m)))))

    (testing "focus and blur"
      (is (msg/focus? (event->msg {:type :focus})))
      (is (msg/blur? (event->msg {:type :blur}))))

    (testing "a mouse button press"
      (let [m (event->msg {:type :mouse :action :press :button 0 :x 3 :y 4})]
        (is (msg/mouse? m))
        (is (= :press (:action m)))
        (is (= :left (:button m)))
        (is (= [3 4] [(:x m) (:y m)]))))

    (testing "wheel movement replaces the action and reports no button"
      (doseq [[code action] {4 :wheel-up 5 :wheel-down 6 :wheel-left 7 :wheel-right}]
        (let [m (event->msg {:type :mouse :action :press :button code :x 0 :y 0})]
          (is (= action (:action m)))
          (is (= :none (:button m))))))

    (testing "an unknown button is reported as none"
      (is (= :none (:button (event->msg {:type :mouse :action :press :button 9 :x 0 :y 0})))))))

(deftest input-backoff-test
  (let [input-backoff-ms #'p/input-backoff-ms]
    (testing "backs off further on each consecutive failure"
      (is (= [2 4 8 16 32 64 128 256 500]
             (mapv input-backoff-ms (range 1 10)))))

    (testing "and is capped"
      (is (= 500 (input-backoff-ms 100))))

    (testing "it gives up before the backoff would grow unbounded"
      (is (< @#'p/max-input-failures 20)))))

(deftest command-thread-test
  (let [execute-cmd! #'p/execute-cmd!
        ;; babashka's native image has no virtual threads and io-thread falls
        ;; back to ordinary ones there, so the claim below only holds where the
        ;; runtime actually provides them
        virtual? (a/<!! (a/io-thread (.isVirtual (Thread/currentThread))))]
    (when virtual?
      (testing "a burst of commands does not cost one platform thread each"
        ;; Thread/activeCount counts the main thread group, which virtual threads
        ;; are not part of - so it counts exactly what we do not want to grow
        (let [n 200
              ch (a/chan (inc n))
              before (Thread/activeCount)]
          (dotimes [_ n]
            (execute-cmd! (p/cmd (fn [] (Thread/sleep 300) (msg/key-press "x"))) ch))
          ;; Sampled while all of them are still sleeping
          (Thread/sleep 100)
          (let [growth (- (Thread/activeCount) before)]
            (dotimes [_ n] (first (a/alts!! [ch (a/timeout 5000)])))
            (is (< growth (quot n 4))
                (str n " concurrent commands added " growth " platform threads"))))))))
