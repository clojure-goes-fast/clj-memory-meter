(ns clj-memory-meter.trace
  (:require [clojure.string :as str]
            [clj-memory-meter.core :as mm])
  (:import java.lang.management.ManagementFactory))

(def ^:private ^:dynamic *depth* 0)
(def ^:private ^:dynamic *initial-used-heap* nil)

(def ^:dynamic *force-gc-around-traced-functions*
  "When true, `System/gc` will be called before invoking a traced function and
  after it returns its result. Bind to false if it slows down your evaluation
  too much (but this will make used heap measurements inaccurate)."
  true)

(def ^:dynamic *calculate-argument-and-return-sizes*
  "When true, `clj-memory-meter.core/measure` will be called on each traced
  function's argument and the returned value. Bind to false if it slows down
  your evaluation too much."
  true)

(defn- funcall-to-string
  "Print the invokation of a functio and size of its arguments to a string."
  [fname args outer-prefix]
  (format "%s(%s %s)" outer-prefix fname
          (if *calculate-argument-and-return-sizes*
            (str/join " " (map #(format "<%s>" (mm/measure %)) args))
            "...")))

(defn- trace-indent []
  (str/join (repeat *depth* "│ ")))

(defn- used-heap-bytes []
  (.getUsed (.getHeapMemoryUsage (ManagementFactory/getMemoryMXBean))))

(defn- used-heap []
  (let [max-heap-pct (/ (.getMax (.getHeapMemoryUsage
                                  (ManagementFactory/getMemoryMXBean)))
                        100.0)]
    (if *initial-used-heap*
      (let [delta (long (- (used-heap-bytes) *initial-used-heap*))
            sign (if (pos? delta) "+" "-")]
        (format "%s%s (%s%.1f%%)"
                sign (mm/convert-to-human-readable (Math/abs delta))
                sign (/ (Math/abs delta) max-heap-pct)))
      (format "%s (%.1f%%)"
              (mm/convert-to-human-readable (used-heap-bytes))
              (/ (used-heap-bytes) max-heap-pct)))))

(defn- trace-fn-call [name f args]
  (println (trace-indent))
  (when *force-gc-around-traced-functions* (System/gc))
  (println (funcall-to-string (str name) args (trace-indent))
           "| Heap:" (used-heap))
  (let [value (binding [*depth* (inc *depth*)]
                (apply f args))
        value-size (if *calculate-argument-and-return-sizes*
                     (str "<" (mm/measure value) ">")
                     "...")]
    (binding [*depth* (inc *depth*)]
      (println (trace-indent)))
    (when *force-gc-around-traced-functions* (System/gc))
    (println
     (format "%s└─→ %s | Heap: %s" (trace-indent) value-size (used-heap)))
    value))

(defn- relative-usage-entrypoint [f]
  (System/gc)
  (println "Initial used heap:" (used-heap))
  (binding [*initial-used-heap* (used-heap-bytes)
            *depth* (inc *depth*)]
    (let [result (f)]
      (System/gc)
      (println "Final used heap:" (used-heap))
      result)))

(def ^:private traced-vars (atom #{}))

(defn- traceable? [v]
  (and (var? v) (ifn? @v) (not (:macro (meta v)))))

;;;; Public API

(defn trace-var
  "If the specified Var holds an IFn and is not marked as a macro, its
  contents is replaced with a version wrapped in a tracing call;
  otherwise nothing happens. Can be undone with `untrace-var`."
  [v]
  (when (and (traceable? v) (not (contains? (meta v) ::traced)))
    (let [f @v
          vname (symbol v)]
      (swap! traced-vars conj v)
      (alter-var-root v #(fn tracing-wrapper [& args]
                           (trace-fn-call vname % args)))
      (alter-meta! v assoc ::traced f)
      v)))

(defn untrace-var
  "Reverses the effect of `trace-var` for the given Var, replacing the traced
  function with the original, untraced version. No-op for non-traced Vars."
  [v]
  (let [f (::traced (meta v))]
    (when f
      (alter-var-root v (constantly (::traced (meta v))))
      (alter-meta! v dissoc ::traced)
      (swap! traced-vars disj v)
      v)))

(defn untrace-all
  "Reverses the effect of tracing for all already traced vars and namespaces."
  []
  (run! untrace-var @traced-vars))

(defmacro with-relative-usage [& body]
  `(#'~`relative-usage-entrypoint (fn [] ~@body)))
