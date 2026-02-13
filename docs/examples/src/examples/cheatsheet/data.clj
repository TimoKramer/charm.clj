(ns examples.cheatsheet.data
  "Cheatsheet structure and ClojureDocs data loading.

   Data comes from two sources:
   - Cheatsheet structure: section/subsection/group hierarchy
   - ClojureDocs export: docstrings, arglists, examples, see-alsos"
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.walk :as walk]))

;; ---------------------------------------------------------------------------
;; ClojureDocs Data Loading
;; ---------------------------------------------------------------------------

(def clojuredocs-edn-url
  "https://github.com/clojure-emacs/clojuredocs-export-edn/raw/master/exports/export.compact.edn")

(defonce docs-cache (atom nil))

(defn load-docs!
  "Download and cache ClojureDocs data. Returns nil (side-effecting cmd)."
  []
  (when-not @docs-cache
    (let [data (-> clojuredocs-edn-url slurp edn/read-string)]
      (reset! docs-cache data)))
  nil)

(defn lookup
  "Look up documentation for a qualified symbol.
   Returns {:arglists [...] :doc \"...\" :examples [...] :see-alsos [...]} or nil."
  [qualified-sym]
  (when-let [docs @docs-cache]
    ;; Keys in the export are qualified keywords like :clojure.core/map
    (get docs (keyword (namespace qualified-sym) (name qualified-sym)))))

(defn all-symbols
  "Get all documented symbol names from the cache."
  []
  (when-let [docs @docs-cache]
    (keys docs)))

;; ---------------------------------------------------------------------------
;; Cheatsheet Structure
;; ---------------------------------------------------------------------------

;; Structure: vector of sections
;; Each section: {:name "..." :subsections [...]}
;; Each subsection: {:name "..." :groups [...]}
;; Each group: {:label "..." :fns [qualified-symbol ...]}

(def sections
  [{:name "Primitives"
    :subsections
    [{:name "Numbers"
      :groups
      [{:label "Arithmetic"
        :fns '[clojure.core/+ clojure.core/- clojure.core/* clojure.core// clojure.core/quot clojure.core/rem clojure.core/mod clojure.core/inc clojure.core/dec clojure.core/max clojure.core/min clojure.core/abs clojure.core/+' clojure.core/-' clojure.core/*' clojure.core/inc' clojure.core/dec']}
       {:label "Compare"
        :fns '[clojure.core/== clojure.core/< clojure.core/> clojure.core/<= clojure.core/>= clojure.core/compare]}
       {:label "Bitwise"
        :fns '[clojure.core/bit-and clojure.core/bit-or clojure.core/bit-xor clojure.core/bit-not clojure.core/bit-flip clojure.core/bit-set clojure.core/bit-shift-right clojure.core/bit-shift-left clojure.core/bit-and-not clojure.core/bit-clear clojure.core/bit-test clojure.core/unsigned-bit-shift-right]}
       {:label "Cast"
        :fns '[clojure.core/byte clojure.core/short clojure.core/int clojure.core/long clojure.core/float clojure.core/double clojure.core/bigdec clojure.core/bigint clojure.core/num clojure.core/rationalize clojure.core/biginteger]}
       {:label "Test"
        :fns '[clojure.core/zero? clojure.core/pos? clojure.core/neg? clojure.core/even? clojure.core/odd? clojure.core/number? clojure.core/rational? clojure.core/integer? clojure.core/ratio? clojure.core/decimal? clojure.core/float? clojure.core/double? clojure.core/int? clojure.core/nat-int? clojure.core/neg-int? clojure.core/pos-int? clojure.core/NaN? clojure.core/infinite?]}
       {:label "Random"
        :fns '[clojure.core/rand clojure.core/rand-int]}
       {:label "Unchecked"
        :fns '[clojure.core/*unchecked-math* clojure.core/unchecked-add clojure.core/unchecked-dec clojure.core/unchecked-inc clojure.core/unchecked-multiply clojure.core/unchecked-negate clojure.core/unchecked-subtract]}]}
     {:name "Strings"
      :groups
      [{:label "Create"
        :fns '[clojure.core/str clojure.core/format]}
       {:label "Use"
        :fns '[clojure.core/count clojure.core/get clojure.core/subs clojure.core/compare clojure.string/join clojure.string/escape clojure.string/split clojure.string/split-lines clojure.string/replace clojure.string/replace-first clojure.string/reverse clojure.string/index-of clojure.string/last-index-of]}
       {:label "Regex"
        :fns '[clojure.core/re-find clojure.core/re-seq clojure.core/re-matches clojure.core/re-pattern clojure.core/re-matcher clojure.core/re-groups clojure.string/re-quote-replacement]}
       {:label "Letters"
        :fns '[clojure.string/capitalize clojure.string/lower-case clojure.string/upper-case]}
       {:label "Trim"
        :fns '[clojure.string/trim clojure.string/trim-newline clojure.string/triml clojure.string/trimr]}
       {:label "Test"
        :fns '[clojure.core/string? clojure.string/blank? clojure.string/starts-with? clojure.string/ends-with? clojure.string/includes?]}
       {:label "Parse"
        :fns '[clojure.core/parse-boolean clojure.core/parse-double clojure.core/parse-long clojure.core/parse-uuid]}]}
     {:name "Other"
      :groups
      [{:label "Characters"
        :fns '[clojure.core/char clojure.core/char? clojure.core/char-name-string clojure.core/char-escape-string]}
       {:label "Keywords"
        :fns '[clojure.core/keyword clojure.core/keyword? clojure.core/find-keyword]}
       {:label "Symbols"
        :fns '[clojure.core/symbol clojure.core/symbol? clojure.core/gensym]}
       {:label "Misc"
        :fns '[clojure.core/true? clojure.core/false? clojure.core/nil? clojure.core/some? clojure.core/boolean?]}]}]}

   {:name "Collections"
    :subsections
    [{:name "Generic Ops"
      :groups
      [{:label "Manipulate"
        :fns '[clojure.core/count clojure.core/empty clojure.core/not-empty clojure.core/into clojure.core/conj clojure.core/bounded-count]}
       {:label "Content tests"
        :fns '[clojure.core/distinct? clojure.core/empty? clojure.core/every? clojure.core/not-every? clojure.core/some clojure.core/not-any?]}
       {:label "Capabilities"
        :fns '[clojure.core/sequential? clojure.core/associative? clojure.core/sorted? clojure.core/counted? clojure.core/reversible? clojure.core/seqable? clojure.core/indexed?]}
       {:label "Type tests"
        :fns '[clojure.core/coll? clojure.core/list? clojure.core/vector? clojure.core/set? clojure.core/map? clojure.core/seq? clojure.core/record? clojure.core/map-entry?]}
       {:label "Walk"
        :fns '[clojure.walk/walk clojure.walk/prewalk clojure.walk/prewalk-demo clojure.walk/prewalk-replace clojure.walk/postwalk clojure.walk/postwalk-demo clojure.walk/postwalk-replace]}
       {:label "Compare"
        :fns '[clojure.core/= clojure.core/identical? clojure.core/not= clojure.core/not clojure.core/compare clojure.data/diff]}]}
     {:name "Lists"
      :groups
      [{:label "Create"
        :fns '[clojure.core/list clojure.core/list*]}
       {:label "Examine"
        :fns '[clojure.core/first clojure.core/nth clojure.core/peek]}
       {:label "Change"
        :fns '[clojure.core/cons clojure.core/conj clojure.core/rest clojure.core/pop]}]}
     {:name "Vectors"
      :groups
      [{:label "Create"
        :fns '[clojure.core/vector clojure.core/vec clojure.core/vector-of clojure.core/mapv clojure.core/filterv]}
       {:label "Examine"
        :fns '[clojure.core/nth clojure.core/get clojure.core/peek]}
       {:label "Change"
        :fns '[clojure.core/assoc clojure.core/assoc-in clojure.core/pop clojure.core/subvec clojure.core/replace clojure.core/conj clojure.core/rseq clojure.core/update clojure.core/update-in]}
       {:label "Ops"
        :fns '[clojure.core/reduce-kv]}]}
     {:name "Sets"
      :groups
      [{:label "Create"
        :fns '[clojure.core/set clojure.core/hash-set clojure.core/sorted-set clojure.core/sorted-set-by]}
       {:label "Examine"
        :fns '[clojure.core/get clojure.core/contains?]}
       {:label "Change"
        :fns '[clojure.core/conj clojure.core/disj]}
       {:label "Ops"
        :fns '[clojure.set/union clojure.set/difference clojure.set/intersection clojure.set/select]}
       {:label "Test"
        :fns '[clojure.set/subset? clojure.set/superset?]}
       {:label "Sorted"
        :fns '[clojure.core/rseq clojure.core/subseq clojure.core/rsubseq]}]}
     {:name "Maps"
      :groups
      [{:label "Create"
        :fns '[clojure.core/hash-map clojure.core/array-map clojure.core/zipmap clojure.core/sorted-map clojure.core/sorted-map-by clojure.core/frequencies clojure.core/group-by]}
       {:label "Examine"
        :fns '[clojure.core/get clojure.core/get-in clojure.core/contains? clojure.core/find clojure.core/keys clojure.core/vals]}
       {:label "Change"
        :fns '[clojure.core/assoc clojure.core/assoc-in clojure.core/dissoc clojure.core/merge clojure.core/merge-with clojure.core/select-keys clojure.core/update clojure.core/update-in clojure.core/update-keys clojure.core/update-vals clojure.set/rename-keys clojure.set/map-invert]}
       {:label "Ops"
        :fns '[clojure.core/reduce-kv]}
       {:label "Entry"
        :fns '[clojure.core/key clojure.core/val]}
       {:label "Sorted"
        :fns '[clojure.core/rseq clojure.core/subseq clojure.core/rsubseq]}]}
     {:name "Relations"
      :groups
      [{:label "Rel algebra"
        :fns '[clojure.set/join clojure.set/select clojure.set/project clojure.set/union clojure.set/difference clojure.set/intersection clojure.set/index clojure.set/rename]}]}
     {:name "Transients"
      :groups
      [{:label "Create"
        :fns '[clojure.core/transient clojure.core/persistent!]}
       {:label "Change"
        :fns '[clojure.core/conj! clojure.core/pop! clojure.core/assoc! clojure.core/dissoc! clojure.core/disj!]}]}]}

   {:name "Sequences"
    :subsections
    [{:name "Create"
      :groups
      [{:label "From coll"
        :fns '[clojure.core/seq clojure.core/vals clojure.core/keys clojure.core/rseq clojure.core/subseq clojure.core/rsubseq clojure.core/sequence]}
       {:label "Producer fn"
        :fns '[clojure.core/lazy-seq clojure.core/repeatedly clojure.core/iterate clojure.core/iteration]}
       {:label "Constant"
        :fns '[clojure.core/repeat clojure.core/range]}
       {:label "From other"
        :fns '[clojure.core/file-seq clojure.core/line-seq clojure.core/re-seq clojure.core/tree-seq clojure.core/xml-seq clojure.core/iterator-seq clojure.core/enumeration-seq]}
       {:label "From seq"
        :fns '[clojure.core/keep clojure.core/keep-indexed]}]}
     {:name "Seq in, Seq out"
      :groups
      [{:label "Get shorter"
        :fns '[clojure.core/distinct clojure.core/filter clojure.core/remove clojure.core/take-nth clojure.core/for clojure.core/dedupe clojure.core/random-sample]}
       {:label "Get longer"
        :fns '[clojure.core/cons clojure.core/conj clojure.core/concat clojure.core/lazy-cat clojure.core/mapcat clojure.core/cycle clojure.core/interleave clojure.core/interpose]}
       {:label "Tail-items"
        :fns '[clojure.core/rest clojure.core/nthrest clojure.core/next clojure.core/fnext clojure.core/nnext clojure.core/drop clojure.core/drop-while clojure.core/take-last]}
       {:label "Head-items"
        :fns '[clojure.core/take clojure.core/take-while clojure.core/butlast clojure.core/drop-last]}
       {:label "Change"
        :fns '[clojure.core/flatten clojure.core/group-by clojure.core/partition clojure.core/partition-all clojure.core/partition-by clojure.core/split-at clojure.core/split-with]}
       {:label "Rearrange"
        :fns '[clojure.core/reverse clojure.core/sort clojure.core/sort-by clojure.core/compare clojure.core/shuffle]}
       {:label "Process"
        :fns '[clojure.core/map clojure.core/pmap clojure.core/map-indexed clojure.core/mapcat clojure.core/for clojure.core/replace clojure.core/seque]}]}
     {:name "Using a Seq"
      :groups
      [{:label "Extract"
        :fns '[clojure.core/first clojure.core/second clojure.core/last clojure.core/rest clojure.core/next clojure.core/ffirst clojure.core/nfirst clojure.core/fnext clojure.core/nnext clojure.core/nth clojure.core/nthnext clojure.core/rand-nth clojure.core/when-first clojure.core/max-key clojure.core/min-key]}
       {:label "Construct"
        :fns '[clojure.core/zipmap clojure.core/into clojure.core/reduce clojure.core/reductions clojure.core/set clojure.core/vec clojure.core/into-array clojure.core/to-array-2d clojure.core/mapv clojure.core/filterv]}
       {:label "Pass to fn"
        :fns '[clojure.core/apply]}
       {:label "Search"
        :fns '[clojure.core/some clojure.core/filter]}
       {:label "Force"
        :fns '[clojure.core/doseq clojure.core/dorun clojure.core/doall clojure.core/run!]}
       {:label "Check"
        :fns '[clojure.core/realized?]}]}]}

   {:name "Functions"
    :subsections
    [{:name "Create"
      :groups
      [{:label "Define"
        :fns '[clojure.core/fn clojure.core/defn clojure.core/defn- clojure.core/identity clojure.core/constantly clojure.core/memfn]}
       {:label "Compose"
        :fns '[clojure.core/comp clojure.core/complement clojure.core/partial clojure.core/juxt clojure.core/memoize clojure.core/fnil clojure.core/every-pred clojure.core/some-fn]}]}
     {:name "Call"
      :groups
      [{:label "Direct"
        :fns '[clojure.core/apply clojure.core/-> clojure.core/->> clojure.core/trampoline]}
       {:label "Threading"
        :fns '[clojure.core/as-> clojure.core/cond-> clojure.core/cond->> clojure.core/some-> clojure.core/some->>]}]}
     {:name "Test"
      :groups
      [{:label "Predicates"
        :fns '[clojure.core/fn? clojure.core/ifn?]}]}]}

   {:name "Macros"
    :subsections
    [{:name "Create"
      :groups
      [{:label "Define"
        :fns '[clojure.core/defmacro clojure.core/macroexpand-1 clojure.core/macroexpand clojure.walk/macroexpand-all]}]}
     {:name "Branch"
      :groups
      [{:label "If/When"
        :fns '[clojure.core/and clojure.core/or clojure.core/when clojure.core/when-not clojure.core/when-let clojure.core/when-first clojure.core/if-not clojure.core/if-let clojure.core/when-some clojure.core/if-some]}
       {:label "Cond"
        :fns '[clojure.core/cond clojure.core/condp clojure.core/case]}]}
     {:name "Loop"
      :groups
      [{:label "Iteration"
        :fns '[clojure.core/for clojure.core/doseq clojure.core/dotimes clojure.core/while]}]}
     {:name "Arrange"
      :groups
      [{:label "Threading"
        :fns '[clojure.core/.. clojure.core/doto clojure.core/-> clojure.core/->> clojure.core/as-> clojure.core/cond-> clojure.core/cond->> clojure.core/some-> clojure.core/some->>]}]}
     {:name "Scope"
      :groups
      [{:label "Binding"
        :fns '[clojure.core/binding clojure.core/locking clojure.core/time clojure.core/with-in-str clojure.core/with-local-vars clojure.core/with-open clojure.core/with-out-str clojure.core/with-redefs]}]}
     {:name "Lazy"
      :groups
      [{:label "Create"
        :fns '[clojure.core/lazy-cat clojure.core/lazy-seq clojure.core/delay]}]}]}

   {:name "Concurrency"
    :subsections
    [{:name "Atoms"
      :groups
      [{:label "Create"
        :fns '[clojure.core/atom]}
       {:label "Change"
        :fns '[clojure.core/swap! clojure.core/reset! clojure.core/compare-and-set! clojure.core/swap-vals! clojure.core/reset-vals!]}]}
     {:name "Refs"
      :groups
      [{:label "Create"
        :fns '[clojure.core/ref]}
       {:label "Examine"
        :fns '[clojure.core/deref]}
       {:label "Transaction"
        :fns '[clojure.core/dosync clojure.core/io! clojure.core/ensure clojure.core/ref-set clojure.core/alter clojure.core/commute]}]}
     {:name "Agents"
      :groups
      [{:label "Create"
        :fns '[clojure.core/agent]}
       {:label "Examine"
        :fns '[clojure.core/agent-error]}
       {:label "Change"
        :fns '[clojure.core/send clojure.core/send-off clojure.core/restart-agent clojure.core/send-via]}
       {:label "Block"
        :fns '[clojure.core/await clojure.core/await-for]}
       {:label "Error"
        :fns '[clojure.core/error-handler clojure.core/set-error-handler! clojure.core/error-mode clojure.core/set-error-mode!]}]}
     {:name "Futures"
      :groups
      [{:label "Create"
        :fns '[clojure.core/future clojure.core/future-call]}
       {:label "Test"
        :fns '[clojure.core/future-done? clojure.core/future-cancel clojure.core/future-cancelled? clojure.core/future?]}]}
     {:name "Volatiles"
      :groups
      [{:label "Create"
        :fns '[clojure.core/volatile!]}
       {:label "Change"
        :fns '[clojure.core/vreset! clojure.core/vswap!]}
       {:label "Test"
        :fns '[clojure.core/volatile?]}]}
     {:name "Misc"
      :groups
      [{:label "Promise"
        :fns '[clojure.core/promise clojure.core/deliver]}
       {:label "Threads"
        :fns '[clojure.core/locking clojure.core/pcalls clojure.core/pvalues clojure.core/pmap clojure.core/seque]}
       {:label "Watchers"
        :fns '[clojure.core/add-watch clojure.core/remove-watch]}
       {:label "Validators"
        :fns '[clojure.core/set-validator! clojure.core/get-validator]}]}]}

   {:name "IO"
    :subsections
    [{:name "Files"
      :groups
      [{:label "Read/Write"
        :fns '[clojure.core/spit clojure.core/slurp]}
       {:label "Open"
        :fns '[clojure.core/with-open clojure.java.io/reader clojure.java.io/writer clojure.java.io/input-stream clojure.java.io/output-stream]}
       {:label "Misc"
        :fns '[clojure.core/file-seq clojure.core/flush clojure.java.io/file clojure.java.io/copy clojure.java.io/delete-file clojure.java.io/resource clojure.java.io/as-file clojure.java.io/as-url clojure.java.io/as-relative-path]}]}
     {:name "Print"
      :groups
      [{:label "To *out*"
        :fns '[clojure.core/pr clojure.core/prn clojure.core/print clojure.core/printf clojure.core/println clojure.core/newline clojure.pprint/print-table]}
       {:label "To writer"
        :fns '[clojure.pprint/pprint clojure.pprint/cl-format]}
       {:label "To string"
        :fns '[clojure.core/format clojure.core/with-out-str clojure.core/pr-str clojure.core/prn-str clojure.core/print-str clojure.core/println-str]}]}
     {:name "Read"
      :groups
      [{:label "From *in*"
        :fns '[clojure.core/read-line clojure.edn/read]}
       {:label "From reader"
        :fns '[clojure.core/line-seq]}
       {:label "From string"
        :fns '[clojure.core/with-in-str clojure.core/read-string clojure.edn/read-string]}]}
     {:name "Tap"
      :groups
      [{:label "Tap"
        :fns '[clojure.core/tap> clojure.core/add-tap clojure.core/remove-tap]}]}]}

   {:name "Transducers"
    :subsections
    [{:name "Use"
      :groups
      [{:label "Apply"
        :fns '[clojure.core/into clojure.core/sequence clojure.core/transduce clojure.core/eduction]}
       {:label "Terminate"
        :fns '[clojure.core/reduced clojure.core/reduced? clojure.core/deref]}
       {:label "Create"
        :fns '[clojure.core/completing clojure.core/ensure-reduced clojure.core/unreduced]}]}
     {:name "Built-in"
      :groups
      [{:label "Transform"
        :fns '[clojure.core/map clojure.core/mapcat clojure.core/filter clojure.core/remove clojure.core/keep clojure.core/keep-indexed clojure.core/map-indexed clojure.core/replace clojure.core/distinct clojure.core/interpose clojure.core/dedupe clojure.core/cat clojure.core/random-sample]}
       {:label "Partition"
        :fns '[clojure.core/partition-by clojure.core/partition-all]}
       {:label "Take/Drop"
        :fns '[clojure.core/take clojure.core/take-while clojure.core/take-nth clojure.core/drop clojure.core/drop-while clojure.core/halt-when]}]}]}

   {:name "Java"
    :subsections
    [{:name "Interop"
      :groups
      [{:label "General"
        :fns '[clojure.core/.. clojure.core/doto clojure.core/new clojure.core/bean clojure.core/comparator clojure.core/enumeration-seq clojure.core/import clojure.core/iterator-seq clojure.core/memfn clojure.core/set!]}
       {:label "Cast"
        :fns '[clojure.core/boolean clojure.core/byte clojure.core/short clojure.core/char clojure.core/int clojure.core/long clojure.core/float clojure.core/double clojure.core/bigdec clojure.core/bigint clojure.core/num clojure.core/cast clojure.core/biginteger]}
       {:label "Type"
        :fns '[clojure.core/class clojure.core/class? clojure.core/type clojure.core/bases clojure.core/supers clojure.core/instance? clojure.core/gen-class clojure.core/gen-interface clojure.core/definterface]}]}
     {:name "Exceptions"
      :groups
      [{:label "Throw/Catch"
        :fns '[clojure.core/throw clojure.core/try clojure.core/ex-info clojure.core/ex-data clojure.core/ex-message clojure.core/ex-cause clojure.core/Throwable->map clojure.core/StackTraceElement->vec]}]}
     {:name "Arrays"
      :groups
      [{:label "Create"
        :fns '[clojure.core/make-array clojure.core/into-array clojure.core/to-array clojure.core/to-array-2d clojure.core/aclone clojure.core/object-array clojure.core/boolean-array clojure.core/byte-array clojure.core/short-array clojure.core/char-array clojure.core/int-array clojure.core/long-array clojure.core/float-array clojure.core/double-array]}
       {:label "Use"
        :fns '[clojure.core/aget clojure.core/aset clojure.core/alength clojure.core/amap clojure.core/areduce]}
       {:label "Cast"
        :fns '[clojure.core/booleans clojure.core/bytes clojure.core/shorts clojure.core/chars clojure.core/ints clojure.core/longs clojure.core/floats clojure.core/doubles]}]}
     {:name "Proxy"
      :groups
      [{:label "Create"
        :fns '[clojure.core/proxy clojure.core/get-proxy-class clojure.core/construct-proxy clojure.core/init-proxy]}
       {:label "Misc"
        :fns '[clojure.core/proxy-mappings clojure.core/proxy-super clojure.core/update-proxy]}]}]}

   {:name "Abstractions"
    :subsections
    [{:name "Protocols"
      :groups
      [{:label "Define"
        :fns '[clojure.core/defprotocol clojure.core/extend-type clojure.core/extend-protocol clojure.core/reify]}
       {:label "Test"
        :fns '[clojure.core/satisfies? clojure.core/extends?]}
       {:label "Other"
        :fns '[clojure.core/extend clojure.core/extenders]}]}
     {:name "Records"
      :groups
      [{:label "Define"
        :fns '[clojure.core/defrecord]}
       {:label "Test"
        :fns '[clojure.core/record?]}]}
     {:name "Types"
      :groups
      [{:label "Define"
        :fns '[clojure.core/deftype]}]}
     {:name "Multimethods"
      :groups
      [{:label "Define"
        :fns '[clojure.core/defmulti clojure.core/defmethod]}
       {:label "Dispatch"
        :fns '[clojure.core/get-method clojure.core/methods]}
       {:label "Remove"
        :fns '[clojure.core/remove-method clojure.core/remove-all-methods]}
       {:label "Prefer"
        :fns '[clojure.core/prefer-method clojure.core/prefers]}
       {:label "Hierarchy"
        :fns '[clojure.core/derive clojure.core/underive clojure.core/isa? clojure.core/parents clojure.core/ancestors clojure.core/descendants clojure.core/make-hierarchy]}]}]}

   {:name "Namespaces"
    :subsections
    [{:name "Namespaces"
      :groups
      [{:label "Current"
        :fns '[clojure.core/*ns*]}
       {:label "Create"
        :fns '[clojure.core/ns clojure.core/in-ns clojure.core/create-ns]}
       {:label "Add"
        :fns '[clojure.core/alias clojure.core/import clojure.core/intern clojure.core/refer]}
       {:label "Find"
        :fns '[clojure.core/all-ns clojure.core/find-ns]}
       {:label "Examine"
        :fns '[clojure.core/ns-name clojure.core/ns-aliases clojure.core/ns-map clojure.core/ns-interns clojure.core/ns-publics clojure.core/ns-refers clojure.core/ns-imports]}
       {:label "Resolve"
        :fns '[clojure.core/resolve clojure.core/ns-resolve clojure.core/namespace clojure.core/the-ns clojure.core/requiring-resolve]}
       {:label "Remove"
        :fns '[clojure.core/ns-unalias clojure.core/ns-unmap clojure.core/remove-ns]}]}
     {:name "Loading"
      :groups
      [{:label "Load libs"
        :fns '[clojure.core/require clojure.core/use clojure.core/import clojure.core/refer]}
       {:label "Load misc"
        :fns '[clojure.core/loaded-libs clojure.core/load clojure.core/load-file clojure.core/load-reader clojure.core/load-string]}]}]}

   {:name "Metadata"
    :subsections
    [{:name "Metadata"
      :groups
      [{:label "Read/Write"
        :fns '[clojure.core/meta clojure.core/with-meta clojure.core/vary-meta clojure.core/alter-meta! clojure.core/reset-meta!]}]}]}

   {:name "Vars"
    :subsections
    [{:name "Def"
      :groups
      [{:label "Variants"
        :fns '[clojure.core/def clojure.core/defn clojure.core/defn- clojure.core/defmacro clojure.core/defmethod clojure.core/defmulti clojure.core/defonce clojure.core/defrecord]}]}
     {:name "Interned"
      :groups
      [{:label "Interned"
        :fns '[clojure.core/declare clojure.core/intern clojure.core/binding clojure.core/find-var clojure.core/var]}]}
     {:name "Var objects"
      :groups
      [{:label "Objects"
        :fns '[clojure.core/with-local-vars clojure.core/var-get clojure.core/var-set clojure.core/alter-var-root clojure.core/var? clojure.core/bound? clojure.core/thread-bound?]}]}]}

   {:name "Zippers"
    :subsections
    [{:name "Zippers"
      :groups
      [{:label "Create"
        :fns '[clojure.zip/zipper clojure.zip/seq-zip clojure.zip/vector-zip clojure.zip/xml-zip]}
       {:label "Get loc"
        :fns '[clojure.zip/up clojure.zip/down clojure.zip/left clojure.zip/right clojure.zip/leftmost clojure.zip/rightmost]}
       {:label "Get seq"
        :fns '[clojure.zip/lefts clojure.zip/rights clojure.zip/path clojure.zip/children]}
       {:label "Change"
        :fns '[clojure.zip/make-node clojure.zip/replace clojure.zip/edit clojure.zip/insert-child clojure.zip/insert-left clojure.zip/insert-right clojure.zip/append-child clojure.zip/remove]}
       {:label "Move"
        :fns '[clojure.zip/next clojure.zip/prev]}
       {:label "Misc"
        :fns '[clojure.zip/root clojure.zip/node clojure.zip/branch? clojure.zip/end?]}]}]}

   {:name "Documentation"
    :subsections
    [{:name "REPL"
      :groups
      [{:label "Explore"
        :fns '[clojure.repl/doc clojure.repl/find-doc clojure.repl/apropos clojure.repl/dir clojure.repl/source clojure.repl/pst]}]}]}

   {:name "Other"
    :subsections
    [{:name "Misc"
      :groups
      [{:label "Code"
        :fns '[clojure.core/eval clojure.core/force clojure.core/hash clojure.core/name clojure.core/*clojure-version* clojure.core/clojure-version clojure.core/random-uuid]}
       {:label "REPL vars"
        :fns '[clojure.core/*1 clojure.core/*2 clojure.core/*3 clojure.core/*e clojure.core/*print-dup* clojure.core/*print-length* clojure.core/*print-level* clojure.core/*print-meta* clojure.core/*print-readably*]}
       {:label "Shell"
        :fns '[clojure.java.shell/sh clojure.java.shell/with-sh-dir clojure.java.shell/with-sh-env]}
       {:label "XML"
        :fns '[clojure.xml/parse clojure.core/xml-seq]}
       {:label "Datafy"
        :fns '[clojure.datafy/datafy clojure.datafy/nav]}]}]}])

;; ---------------------------------------------------------------------------
;; Filtering & Flattening
;; ---------------------------------------------------------------------------

(defn filter-sections
  "Filter sections by query string, keeping hierarchy structure.
   Returns sections with only groups containing matching functions.
   Empty subsections and sections are removed."
  [sects query]
  (let [query (str/lower-case (str/trim (or query "")))]
    (if (str/blank? query)
      sects
      (let [match? #(str/includes? (str/lower-case (name %)) query)]
        (->> sects
             (walk/postwalk
               (fn [node]
                 (if-not (map? node)
                   node
                   (cond
                     (:fns node)          (update node :fns #(filterv match? %))
                     (:groups node)       (update node :groups #(filterv (comp seq :fns) %))
                     (:subsections node)  (update node :subsections #(filterv (comp seq :groups) %))
                     :else node))))
             (filterv (comp seq :subsections)))))))

(defn sections->grid
  "Convert sections into a grid: a vector of group rows.
   Each row = {:section ... :subsection ... :label ... :fns [...]}.
   Row index = group index across all sections/subsections."
  [sects]
  (vec (for [section sects
             subsection (:subsections section)
             group (:groups subsection)]
         {:section (:name section)
          :subsection (:name subsection)
          :label (:label group)
          :fns (:fns group)})))
