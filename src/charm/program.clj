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
   [clojure.core.async :as a :refer [chan close!]])
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
   :sanitize true
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

(defn- run-cmd-fn!
  "Call one command function and send what it returns to the channel."
  [f msg-chan]
  (try
    (when-let [result (f)]
      (a/>!! msg-chan result))
    (catch Exception e
      (a/>!! msg-chan (msg/error e)))))

(defn- execute-cmd!
  "Execute a command and send the resulting message to the channel.

   Command bodies run on `a/thread`, not in a `go` block. A command is arbitrary
   user code and is usually the place where a program does its blocking work -
   sleeping, reading a file, calling an HTTP API - which is exactly what a `go`
   block must not do. On the eight-thread dispatch pool a handful of concurrent
   commands would starve it and freeze the UI. `a/thread` also conveys the
   thread's binding frame, so the color environment still reaches a command that
   renders styled text."
  [cmd msg-chan]
  (when cmd
    (case (:type cmd)
      :cmd
      (a/thread (run-cmd-fn! (:fn cmd) msg-chan))

      :batch
      (doseq [c (:cmds cmd)]
        (execute-cmd! c msg-chan))

      :sequence
      (a/thread
        (doseq [c (:cmds cmd)]
          (run-cmd-fn! (:fn c) msg-chan)))

      nil)))

(defn- handle-msg!
  "Run `update` for one message and execute the command it returns.

   Returns true when the frame needs redrawing: either the state changed, or the
   message was a resize, where the frame changed even if the application ignored
   it. Rendering itself belongs to the frame tick, not here.

   A quit message stops the loop, and an error stops it and is rethrown so it
   surfaces on the caller's thread."
  [{:keys [renderer update state msg-chan running?]} m]
  (cond
    (msg/quit? m)
    (do (reset! running? false) false)

    (msg/error? m)
    (do (reset! running? false)
        (throw (:error m)))

    :else
    (let [resize? (msg/window-size? m)]
      (when resize?
        (render/update-size! renderer (:width m) (:height m)))
      (let [old-state @state
            [new-state cmd] (update old-state m)]
        (reset! state new-state)
        (execute-cmd! cmd msg-chan)
        (or resize? (not (identical? new-state old-state)))))))

(defn- drain!
  "Handle `m` and every message already queued behind it.

   Returns true if any of them dirtied the frame. Stops early once the loop is
   no longer running, so nothing is processed after a quit."
  [{:keys [msg-chan running?] :as ctx} m]
  (loop [m m
         dirty? false]
    (if (nil? m)
      dirty?
      (let [dirty? (or (handle-msg! ctx m) dirty?)]
        (if @running?
          (recur (a/poll! msg-chan) dirty?)
          dirty?)))))

(defn- run-event-loop!
  "Block for messages, drain everything queued, and render at most once a frame.

   Waiting rather than sleeping is what lifts the input ceiling: every message
   used to wait up to 10 ms and only one was handled per tick, so a
   300-character paste took three seconds. Rendering on the frame tick rather
   than per message is what makes `:fps` mean anything - a burst of messages now
   costs one `view` and one diff instead of one each."
  [{:keys [renderer view state msg-chan running? fps] :as ctx}]
  (let [frame-ns (quot 1000000000 (long (max 1 fps)))]
    (loop [dirty? false
           last-render (System/nanoTime)]
      (if-not @running?
        ;; Draw the frame still owed on the way out: a program that quits on the
        ;; same key that changed its state would otherwise never show its last
        ;; view, which for an inline program is the one left on screen.
        (when dirty?
          (render/render! renderer (view @state)))
        (let [now (System/nanoTime)]
          (if (and dirty? (>= (- now last-render) frame-ns))
            (do (render/render! renderer (view @state))
                (recur false now))
            ;; Wait for work, but never past the frame deadline, so a pending
            ;; render and an externally flipped `running?` are both noticed.
            (let [wait-ns (if dirty? (- frame-ns (- now last-render)) frame-ns)
                  wait-ms (max 1 (quot wait-ns 1000000))
                  [m _] (a/alts!! [msg-chan (a/timeout wait-ms)])]
              ;; `or`, not `=`: a frame already owed must not be forgotten
              ;; because the next batch of messages changed nothing.
              (recur (or (drain! ctx m) dirty?) last-render))))))))

(def ^:private wheel-buttons
  "Mouse button codes the terminal reports for wheel movement."
  {4 :wheel-up 5 :wheel-down 6 :wheel-left 7 :wheel-right})

(def ^:private mouse-buttons
  {0 :left 1 :middle 2 :right})

(defn- event->msg
  "Convert one input event into a message."
  [event]
  (case (:type event)
    :mouse
    (let [button (int (:button event))
          wheel (get wheel-buttons button)]
      (msg/mouse (or wheel (:action event))
                 (if wheel :none (get mouse-buttons button :none))
                 (:x event) (:y event)
                 :ctrl (boolean (:ctrl event))
                 :alt (boolean (:alt event))
                 :shift (boolean (:shift event))))

    :focus (msg/focus)
    :blur (msg/blur)

    ;; A :runes event carries the characters typed; everything else is named by
    ;; its type, which is the key name.
    ;; `boolean`, because an event map simply omits the modifiers it does not
    ;; carry, and key-press documents them as false rather than nil
    (msg/key-press (if (= :runes (:type event))
                     (:runes event)
                     (:type event))
                   :ctrl (boolean (:ctrl event))
                   :alt (boolean (:alt event))
                   :shift (boolean (:shift event)))))

(def ^:private max-input-failures
  "Consecutive read failures tolerated before giving up on the terminal.

   A reader that fails forever would otherwise spin this thread at 100% of a
   core, since every exception was caught and discarded with no backoff."
  10)

(defn- input-backoff-ms
  "Milliseconds to wait after `failures` consecutive read failures."
  ^long [failures]
  (min 500 (bit-shift-left 1 (min 9 (long failures)))))

(defn- start-input-loop!
  "Start reading terminal input and sending to message channel.
   Returns the thread so it can be interrupted on shutdown."
  [^Terminal terminal msg-chan running?]
  (let [;; Create terminal-aware keymap for escape sequence lookup
        keymap (km/create-keymap terminal)
        thread
        (Thread.
         (fn []
           (loop [failures 0]
             (when @running?
               (let [failure
                     (try
                       (when-let [event (input/read-event terminal
                                                          :timeout-ms 100
                                                          :keymap keymap)]
                         (a/put! msg-chan (event->msg event)))
                       nil
                       (catch InterruptedException _
                         (reset! running? false)
                         nil)
                       (catch Exception e
                         ;; Expected while shutting down, when the reader is
                         ;; closed under us; only a run of them is a problem.
                         e))]
                 (cond
                   (not @running?) nil

                   failure
                   (let [failures (inc failures)]
                     (if (>= failures max-input-failures)
                       (do
                         (a/put! msg-chan
                                 (msg/error
                                  (ex-info (str "Terminal input failed "
                                                failures " times in a row; giving up")
                                           {:failures failures}
                                           failure)))
                         (reset! running? false))
                       (do
                         (Thread/sleep (input-backoff-ms failures))
                         (recur failures))))

                   :else (recur 0)))))))]
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
     :sanitize      - Drop every escape sequence but SGR styling from the view
                      before it reaches the terminal (default: true). Turn this
                      off only for a view that authors its own control
                      sequences, and then sanitize untrusted parts of it with
                      charm.ansi.sanitize/sanitize.
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
        {:keys [alt-screen mouse focus-reporting fps hide-cursor sanitize]} opts

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
                                         :hide-cursor hide-cursor
                                         :sanitize sanitize)

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
          (run-event-loop! {:renderer renderer
                            :update update
                            :view view
                            :state state
                            :msg-chan msg-chan
                            :running? running?
                            :fps fps})

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
