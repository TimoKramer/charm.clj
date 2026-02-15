(ns build
  (:require
   [clojure.tools.build.api :as b]
   [clojure.string :as str]
   [deps-deploy.deps-deploy :as dd]))

(def lib 'de.timokramer/charm.clj)
(def version "0.1.")
(def class-dir "target/classes")

(defn- version-str []
  (str version (str/trim (b/git-count-revs {:dir "."}))))

(defn- tag-str []
  (str "v" (version-str)))

(def jar-file (format "target/%s-%s.jar" (name lib) (version-str)))
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (clean nil)
  (let [basis @basis
        src-dirs (:paths basis)]
    (b/write-pom {:class-dir class-dir
                  :lib lib
                  :version (version-str)
                  :basis basis
                  :src-dirs src-dirs
                  :scm {:url "https://github.com/TimoKramer/charm.clj"
                        :connection "scm:git:git://github.com/TimoKramer/charm.clj.git"
                        :developerConnection "scm:git:ssh://git@github.com/TimoKramer/charm.clj.git"
                        :tag (tag-str)}
                  :pom-data [[:description "Terminal UI library for Clojure"]
                             [:url "https://github.com/TimoKramer/charm.clj"]
                             [:licenses
                              [:license
                               [:name "MIT License"]
                               [:url "https://opensource.org/licenses/MIT"]]]]})
    (b/copy-dir {:src-dirs src-dirs
                 :target-dir class-dir})
    (b/jar {:class-dir class-dir
            :jar-file jar-file}))
  (println (str "Built " jar-file)))

(defn deploy [_]
  (dd/deploy {:installer :remote
              :artifact (b/resolve-path jar-file)
              :pom-file (b/pom-path {:lib lib :class-dir class-dir})}))

(defn tag-release [_]
  (let [tag (tag-str)
        git-name (or (System/getenv "GIT_COMMITTER_NAME") "github-actions[bot]")
        git-email (or (System/getenv "GIT_COMMITTER_EMAIL") "41898282+github-actions[bot]@users.noreply.github.com")]
    (b/git-process {:git-args ["-c" (str "user.name=" git-name)
                               "-c" (str "user.email=" git-email)
                               "tag" "-a" tag "-m" (str "Release " tag)]})
    (b/git-process {:git-args ["push" "--tags"]})))
