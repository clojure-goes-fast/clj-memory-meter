(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.string :as str]
            [clojure.tools.build.api :as b]
            [clojure.tools.build.tasks.write-pom]
            [org.corfield.build :as bb]))

(defmacro opts+ []
  `(assoc ~'opts
          :lib 'com.clojure-goes-fast/clj-memory-meter
          :version "0.2.2"
          :resource-dirs ["res"]
          :src-pom "res/pom-template.xml"))

;; Hack to propagate scope into pom.
(alter-var-root
 #'clojure.tools.build.tasks.write-pom/to-dep
 (fn [f]
   (fn [[_ {:keys [mvn/scope]} :as arg]]
     (let [res (f arg)
           alias (some-> res first namespace)]
       (cond-> res
         (and alias scope) (conj [(keyword alias "scope") scope]))))))

(defn test "Run all the tests." [opts]
  (bb/run-tests (cond-> opts
                  (:clj opts) (assoc :aliases [(:clj opts)])))
  opts)

(defn jar
  "Run the CI pipeline of tests (and build the JAR).
  Specify :cljs true to run the ClojureScript tests as well."
  [opts]
  (bb/clean opts)
  (let [{:keys [class-dir src+dirs] :as opts} (#'bb/jar-opts (opts+))]
    (b/write-pom opts)
    (b/copy-dir {:src-dirs   src+dirs
                 :target-dir class-dir
                 :include "**.{clj,jar}"})
    (println "Building jar...")
    (b/jar opts)))

(defn deploy "Deploy the JAR to Clojars." [opts]
  (bb/deploy (opts+)))
