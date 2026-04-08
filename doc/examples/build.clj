(ns build
  "Build script for timer CLI app.

   Usage (from doc/examples directory):
     clj -T:build clean
     clj -T:build uber
     clj -T:build all    ; clean + uber"
  (:require [clojure.tools.build.api :as b]))

(def my-examples
  ["timer"])
   ;;"todos" "download" "countdown" "file-browser" "form" "spinner-demo" "counter"

(defn class-dir [ex]
  (str "target/" ex "/classes"))

(defn uber-file [ex]
  (str "target/" ex ".jar"))

(defn basis [ex]
  (delay (b/create-basis {:project "deps.edn"
                          :aliases [(keyword ex)]})))

(defn clean
  "Remove build artifacts."
  [ex]
  (println "Cleaning target directory:" ex)
  (b/delete {:path (str "target/" ex)})
  (println "Done."))

(defn uber
  "Build uberjar with AOT compilation."
  [ex]
  (println "Building uberjar:" ex)

  (b/delete {:path (class-dir ex)})

  (b/copy-dir {:src-dirs ["src"]
               :target-dir (class-dir ex)})

  (b/copy-dir {:src-dirs ["resources"]
               :target-dir (class-dir ex)})

  (println "Compiling:" ex)
  (b/compile-clj {:basis (deref (basis ex))
                  :ns-compile '[examples.timer]
                  :class-dir (class-dir ex)})

  (println "Creating uberjar:" ex)
  (b/uber {:class-dir (class-dir ex)
           :uber-file (uber-file ex)
           :basis (deref (basis ex))
           :main (symbol (str "examples." ex))})

  (println (str "Built: " (uber-file ex))))

(defn all
  "Clean and build uberjar."
  [_]
  (doseq [ex my-examples]
    (clean ex)
    (uber ex)))
