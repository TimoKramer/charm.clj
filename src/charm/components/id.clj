(ns ^:no-doc charm.components.id
  "Identifiers for component instances.

   Components that schedule their own work - the spinner and the timer - route
   tick messages back to themselves by comparing ids, so two components sharing
   one would drive each other. A counter cannot collide; `(rand-int 1000000)`
   could, with roughly a one-in-a-hundred chance once an application holds about
   150 components.")

(defonce ^:private counter (atom 0))

(defn next-id
  "The next component id."
  []
  (swap! counter inc))
