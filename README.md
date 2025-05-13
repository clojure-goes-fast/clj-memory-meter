# clj-memory-meter [![CircleCI](https://img.shields.io/circleci/build/github/clojure-goes-fast/clj-memory-meter/master.svg)](https://dl.circleci.com/status-badge/redirect/gh/clojure-goes-fast/clj-memory-meter/tree/master) ![](https://img.shields.io/badge/dependencies-none-brightgreen) [![](https://img.shields.io/clojars/dt/com.clojure-goes-fast/clj-memory-meter?color=teal)](https://clojars.org/com.clojure-goes-fast/clj-memory-meter) [![](https://img.shields.io/badge/-changelog-blue.svg)](CHANGELOG.md)

**clj-memory-meter** is a Clojure library that let's you inspect how much memory
an object occupies in the heap. It is a wrapper around [Java Agent for Memory
Measurements](https://github.com/jbellis/jamm).

Extra features compared to **jamm**:

1. Can be added to the project as a simple dependency (don't have to provide a
separate agent file and point to it with a JVM option).
2. Loadable at runtime.
3. Human-readable size output.
4. Memory usage tracing.

**jamm** JAR file is shipped together with **clj-memory-meter** and unpacked at
runtime.

## Usage

On JDK11 and above, you must start your application with JVM option
`-Djdk.attach.allowAttachSelf`, otherwise the agent will not be able to
dynamically attach to the running process.

 - For Leiningen, add `:jvm-opts ["-Djdk.attach.allowAttachSelf"]` to
 `project.clj`.
 - For tools.deps, add the same `:jvm-opts` to `deps.edn` or write
`-J-Djdk.attach.allowAttachSelf` explicitly in your REPL command.

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
;; integer instead of true to limit the number of nested levels printed.

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

### Troubleshooting

Starting with Java 17, JVM no longer allows access to private fields of classes
residing in external modules. On newer Java versions, clj-memory-meter utilizes
Unsafe to get into such private fields. As any Unsafe usage, it can potentially
crash the application. Use at your own risk.

Because of Unsafe, you may eventually run into errors like this one:

```
Execution error (UnsupportedOperationException) at sun.misc.Unsafe/objectFieldOffset (Unsafe.java:645).
can't get field offset on a hidden class: private final java.util.regex.Pattern$BmpCharPredicate java.util.regex.Pattern$BmpCharPredicate$$Lambda$22/0x80000002c.arg$1
```

The only way to prevent this from happening is to start the REPL with
`--add-opens` JVM options for the in-module private classes. Expand the block
below for the tools.deps alias I use locally for my development REPL.

<details>
  <summary>Click to show full :add-opens alias</summary>
  <pre><code>:add-opens {:jvm-opts ["--add-opens=java.base/java.io=ALL-UNNAMED"
                       "--add-opens=java.base/java.lang=ALL-UNNAMED"
                       "--add-opens=java.base/java.lang.annotation=ALL-UNNAMED"
                       "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED"
                       "--add-opens=java.base/java.lang.module=ALL-UNNAMED"
                       "--add-opens=java.base/java.lang.ref=ALL-UNNAMED"
                       "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
                       "--add-opens=java.base/java.math=ALL-UNNAMED"
                       "--add-opens=java.base/java.net=ALL-UNNAMED"
                       "--add-opens=java.base/java.net.spi=ALL-UNNAMED"
                       "--add-opens=java.base/java.nio=ALL-UNNAMED"
                       "--add-opens=java.base/java.nio.channels=ALL-UNNAMED"
                       "--add-opens=java.base/java.nio.channels.spi=ALL-UNNAMED"
                       "--add-opens=java.base/java.nio.charset=ALL-UNNAMED"
                       "--add-opens=java.base/java.nio.charset.spi=ALL-UNNAMED"
                       "--add-opens=java.base/java.nio.file=ALL-UNNAMED"
                       "--add-opens=java.base/java.nio.file.attribute=ALL-UNNAMED"
                       "--add-opens=java.base/java.nio.file.spi=ALL-UNNAMED"
                       "--add-opens=java.base/java.security=ALL-UNNAMED"
                       "--add-opens=java.base/java.security.cert=ALL-UNNAMED"
                       "--add-opens=java.base/java.security.interfaces=ALL-UNNAMED"
                       "--add-opens=java.base/java.security.spec=ALL-UNNAMED"
                       "--add-opens=java.base/java.text=ALL-UNNAMED"
                       "--add-opens=java.base/java.text.spi=ALL-UNNAMED"
                       "--add-opens=java.base/java.time=ALL-UNNAMED"
                       "--add-opens=java.base/java.time.chrono=ALL-UNNAMED"
                       "--add-opens=java.base/java.time.format=ALL-UNNAMED"
                       "--add-opens=java.base/java.time.temporal=ALL-UNNAMED"
                       "--add-opens=java.base/java.time.zone=ALL-UNNAMED"
                       "--add-opens=java.base/java.util=ALL-UNNAMED"
                       "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED"
                       "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED"
                       "--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED"
                       "--add-opens=java.base/java.util.function=ALL-UNNAMED"
                       "--add-opens=java.base/java.util.jar=ALL-UNNAMED"
                       "--add-opens=java.base/java.util.regex=ALL-UNNAMED"
                       "--add-opens=java.base/java.util.spi=ALL-UNNAMED"
                       "--add-opens=java.base/java.util.stream=ALL-UNNAMED"
                       "--add-opens=java.base/java.util.zip=ALL-UNNAMED"
                       "--add-opens=java.base/javax.crypto=ALL-UNNAMED"
                       "--add-opens=java.base/javax.crypto.interfaces=ALL-UNNAMED"
                       "--add-opens=java.base/javax.crypto.spec=ALL-UNNAMED"
                       "--add-opens=java.base/javax.net=ALL-UNNAMED"
                       "--add-opens=java.base/javax.net.ssl=ALL-UNNAMED"
                       "--add-opens=java.base/javax.security.auth=ALL-UNNAMED"
                       "--add-opens=java.base/javax.security.auth.callback=ALL-UNNAMED"
                       "--add-opens=java.base/javax.security.auth.login=ALL-UNNAMED"
                       "--add-opens=java.base/javax.security.auth.spi=ALL-UNNAMED"
                       "--add-opens=java.base/javax.security.auth.x500=ALL-UNNAMED"
                       "--add-opens=java.base/javax.security.cert=ALL-UNNAMED"
                       "--add-opens=java.desktop/sun.java2d.marlin=ALL-UNNAMED"
                       "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED"
                       "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
                       "--add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED"]}
   </code></pre>
</details>

## Memory usage tracing

`clj-memory-meter.trace` provides a way to instrument functions so that they
report heap usage before and after the invocation, and also memory size of the
arguments and return values. This blog post describes why you would want to use
it and how: [Tracking memory usage with
clj-memory-meter.trace](https://clojure-goes-fast.com/blog/tracking-memory-usage/).

Here's a short example:

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

Copyright 2018-2025 Oleksandr Yakushev
