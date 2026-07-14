(ns clj-memory-meter.core-test
  (:require [clj-memory-meter.core :as sut]
            [clojure.string :as str]
            [clojure.test :refer :all])
  (:import org.spdx.library.model.license.LicenseInfoFactory))

;; Sanity check we run the Clojure version which we think we do.
(deftest clojure-version-sanity-check
  (is (let [v (System/getenv "CLOJURE_VERSION")]
        (println "Running on Clojure" (clojure-version))
        (or (nil? v) (.startsWith (clojure-version) v)))))

(deftest basic-test
  (is (= 240 (sut/measure [] :bytes true)))
  (is (= 40 (sut/measure [] :bytes true :shallow true)))
  (is (= 48 (sut/measure (java.util.HashMap.) :bytes true)))
  (is (= 80 (sut/measure (java.io.File. "/") :bytes true)))
  (is (= 24 (sut/measure (java.time.Instant/now) :bytes true))))

(defmacro capture-system-out [& body]
  `(let [baos# (java.io.ByteArrayOutputStream.)
         sw# (java.io.PrintStream. baos#)
         bk-out# (System/out)]
     (System/setOut sw#)
     ~@body
     (.flush (System/out))
     (System/setOut bk-out#)
     (String. (.toByteArray baos#))))

(deftest output-test
  (is (= "240 B" (sut/measure [])))
  (is (= "3.1 KiB" (sut/measure (vec (range 100)))))
  (is (= "2.8 MiB" (sut/measure (vec (range 100000)))))

  (testing ":debug true"
    (is (str/ends-with? (str/trim (capture-system-out (sut/measure (apply list (range 4)) :debug true)))
                          (str/trim "
root [clojure.lang.PersistentList] 256 bytes (40 bytes)
  |
  +--_first [java.lang.Long] 24 bytes (24 bytes)
  |
  +--_rest [clojure.lang.PersistentList] 192 bytes (40 bytes)
    |
    +--_first [java.lang.Long] 24 bytes (24 bytes)
    |
    +--_rest [clojure.lang.PersistentList] 128 bytes (40 bytes)
      |
      +--_first [java.lang.Long] 24 bytes (24 bytes)
      |
      +--_rest [clojure.lang.PersistentList] 64 bytes (40 bytes)
        |
        +--_first [java.lang.Long] 24 bytes (24 bytes)")))))

(deftest error-test
  ;; Only test this against JDK14+.
  (when (>= (try (eval '(.major (Runtime/version))) (catch Exception _ 0)) 14)
    (is (< 100000 (sut/measure (LicenseInfoFactory/getListedLicenseById "Apache-2.0") :bytes true)))))

(deftest measure-list-test
  (is (= [{:x [4 3 2 1], :size "352 B"}
          {:x [3 2 1], :size "328 B"}
          {:x [2 1], :size "296 B"}
          {:x [1], :size "272 B"}
          {:x [], :size "240 B"}]
         (sut/measure-list (map #(vec (range % 0 -1)) (range 5)) :sort true)))

  (is (= [{:x [], :size "240 B"}
          {:x [1], :size "272 B"}
          {:x [2 1], :size "296 B"}
          {:x [3 2 1], :size "328 B"}
          {:x [4 3 2 1], :size "352 B"}]
         (sut/measure-list (map #(vec (range % 0 -1)) (range 5)))))

  (is (= [{:x [], :size 240}
          {:x [1], :size 272}
          {:x [2 1], :size 296}
          {:x [3 2 1], :size 328}
          {:x [4 3 2 1], :size 352}]
         (sut/measure-list (map #(vec (range % 0 -1)) (range 5)) :bytes true))))

(deftest measure-map-test
  (is (= [{:k "key4", :v [4 3 2 1], :size "352 B"}
          {:k "key3", :v [3 2 1], :size "328 B"}
          {:k "key2", :v [2 1], :size "296 B"}
          {:k "key1", :v [1], :size "272 B"}
          {:k "key0", :v [], :size "240 B"}]
         (sut/measure-map (into {} (map (fn [k] [(str "key" k) (vec (range k 0 -1))]) (range 5))))))

  (is (= [{:k "key4", :v [4 3 2 1], :size 352}
          {:k "key3", :v [3 2 1], :size 328}
          {:k "key2", :v [2 1], :size 296}
          {:k "key1", :v [1], :size 272}
          {:k "key0", :v [], :size 240}]
         (sut/measure-map (into {} (map (fn [k] [(str "key" k) (vec (range k 0 -1))]) (range 5))) :bytes true))))

(deftest measure-transposed-maps-test
  (is (= [{:k :expensive-key, :vals [[] [1] [2 1] [3 2 1] [4 3 2 1]], :size "1.4 KiB"}
          {:k :cheap-key, :vals [0 1 2 3 4], :size "384 B"}]
         (sut/measure-transposed-maps (map (fn [k] {:cheap-key k, :expensive-key (vec (range k 0 -1))}) (range 5)))))

  (is (= [{:k :expensive-key, :vals [[] [1] [2 1] [3 2 1] [4 3 2 1]], :size 1424}
          {:k :cheap-key, :vals [0 1 2 3 4], :size 384}]
         (sut/measure-transposed-maps (map (fn [k] {:cheap-key k, :expensive-key (vec (range k 0 -1))}) (range 5)) :bytes true))))
