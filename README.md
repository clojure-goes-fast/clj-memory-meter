# clj-memory-meter [![CircleCI](https://img.shields.io/circleci/build/github/clojure-goes-fast/clj-memory-meter/master.svg)](https://dl.circleci.com/status-badge/redirect/gh/clojure-goes-fast/clj-memory-meter/tree/master) ![](https://img.shields.io/badge/dependencies-none-brightgreen) [![](https://img.shields.io/clojars/dt/com.clojure-goes-fast/clj-memory-meter?color=teal)](https://clojars.org/com.clojure-goes-fast/clj-memory-meter) [![](https://img.shields.io/badge/-changelog-blue.svg)](CHANGELOG.md)

**clj-memory-meter** is a Clojure library that allows you to inspect how much
memory an object occupies at runtime, together with all its child fields. It is
a wrapper around [Java Agent for Memory
Measurements](https://github.com/jbellis/jamm).

Extra features compared to **jamm**:

1. Can be added to the project as a simple dependency (don't have to provide a
separate agent file and point to it with a JVM option).
2. Loadable at runtime.
3. Human-readable size output.

**jamm** JAR file is shipped together with **clj-memory-meter** and unpacked at
runtime.

## Usage

**JDK11+:** you must start your application with JVM option
`-Djdk.attach.allowAttachSelf`, otherwise the agent will not be able to
dynamically attach to the running process. For Leiningen, add `:jvm-opts
["-Djdk.attach.allowAttachSelf"]` to `project.clj`. For tools.deps, add the same
`:jvm-opts` to `deps.edn` or write `-J-Djdk.attach.allowAttachSelf` explicitly
in your REPL command.

Add `com.clojure-goes-fast/clj-memory-meter` to your dependencies:

[![](https://clojars.org/com.clojure-goes-fast/clj-memory-meter/latest-version.svg)](https://clojars.org/com.clojure-goes-fast/clj-memory-meter)

Once loaded, you can measure objects like this:

```clojure
(require '[clj-memory-meter.core :as mm])

;; measure calculates total memory occupancy of the object

(mm/measure "Hello, world!")
;=> "72 B"

(mm/measure [])
;=> "240 B"

(mm/measure (into {} (map #(vector % (str %)) (range 100))))
;=> "9.6 KiB"

;; :shallow true calculates only memory occupied by the object itself,
;; without children

(mm/measure (object-array (repeatedly 100 #(String. "hello"))) :shallow true)
;=> "416 B"
(mm/measure (object-array (repeatedly 100 #(String. "hello"))))
;=> "2.8 KiB"

;; :bytes true can be passed to return the size in bytes as a number

(mm/measure (object-array (repeatedly 100 #(String. "hello"))) :bytes true)
;=> 2848

;; :debug true can be passed to print the object hierarchy. You can also pass an
;; integer number to limit the number of nested levels printed.

(mm/measure (apply list (range 4)) :debug true)

; root [clojure.lang.PersistentList] 256 bytes (40 bytes)
;   |
;   +--_first [java.lang.Long] 24 bytes (24 bytes)
;   |
;   +--_rest [clojure.lang.PersistentList] 192 bytes (40 bytes)
;     |
;     +--_first [java.lang.Long] 24 bytes (24 bytes)
;     |
;     +--_rest [clojure.lang.PersistentList] 128 bytes (40 bytes)
;       |
;       +--_first [java.lang.Long] 24 bytes (24 bytes)
;       |
;       +--_rest [clojure.lang.PersistentList] 64 bytes (40 bytes)
;         |
;         +--_first [java.lang.Long] 24 bytes (24 bytes)

;; Custom MemoryMeter object can be passed. See what you can configure here:
;; https://github.com/jbellis/jamm/blob/master/src/org/github/jamm/MemoryMeter.java
```

**Note on JDK17+:** Starting with Java 17, JVM no longer allows accessing
private fields of classes residing in external modules. On those versions of
Java, JAMM and clj-memory-meter utilize Unsafe to get into such private fields.
As any Unsafe usage, it can potentially crash the application. Use at your own
risk. Also, the Unsafe itself may go away in the future versions of Java.

## Memory usage tracing

The `clj-memory-meter.trace` provides a way to instrument functions so that they
report heap usage before and after the invocation, and also memory size of the
arguments and return values. Here's how it works:

```clj
(require '[clj-memory-meter.trace :as cmm.trace])

(defn make-numbers [n]
  (vec (repeatedly n #(rand-int 10000))))

(defn avg [numbers]
  (/ (reduce + numbers) (count numbers)))

(defn distribution [m]
  (->> (range 1 m)
       (mapv #(make-numbers (* 1000000 %)))
       (mapv avg)))

(cmm.trace/trace-var #'make-numbers)
(cmm.trace/trace-var #'avg)
(cmm.trace/trace-var #'distribution)

(cmm.trace/with-relative-usage
  (distribution 5))

; Initial used heap: 63.2 MiB (1.5%)
; │ (example/distribution <24 B>) | Heap: +472 B (+0.0%)
; │ │ (example/make-numbers <24 B>) | Heap: +1.5 KiB (+0.0%)
; │ │ └─→ <20.2 MiB> | Heap: +20.2 MiB (+0.5%)
; │ │
; │ │ (example/make-numbers <24 B>) | Heap: +20.2 MiB (+0.5%)
; │ │ └─→ <40.5 MiB> | Heap: +60.7 MiB (+1.5%)
; │ │
; │ │ (example/make-numbers <24 B>) | Heap: +60.7 MiB (+1.5%)
; │ │ └─→ <60.7 MiB> | Heap: +121.6 MiB (+3.0%)
; │ │
; │ │ (example/make-numbers <24 B>) | Heap: +121.6 MiB (+3.0%)
; │ │ └─→ <80.9 MiB> | Heap: +202.7 MiB (+4.9%)
; │ │
; │ │ (example/avg <20.2 MiB>) | Heap: +230.7 MiB (+5.6%)
; │ │ └─→ <152 B> | Heap: +202.7 MiB (+4.9%)
; │ │
; │ │ (example/avg <40.5 MiB>) | Heap: +229.7 MiB (+5.6%)
; │ │ └─→ <152 B> | Heap: +202.7 MiB (+4.9%)
; │ │
; │ │ (example/avg <60.7 MiB>) | Heap: +297.4 MiB (+7.3%)
; │ │ └─→ <152 B> | Heap: +202.7 MiB (+4.9%)
; │ │
; │ │ (example/avg <80.9 MiB>) | Heap: +295.0 MiB (+7.2%)
; │ │ └─→ <152 B> | Heap: +202.7 MiB (+4.9%)
; │ └─→ <864 B> | Heap: +23.3 KiB (+0.0%)
; Final used heap: +23.2 KiB (+0.0%)
```

Use `trace-var` to instrument a function. You can undo the instrumenting by
calling `untrace-var` on it or redefining the function. Once instrumented, the
function will print the current heap usage before and after its execution.

By default, the absolute heap usage numbers are printed. But if you wrap the
top-level call in `with-relative-usage` macro (like in the example above), each
heap usage report will be relative to the **beginning** of execution (not
relative to the previous heap report). So, in the example, `Heap: +20.2 MiB` and
then `Heap: +60.7 MiB` means that the heap usage grew by 60 megabytes, not 80.

If the traced function gets called a lot, it will significantly slow down
execution. **Don't use this in production.** Also, you can disable the following
features to reduce the overhead:

- `*calculate-argument-and-return-sizes*` — bind to `false` to skip measuring
  traced function arguments and returned values;
- `*force-gc-around-traced-functions*` — bind to `false` to avoid calling
  `(System/gc)` at traced function boundaries. Note that this will make heap
  usage reports not very accurate since they will also include collectable
  garbage.

## License

jamm is distributed under Apache-2.0.
See [APACHE_PUBLIC_LICENSE](license/APACHE_PUBLIC_LICENSE) file. The location of the original
repository
is
[https://github.com/jbellis/jamm](https://github.com/jbellis/jamm).

---

clj-memory-meter is distributed under the Eclipse Public License.
See [ECLIPSE_PUBLIC_LICENSE](license/ECLIPSE_PUBLIC_LICENSE).

Copyright 2018-2025 Alexander Yakushev
