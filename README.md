# clj-memory-meter

This library is a thin wrapper
around [Java Agent for Memory Measurements](https://github.com/jbellis/jamm). It
allows to inspect at runtime how much memory an object occupies together with
all its child fields.

Extra features compared to **jamm**:

1. Easy runtime loading (you can start using it at any time at the REPL,
   providing additional startup parameters is not necessary).
2. Human-readable size output.

**jamm** JAR file is shipped together with **clj-memory-meter** and unpacked at
runtime.

## Usage

**JDK9+:** you must start the JVM with option `-Djdk.attach.allowAttachSelf`,
otherwise the agent will not be able to dynamically attach to the running
process. For Leiningen, add `:jvm-opts ["-Djdk.attach.allowAttachSelf"]` to
`project.clj`. For Boot, start the process with environment variable
`BOOT_JVM_OPTIONS="-Djdk.attach.allowAttachSelf"`.

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
;=> "9.6 KB"

;; :shallow true calculates only memory occupied by the object itself,
;; without children

(mm/measure (object-array (repeatedly 100 #(String. "hello"))) :shallow true)
;=> "416 B"
(mm/measure (object-array (repeatedly 100 #(String. "hello"))))
;=> "2.8 KB"

;; :bytes true can be passed to return the raw number of bytes

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

## License

jamm is distributed under Apache-2.0.
See [APACHE_PUBLIC_LICENSE](license/APACHE_PUBLIC_LICENSE) file. The location of the original
repository
is
[https://github.com/jbellis/jamm](https://github.com/jbellis/jamm).

---

clj-memory-meter is distributed under the Eclipse Public License.
See [ECLIPSE_PUBLIC_LICENSE](license/ECLIPSE_PUBLIC_LICENSE).

Copyright 2018 Alexander Yakushev
