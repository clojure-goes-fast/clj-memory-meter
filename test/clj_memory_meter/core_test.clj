(ns clj-memory-meter.core-test
  (:require [clj-memory-meter.core :as sut]
            [clojure.string :as str]
            [clojure.test :refer :all])
  (:import org.spdx.library.model.license.LicenseInfoFactory))

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
