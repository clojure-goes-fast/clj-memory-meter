(task-options!
 pom {:project     'com.clojure-goes-fast/clj-memory-meter
      :version     "0.1.2"
      :description "Measure object memory consumption from Clojure"
      :url         "http://github.com/clojure-goes-fast/clj-memory-meter"
      :scm         {:url "http://github.com/clojure-goes-fast/clj-memory-meter"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}}
 push {:repo "clojars"})

(set-env! :resource-paths #{"src" "res"}
          :source-paths   #{"src"}
          :dependencies   '[[org.clojure/clojure "1.9.0" :scope "provided"]])

(deftask build []
  (comp (pom)
        (jar)
        (sift :move {#"jamm-.+\.jar" "dont-push.bin"})))
