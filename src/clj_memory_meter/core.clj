(ns clj-memory-meter.core
  (:require [clojure.java.io :as io])
  (:import clojure.lang.DynamicClassLoader
           java.io.File
           java.lang.management.ManagementFactory))

;;;; Agent JAR unpacking

(def ^:private jamm-jar-name "jamm-0.3.2.jar")

(defn- unpack-jamm-from-resource []
  (let [dest (File/createTempFile "jamm" ".jar")]
    (io/copy (io/input-stream (io/resource jamm-jar-name)) dest)
    (.getAbsolutePath dest)))

(defonce ^:private extracted-jamm-jar (unpack-jamm-from-resource))

;;;; Agent loading

(defn- get-dynamic-classloader
  []
  (loop [loader (.getContextClassLoader (Thread/currentThread))]
    (let [parent (.getParent loader)]
      (if (instance? DynamicClassLoader parent)
        (recur parent)
        loader))))

(defn- add-tools-jar-to-classloader!
  [^DynamicClassLoader loader]
  (let [file (io/file (System/getProperty "java.home"))
        file (if (.equalsIgnoreCase (.getName file) "jre")
               (.getParentFile file)
               file)
        file (io/file file "lib" "tools.jar")]
    (.addURL loader (io/as-url file))))

(defonce ^:private tools-jar-classloader
  ;; First, find top-level Clojure classloader.
  (delay
   (try
     (let [^DynamicClassLoader loader (get-dynamic-classloader)]
      ;; Loader found, add tools.jar to it
      (add-tools-jar-to-classloader! loader)
      loader)
     (catch Exception e
       (throw (ex-info "Could not prepare the classloader."
                       {:hint "If you're currently requiring clj-memory-meter.core in your (ns) form, try removing it from there and executing (require 'clj-memory-meter.core) manually after everything is loaded."}
                       e))))))

(defn- get-self-pid
  "Returns the process ID of the current JVM process."
  []
  (let [^String rt-name (.getName (ManagementFactory/getRuntimeMXBean))]
    (subs rt-name 0 (.indexOf rt-name "@"))))

#_(get-self-pid)

(defn- mk-vm [pid]
  (let [vm-class (Class/forName "com.sun.tools.attach.VirtualMachine"
                                false @tools-jar-classloader)
        method (.getDeclaredMethod vm-class "attach" (into-array Class [String]))]
    (.invoke method nil (object-array [pid]))))

(defn- load-jamm-agent []
  (let [vm (mk-vm (get-self-pid))]
    (.loadAgent vm extracted-jamm-jar)
    (.detach vm)
    true))

(def ^:private jamm-agent-loaded (delay (load-jamm-agent)))

;;;; Public API

(def ^:private memory-meter
  (delay
   @jamm-agent-loaded
   (.newInstance (Class/forName "org.github.jamm.MemoryMeter"))))

(defn- convert-to-human-readable
  "Taken from http://programming.guide/java/formatting-byte-size-to-human-readable-format.html."
  [bytes]
  (let [unit 1024]
    (if (< bytes unit)
      (str bytes " B")
      (let [exp (int (/ (Math/log bytes) (Math/log unit)))
            pre (nth "KMGTPE" (dec exp))]
        (format "%.1f %sB" (/ bytes (Math/pow unit exp)) pre)))))

#_(convert-to-human-readable 512)
#_(convert-to-human-readable 10e8)

(defn measure
  "Measure the memory usage of the `object`. Return a human-readable string.

  :debug   - if true, print the object layout tree to stdout. Can also be set to
             a number to limit the nesting level being printed.
  :shallow - if true, count only the object header and its fields, don't follow
             object references
  :bytes   - if true, return a number of bytes instead of a string
  :meter   - custom org.github.jamm.MemoryMeter object"
  [object & {:keys [debug shallow bytes meter]}]
  (let [m (or meter @memory-meter)
        m (cond (integer? debug) (.enableDebug m debug)
                debug (.enableDebug m)
                :else m)
        byte-count (if shallow
                     (.measure m object)
                     (.measureDeep m object))]
    (if bytes
      byte-count
      (convert-to-human-readable byte-count))))

#_(measure (vec (repeat 100 "hello")) :debug true)
#_(measure (object-array (repeatedly 1000 (fn [] (String. "foobarbaz")))) :bytes true)
