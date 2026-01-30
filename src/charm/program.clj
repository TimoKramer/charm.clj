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
   [charm.terminal :as term]
   [clojure.core.async :as a :refer [>! chan close! go]])
  (:import
   [org.jline.terminal Terminal Attributes]
   [org.jline.utils Signals]))

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
   :hide-cursor true})

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
                                  (msg/mouse (:button event) (:x event) (:y event)
                                             :action (:action event)
                                             :ctrl (:ctrl event)
                                             :alt (:alt event)
                                             :shift (:shift event))

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

   The init function should return [initial-state cmd] or just initial-state.
   The update function receives (state msg) and returns [new-state cmd].
   Commands are optional and can be nil."
  [{:keys [init update view] :as opts}]
  (let [opts (merge (default-opts) opts)
        {:keys [alt-screen mouse focus-reporting fps hide-cursor]} opts

        ;; Create terminal and save original attributes for restoration
        terminal (term/create-terminal)
        ^Attributes original-attrs (term/enter-raw-mode terminal)

        ;; Create renderer
        renderer (render/create-renderer terminal
                                         :fps fps
                                         :alt-screen alt-screen
                                         :hide-cursor hide-cursor)

        ;; Message channel
        msg-chan (chan 256)

        ;; State
        running? (atom true)
        last-size (atom {:width 0 :height 0})

        ;; Initialize state
        init-result (if (fn? init) (init) [init nil])
        [initial-state init-cmd] (if (vector? init-result)
                                   init-result
                                   [init-result nil])
        state (atom initial-state)]

    (try
      ;; Setup renderer
      (render/start! renderer)
      (when alt-screen
        (render/enter-alt-screen! renderer))

      ;; Setup mouse
      (when mouse
        (render/enable-mouse! renderer mouse))

      ;; Setup focus reporting
      (when focus-reporting
        (render/enable-focus-reporting! renderer))

      ;; Handle window resize signal
      (Signals/register "WINCH"
                        (reify Runnable
                          (run [_]
                            (check-window-size! terminal msg-chan last-size))))

      ;; Check initial window size
      (check-window-size! terminal msg-chan last-size)

      ;; Start input loop (returns thread)
      (let [^Thread input-thread (start-input-loop! terminal msg-chan running?)]

        ;; Execute init command
        (execute-cmd! init-cmd msg-chan)

        ;; Render initial view
        (render/render! renderer (view @state))

        ;; Drain any initial window-size message to avoid double render
        (a/poll! msg-chan)

        ;; Main event loop
        (loop []
         (when @running?
           (when-let [m (a/<!! (a/timeout 10))]
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
                 (let [[new-state cmd] (update @state m)]
                   (reset! state new-state)
                   (execute-cmd! cmd msg-chan)
                   (render/render! renderer (view new-state))))

               ;; Regular message
               :else
               (let [[new-state cmd] (update @state m)]
                 (reset! state new-state)
                 (execute-cmd! cmd msg-chan)
                 (render/render! renderer (view new-state)))))

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

        ;; Disable mouse
        (render/disable-mouse! renderer)

        ;; Disable focus reporting
        (when focus-reporting
          (render/disable-focus-reporting! renderer))

        ;; Stop renderer
        (render/stop! renderer)

        ;; Restore terminal attributes before closing
        (term/set-attributes terminal original-attrs)

        ;; Close terminal
        (term/close terminal)))))

;; ---------------------------------------------------------------------------
;; Simple Run Helper
;; ---------------------------------------------------------------------------

(defn run-simple
  "Run a simple TUI program with minimal setup.

   Takes a map with:
     :state  - Initial state
     :update - (fn [state msg] [new-state cmd])
     :view   - (fn [state] string)

   Example:
     (run-simple
       {:state {:count 0}
        :update (fn [state msg]
                  (if (key-match? msg \"q\")
                    [state quit-cmd]
                    [(update state :count inc) nil]))
        :view (fn [state]
                (str \"Count: \" (:count state) \"\\nPress q to quit\"))})"
  [{:keys [state update view] :as opts}]
  (run (assoc opts :init state)))
