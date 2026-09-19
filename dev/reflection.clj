(ns reflection
  "Fail the build on compiler warnings - reflective calls and auto-boxing.

   Clojure only honours *warn-on-reflection* while compiling, and neither
   `clojure -M` nor the test runner binds it, so this loads every namespace
   under src/ with the flag on and exits non-zero if anything warned."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn- source-namespaces
  "Namespace symbols for every .clj file under src/, in load order."
  []
  (->> (file-seq (io/file "src"))
       (filter #(str/ends-with? (.getName ^java.io.File %) ".clj"))
       (map #(-> (.getPath ^java.io.File %)
                 (str/replace #"^src/" "")
                 (str/replace #"\.clj$" "")
                 (str/replace "_" "-")
                 (str/replace "/" ".")
                 symbol))
       sort))

(defn -main [& _]
  (let [warnings (java.io.StringWriter.)]
    (binding [*warn-on-reflection* true
              *err* warnings]
      (doseq [n (source-namespaces)]
        (require n :reload)))
    (let [out (str warnings)]
      (if (str/blank? out)
        (println "No compiler warnings.")
        (do (print out)
            (flush)
            (System/exit 1))))))
