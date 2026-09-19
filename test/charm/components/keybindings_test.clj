(ns charm.components.keybindings-test
  "Guards that every default key binding is reachable.

   A binding string that names no key a terminal can actually produce -
   \"pgup\" when the event carries :page-up - silently does nothing, and
   nothing else in the suite notices."
  (:require
   [clojure.test :refer [deftest is testing]]
   [charm.components.list :as lst]
   [charm.components.paginator :as paginator]
   [charm.components.table :as table]
   [charm.components.text-input :as text-input]
   [charm.components.viewport :as viewport]
   [charm.input.keys :as keys]
   [charm.message :as msg]))

(def ^:private modifier-combos
  (for [ctrl [false true]
        alt [false true]
        shift [false true]]
    {:ctrl ctrl :alt alt :shift shift}))

(def ^:private key-names
  "Every key a real event can carry: the special keys, plus printable runes."
  (concat (remove #{:runes :unknown} keys/key-types)
          (map #(str (char %)) (range 33 127))))

(def ^:private reachable-messages
  "One key-press message per key and modifier combination."
  (for [key key-names
        mods modifier-combos]
    (msg/key-press key :ctrl (:ctrl mods) :alt (:alt mods) :shift (:shift mods))))

(defn- reachable?
  "Is there a real key event that this binding string matches?"
  [binding]
  (boolean (some #(msg/key-match? % binding) reachable-messages)))

(def ^:private component-defaults
  {"list" @#'lst/default-keys
   "table" @#'table/default-keys
   "viewport" @#'viewport/default-keys
   "paginator" @#'paginator/default-keys
   "text-input" @#'text-input/default-keys})

(deftest default-bindings-are-reachable-test
  (doseq [[component defaults] component-defaults
          [action bindings] defaults
          binding bindings]
    (testing (str component " " action " " (pr-str binding))
      (is (reachable? binding)))))

(deftest reachable?-catches-unknown-keys-test
  (testing "a key name no event carries is not reachable"
    (is (not (reachable? "pgupp")))
    (is (not (reachable? "ctrl+nosuchkey"))))

  (testing "the names the components use are reachable"
    (is (reachable? "page-up"))
    (is (reachable? "ctrl+u"))
    (is (reachable? "alt+backspace"))))
