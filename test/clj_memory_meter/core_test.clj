(ns clj-memory-meter.core-test
  (:require [clj-memory-meter.core :as sut]
            [clojure.test :refer :all]))

(deftest basic-test
  (is (= 240 (sut/measure [] :bytes true)))
  (is (= 40 (sut/measure [] :bytes true :shallow true)))
  (is (= 48 (sut/measure (java.util.HashMap.) :bytes true)))
  (is (= 80 (sut/measure (java.io.File. "/") :bytes true)))
  (is (= 24 (sut/measure (java.time.Instant/now) :bytes true))))

(deftest output-test
  (is (= "240 B" (sut/measure [])))
  (is (= "3.1 KiB" (sut/measure (vec (range 100)))))
  (is (= "2.8 MiB" (sut/measure (vec (range 100000))))))
