(ns charm.program
  "The Elm Architecture event loop for TUI applications.

   A program consists of:
   - init: Initial state and optional startup command
   - update: (state, msg) -> [new-state, cmd]
   - view: state -> string

   Commands are functions that produce messages asynchronously."
  (:require
   [charm.input.handler :as input]
   [charm.input.keymap :as km]
   [charm.message :as msg]
   [charm.render.core :as render]
   [charm.style.color :as color]
   [charm.terminal :as term]
   [clojure.core.async :as a :refer [>! chan close! go]])
  (:import
   [org.jline.terminal Terminal Attributes]))

;; ---------------------------------------------------------------------------
;; Command Helpers
;; ---------------------------------------------------------------------------

(defn cmd
  "Create a command from a function that returns a message.
   The function will be called asynchronously."
  [f]
  {:type :cmd :fn f})

(defn batch
  "Combine multiple commands into one."
  [& cmds]
  {:type :batch :cmds (remove nil? cmds)})

(defn sequence-cmds
  "Run commands in sequence (each waits for previous to complete)."
  [& cmds]
  {:type :sequence :cmds (remove nil? cmds)})

(def quit-cmd
  "Command that sends a quit message."
  (cmd #(msg/quit)))

;; ---------------------------------------------------------------------------
;; Built-in Messages
;; ---------------------------------------------------------------------------

(defn window-size-msg
  "Create a window size message."
  [width height]
  (msg/window-size width height))

;; ---------------------------------------------------------------------------
;; Program Options
;; ---------------------------------------------------------------------------

(defn- default-opts
  "Default program options."
  []
  {:alt-screen false
   :mouse nil  ; nil, :normal, :cell, or :all
   :focus-reporting false
   :fps 60
   :hide-cursor true
   :color-profile nil    ; nil - detect from the environment
   :dark-background? nil})  ; nil - query the terminal

(defn- resolve-color-profile
  "Use a caller-supplied color profile, or detect one from the environment.
   Throws an ex-info naming the value for an unknown profile."
  [profile]
  (cond
    (nil? profile) (color/detect-color-profile)
    (contains? color/color-profiles profile) profile
    :else (throw (ex-info (str "Unknown color profile: " (pr-str profile))
                          {:color-profile profile
                           :valid color/color-profiles}))))

;; ---------------------------------------------------------------------------
;; Event Loop
;; ---------------------------------------------------------------------------

(defn- execute-cmd!
  "Execute a command and send the resulting message to the channel."
  [cmd msg-chan]
  (when cmd
    (case (:type cmd)
      :cmd
      (go
        (try
          (when-let [result ((:fn cmd))]
            (>! msg-chan result))
          (catch Exception e
            (>! msg-chan (msg/error e)))))

      :batch
      (doseq [c (:cmds cmd)]
        (execute-cmd! c msg-chan))

      :sequence
      (go
        (doseq [c (:cmds cmd)]
          (try
            (when-let [result ((:fn c))]
              (>! msg-chan result))
            (catch Exception e
              (>! msg-chan (msg/error e))))))

      nil)))

(defn- handle-msg!
  "Run `update` for one message, execute its command and render the new view.

   Skips the render when `update` returned the identical state, since the
   frame cannot have changed."
  [renderer update view state msg-chan m & {:keys [force-render?]}]
  (let [old-state @state
        [new-state cmd] (update old-state m)]
    (reset! state new-state)
    (execute-cmd! cmd msg-chan)
    (when (or force-render? (not (identical? new-state old-state)))
      (render/render! renderer (view new-state)))))

(defn- start-input-loop!
  "Start reading terminal input and sending to message channel.
   Returns the thread so it can be interrupted on shutdown."
  [^Terminal terminal msg-chan running?]
  (let [;; Create terminal-aware keymap for escape sequence lookup
        keymap (km/create-keymap terminal)
        thread (Thread.
                (fn []
                  (while @running?
                    (try
                      (when-let [event (input/read-event terminal
                                                         :timeout-ms 100
                                                         :keymap keymap)]
                        ;; Convert input event to message
                        (let [m (cond
                                  (= :mouse (:type event))
                                  (let [raw-button (:button event)
                                        wheel (case (int raw-button)
                                                4 :wheel-up   5 :wheel-down
                                                6 :wheel-left 7 :wheel-right
                                                nil)]
                                    (msg/mouse (if wheel wheel (:action event))
                                               (if wheel
                                                 :none
                                                 (case (int raw-button)
                                                   0 :left 1 :middle 2 :right
                                                   :none))
                                               (:x event) (:y event)
                                               :ctrl (:ctrl event)
                                               :alt (:alt event)
                                               :shift (:shift event)))

                                  (= :focus (:type event))
                                  (msg/focus)

                                  (= :blur (:type event))
                                  (msg/blur)

                                  :else
                                  ;; For :runes type, use the runes as key; otherwise use type
                                  (let [key (if (= :runes (:type event))
                                              (:runes event)
                                              (:type event))]
                                    (msg/key-press key
                                                   :ctrl (:ctrl event)
                                                   :alt (:alt event)
                                                   :shift (:shift event))))]
                          (when m
                            (a/put! msg-chan m))))
                      (catch InterruptedException _
                        (reset! running? false))
                      (catch Exception _
                        ;; Ignore read errors during shutdown
                        nil)))))]
    (.setDaemon thread true)
    (.start thread)
    thread))

(defn- restore-terminal!
  "Put the terminal back the way it was found - `render/stop!` covers the mouse,
   focus reporting, the cursor and the alternate screen; the attributes are ours
   to restore.

   Runs from `run`'s finally and from the shutdown hook, so it has to tolerate
   being called twice and after the terminal is already gone."
  [renderer ^Terminal terminal ^Attributes original-attrs]
  (try
    (render/stop! renderer)
    (term/set-attributes terminal original-attrs)
    (catch Exception _
      ;; Nothing useful to do while unwinding, and a terminal that is already
      ;; closed throws on every one of these in JLine 4.
      nil)))

(defn- add-shutdown-hook!
  "Register `f` to run on JVM shutdown, returning the thread so it can be
   removed again.

   `finally` does not cover System/exit or SIGTERM, and an example in this
   repository calls System/exit from inside a running program - without this the
   terminal is left in raw mode, cursor hidden, alternate screen active."
  ^Thread [f]
  (let [thread (Thread. ^Runnable f "charm-shutdown")]
    (.addShutdownHook (Runtime/getRuntime) thread)
    thread))

(defn- remove-shutdown-hook!
  [^Thread thread]
  (try
    (.removeShutdownHook (Runtime/getRuntime) thread)
    (catch IllegalStateException _
      ;; Already shutting down - the hook is running or has run.
      nil)))

(defn- check-window-size!
  "Check terminal size and send resize message if changed."
  [^Terminal terminal msg-chan last-size]
  (let [{:keys [width height]} (term/get-size terminal)]
    (when (or (not= width (:width @last-size))
              (not= height (:height @last-size)))
      (reset! last-size {:width width :height height})
      (a/put! msg-chan (window-size-msg width height)))))

;; ---------------------------------------------------------------------------
;; Main Program
;; ---------------------------------------------------------------------------

(defn run
  "Run a TUI program.

   Options:
     :init          - (fn [] [initial-state cmd]) or initial state value
     :update        - (fn [state msg] [new-state cmd])
     :view          - (fn [state] string)
     :alt-screen    - Use alternate screen buffer (default: false)
     :mouse         - Mouse mode: nil, :normal, :cell, or :all (default: nil)
     :focus-reporting - Report focus in/out (default: false)
     :fps           - Frames per second (default: 60)
     :hide-cursor   - Hide cursor (default: true)
     :running?      - Atom to control the event loop externally (default: internal atom)
     :color-profile - :ascii, :ansi, :ansi256 or :true-color, overriding
                      detection (default: nil, detect from $TERM/$COLORTERM)
     :dark-background? - Override the terminal background query (default: nil,
                      query the terminal)

   The init function should return [initial-state cmd] or just initial-state.
   The update function receives (state msg) and returns [new-state cmd].
   Commands are optional and can be nil.

   At startup an :environment message with the :color-profile and
   :dark-background? in effect is sent, and both are bound for the duration of
   the program so colors resolve against the actual terminal. Pinning either
   option skips its detection; pinning :dark-background? also skips the
   terminal background query, and with it the probe timeout that terminals
   which never answer would otherwise cost at every startup."
  [{:keys [init update view running?] :as opts}]
  (let [opts (merge (default-opts) opts)
        {:keys [alt-screen mouse focus-reporting fps hide-cursor]} opts

        ;; Create terminal and save original attributes for restoration
        terminal (term/create-terminal)
        ^Attributes original-attrs (term/enter-raw-mode terminal)

        ;; Detect the environment for adaptive styling, unless the caller
        ;; pinned it. The background query reads the terminal's OSC response,
        ;; so it must happen before the input loop starts consuming input.
        color-profile (resolve-color-profile (:color-profile opts))
        dark-background? (if (some? (:dark-background? opts))
                           (:dark-background? opts)
                           (term/dark-background? terminal))

        ;; Create renderer
        renderer (render/create-renderer terminal
                                         :fps fps
                                         :alt-screen alt-screen
                                         :hide-cursor hide-cursor)

        ;; Message channel
        msg-chan (chan 256)

        ;; State - use externally provided atom if given
        running? (or running? (atom true))
        last-size (atom {:width 0 :height 0})

        ;; Initialize state
        init-result (if (fn? init) (init) [init nil])
        [initial-state init-cmd] (if (vector? init-result)
                                   init-result
                                   [init-result nil])
        state (atom initial-state)

        ;; Signal handlers we displace, so the finally can restore them
        previous-winch (atom nil)
        previous-int (atom nil)

        shutdown-hook (add-shutdown-hook!
                       #(restore-terminal! renderer terminal original-attrs))]

    (binding [color/*color-profile* color-profile
              color/*dark-background?* dark-background?]
      (try
        ;; Setup renderer
        (render/start! renderer)

        ;; Setup mouse
        (when mouse
          (render/enable-mouse! renderer mouse))

        ;; Setup focus reporting
        (when focus-reporting
          (render/enable-focus-reporting! renderer))

        ;; Handle window resize and interrupt, keeping the handlers they
        ;; replaced so the finally can put them back
        (reset! previous-winch
                (term/handle-signal :winch
                                    #(check-window-size! terminal msg-chan last-size)))
        (reset! previous-int
                (term/handle-signal :int
                                    #(a/put! msg-chan (msg/key-press "c" :ctrl true))))

        ;; Check initial window size
        (check-window-size! terminal msg-chan last-size)

        ;; Tell the app about the terminal environment
        (a/put! msg-chan (msg/environment color-profile dark-background?))

        ;; Start input loop (returns thread)
        (let [^Thread input-thread (start-input-loop! terminal msg-chan running?)]

          ;; Execute init command
          (execute-cmd! init-cmd msg-chan)

          ;; Render initial view
          (render/render! renderer (view @state))

          ;; Main event loop
          (loop []
            (when @running?
              (when-let [_ (a/<!! (a/timeout 10))]
                ;; Timeout - just continue
                nil)

              (when-let [m (a/poll! msg-chan)]
                (cond
                  ;; Quit message
                  (msg/quit? m)
                  (reset! running? false)

                  ;; Error message
                  (= :error (:type m))
                  (do
                    (reset! running? false)
                    (throw (:error m)))

                  ;; Window size
                  (= :window-size (:type m))
                  (do
                    (render/update-size! renderer (:width m) (:height m))
                    ;; The size changed, so the frame has to be redrawn even
                    ;; if the app ignored the message.
                    (handle-msg! renderer update view state msg-chan m :force-render? true))

                  ;; Regular message
                  :else
                  (handle-msg! renderer update view state msg-chan m)))

              (when @running?
                (recur))))

          ;; Interrupt input thread
          (.interrupt input-thread))

        ;; Return final state
        @state

        (finally
          ;; Cleanup
          (reset! running? false)
          (close! msg-chan)

          ;; Put back the signal handlers we displaced, before the terminal
          ;; closes under them
          (term/restore-signal! :winch @previous-winch)
          (term/restore-signal! :int @previous-int)

          ;; Restore the terminal, then stop guarding it
          (restore-terminal! renderer terminal original-attrs)
          (remove-shutdown-hook! shutdown-hook)

          ;; Close terminal
          (term/close terminal))))))

;; ---------------------------------------------------------------------------
;; Async Run
;; ---------------------------------------------------------------------------

(defn run-async
  "Run a TUI program in the background. Returns a handle:
     :quit!  - (fn [] ...) stop the program
     :result - promise, deref to get the final state

   Accepts the same options as `run`."
  [opts]
  (let [running? (atom true)
        result (promise)
        thread (doto (Thread.
                      (fn []
                        (deliver result (run (assoc opts :running? running?)))))
                 (.setDaemon true)
                 (.start))]
    {:quit! (fn [] (reset! running? false))
     :result result}))
