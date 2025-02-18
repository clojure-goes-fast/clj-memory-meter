(ns clj-memory-meter.trace-test
  (:require [clj-memory-meter.trace :as sut]
            clojure.pprint
            [clojure.string :as str]
            [clojure.test :refer :all])
  (:import java.util.regex.Pattern))

(defn ns-fixture
  [f]
  (in-ns 'clj-memory-meter.trace-test)
  (f))

;; Force tests to be run within this namespaces
;; (see https://github.com/cognitect-labs/test-runner/issues/38)
(use-fixtures :once ns-fixture)

(defmacro with-out-trimmed-str [& body]
  `(let [res# (with-out-str ~@body)]
     (->> res#
          str/split-lines
          (remove str/blank?)
          (map str/trim)
          (str/join "\n"))))

(defn =str [s1 s2]
  (let [s1s (str/split-lines s1)
        s2s (str/split-lines s2)
        diff (map (fn [l1 l2]
                    (when-not
                        (or (= l2 "**")
                            (= l1 l2)
                            (and (str/includes? l2 "**")
                                 (let [parts (map #(Pattern/quote %) (str/split l2 #"\*\*"))
                                       rx (re-pattern (str/join ".*?" parts))]
                                   (re-find rx l1))))
                      [l1 l2]))
                  (str/split-lines s1)
                  (str/split-lines s2))]
    (or (every? nil? diff)
        (do (println "FAIL\n:")
            (clojure.pprint/pprint diff)
            (println  "\n-------\ns2:\n")
            (clojure.pprint/pprint s2s)
            false))))

(defn make-numbers [n]
  (vec (repeatedly n #(double (rand-int 10000)))))

(defn avg [numbers]
  (/ (reduce + numbers) (count numbers)))

(defn distribution [m]
  (->> (range 1 m)
       (mapv #(make-numbers (* 1000000 %)))
       (mapv avg)))

(sut/trace-var #'make-numbers)
(sut/trace-var #'avg)
(sut/trace-var #'distribution)

(deftest basic-test
  (is (=str (with-out-trimmed-str (distribution 5))
            "(clj-memory-meter.trace-test/distribution <24 B>) | Heap: ** (**%)
│
│ (clj-memory-meter.trace-test/make-numbers <24 B>) | Heap: ** (**%)
│ │
│ └─→ <28.1 MiB> | Heap: ** (**%)
│
│ (clj-memory-meter.trace-test/make-numbers <24 B>) | Heap: ** (**%)
│ │
│ └─→ <56.1 MiB> | Heap: ** (**%)
│
│ (clj-memory-meter.trace-test/make-numbers <24 B>) | Heap: ** (**%)
│ │
│ └─→ <84.2 MiB> | Heap: ** (**%)
│
│ (clj-memory-meter.trace-test/make-numbers <24 B>) | Heap: ** (**%)
│ │
│ └─→ <112.2 MiB> | Heap: ** (**%)
│
│ (clj-memory-meter.trace-test/avg <28.1 MiB>) | Heap: ** (**%)
│ │
│ └─→ <24 B> | Heap: ** (**%)
│
│ (clj-memory-meter.trace-test/avg <56.1 MiB>) | Heap: ** (**%)
│ │
│ └─→ <24 B> | Heap: ** (**%)
│
│ (clj-memory-meter.trace-test/avg <84.2 MiB>) | Heap: ** (**%)
│ │
│ └─→ <24 B> | Heap: ** (**%)
│
│ (clj-memory-meter.trace-test/avg <112.2 MiB>) | Heap: ** (**%)
│ │
│ └─→ <24 B> | Heap: ** (**%)
│
└─→ <352 B> | Heap: ** (**%)
")))

(deftest relative-test
  (is (=str (with-out-trimmed-str (sut/with-relative-usage (distribution 5)))
            "Initial used heap: **
│
│ (clj-memory-meter.trace-test/distribution <24 B>) | Heap: ** (**%)
│ │
│ │ (clj-memory-meter.trace-test/make-numbers <24 B>) | Heap: ** (**%)
│ │ │
│ │ └─→ <28.1 MiB> | Heap: +** MiB (+**%)
│ │
│ │ (clj-memory-meter.trace-test/make-numbers <24 B>) | Heap: +** MiB (+**%)
│ │ │
│ │ └─→ <56.1 MiB> | Heap: +** MiB (+**%)
│ │
│ │ (clj-memory-meter.trace-test/make-numbers <24 B>) | Heap: +** MiB (+**%)
│ │ │
│ │ └─→ <84.2 MiB> | Heap: +** MiB (+**%)
│ │
│ │ (clj-memory-meter.trace-test/make-numbers <24 B>) | Heap: +** MiB (+**%)
│ │ │
│ │ └─→ <112.2 MiB> | Heap: +** MiB (+**%)
│ │
│ │ (clj-memory-meter.trace-test/avg <28.1 MiB>) | Heap: +** MiB (+**%)
│ │ │
│ │ └─→ <24 B> | Heap: +** MiB (+**%)
│ │
│ │ (clj-memory-meter.trace-test/avg <56.1 MiB>) | Heap: +** MiB (+**%)
│ │ │
│ │ └─→ <24 B> | Heap: +** MiB (+**%)
│ │
│ │ (clj-memory-meter.trace-test/avg <84.2 MiB>) | Heap: +** MiB (+**%)
│ │ │
│ │ └─→ <24 B> | Heap: +** MiB (+**%)
│ │
│ │ (clj-memory-meter.trace-test/avg <112.2 MiB>) | Heap: +** MiB (+**%)
│ │ │
│ │ └─→ <24 B> | Heap: +** MiB (+**%)
│ │
│ └─→ <352 B> | Heap: ** (**%)
Final used heap: ** (**%)")))

(deftest no-args-measurement-test
  (is (=str (with-out-trimmed-str
              (binding [sut/*calculate-argument-and-return-sizes* false]
                (sut/with-relative-usage (distribution 5))))
            "Initial used heap: **
│
│ (clj-memory-meter.trace-test/distribution ...) | Heap: ** (**%)
│ │
│ │ (clj-memory-meter.trace-test/make-numbers ...) | Heap: ** (**%)
│ │ │
│ │ └─→ ... | Heap: +** MiB (+**%)
│ │
│ │ (clj-memory-meter.trace-test/make-numbers ...) | Heap: +** MiB (+**%)
│ │ │
│ │ └─→ ... | Heap: +** MiB (+**%)
│ │
│ │ (clj-memory-meter.trace-test/make-numbers ...) | Heap: +** MiB (+**%)
│ │ │
│ │ └─→ ... | Heap: +** MiB (+**%)
│ │
│ │ (clj-memory-meter.trace-test/make-numbers ...) | Heap: +** MiB (+**%)
│ │ │
│ │ └─→ ... | Heap: +** MiB (+**%)
│ │
│ │ (clj-memory-meter.trace-test/avg ...) | Heap: +** MiB (+**%)
│ │ │
│ │ └─→ ... | Heap: +** MiB (+**%)
│ │
│ │ (clj-memory-meter.trace-test/avg ...) | Heap: +** MiB (+**%)
│ │ │
│ │ └─→ ... | Heap: +** MiB (+**%)
│ │
│ │ (clj-memory-meter.trace-test/avg ...) | Heap: +** MiB (+**%)
│ │ │
│ │ └─→ ... | Heap: +** MiB (+**%)
│ │
│ │ (clj-memory-meter.trace-test/avg ...) | Heap: +** MiB (+**%)
│ │ │
│ │ └─→ ... | Heap: +** MiB (+**%)
│ │
│ └─→ ... | Heap: ** (**%)
Final used heap: ** (**%)")))
