(ns clj-memory-meter.core
  (:require [clojure.java.io :as io])
  (:import java.io.File
           java.lang.management.ManagementFactory
           java.net.URLClassLoader))

;;;; Agent JAR unpacking

(def ^:private jamm-jar-name "jamm-0.4.0-cljmm-SNAPSHOT.jar")

(defn- unpack-jamm-from-resource []
  (let [dest (File/createTempFile "jamm" ".jar")]
    (io/copy (io/input-stream (io/resource jamm-jar-name)) dest)
    (.getAbsolutePath dest)))

(defonce ^:private extracted-jamm-jar (unpack-jamm-from-resource))

;;;; Agent loading

(defn- tools-jar-url []
  (let [file (io/file (System/getProperty "java.home"))
        file (if (.equalsIgnoreCase (.getName file) "jre")
               (.getParentFile file)
               file)
        file (io/file file "lib" "tools.jar")]
    (io/as-url file)))

(defn- add-url-to-classloader-reflective
  "This is needed for cases when there is no DynamicClassLoader in the classloader
  chain (i.e., the env is not a REPL). Note that this will throw warning on Java
  11 and stops working after Java 17."
  [^URLClassLoader loader, url]
  (doto (.getDeclaredMethod URLClassLoader "addURL" (into-array Class [java.net.URL]))
    (.setAccessible true)
    (.invoke loader (object-array [url]))))

(defn- get-classloader
  "Find the uppermost DynamicClassLoader in the chain. However, if the immediate
  context classloader is not a DynamicClassLoader, it means we are not run in
  the REPL environment, and have to use reflection to patch this classloader.

  Return a tuple of [classloader is-it-dynamic?]."
  []
  (let [dynamic-cl?
        #(#{"clojure.lang.DynamicClassLoader" "boot.AddableClassLoader"}
          (.getName (class %)))

        ctx-loader (.getContextClassLoader (Thread/currentThread))]
    (if (dynamic-cl? ctx-loader)
      ;; The chain starts with a dynamic classloader, walk the chain up to find
      ;; the uppermost one.
      (loop [loader ctx-loader]
        (let [parent (.getParent loader)]
          (if (dynamic-cl? parent)
            (recur parent)
            [loader true])))

      ;; Otherwise, return the immediate classloader and tell it's not dynamic.
      [ctx-loader false])))

(def ^:private tools-jar-classloader
  (delay
   (let [tools-jar (tools-jar-url)
         [loader dynamic?] (get-classloader)]
     (if dynamic?
       (.addURL loader tools-jar)
       (add-url-to-classloader-reflective loader tools-jar))
     loader)))

(defn- ^Class get-virtualmachine-class []
  ;; In JDK9+, the class is already present, no extra steps required.
  (or (try (resolve 'com.sun.tools.attach.VirtualMachine)
           (catch ClassNotFoundException _))
      ;; In earlier JDKs, load tools.jar and get the class from there.
      (Class/forName "com.sun.tools.attach.VirtualMachine"
                     false @tools-jar-classloader)))

(defmacro ^:private get-self-pid*
  "This macro expands into proper way of obtaining self PID on JDK9+, and digs
  into internals on JDK8."
  []
  (if (try (resolve 'java.lang.ProcessHandle)
           (catch ClassNotFoundException _))
    `(.pid (java.lang.ProcessHandle/current))
    `(let [runtime# (ManagementFactory/getRuntimeMXBean)
           jvm# (.get (doto (.getDeclaredField (class runtime#) "jvm")
                        (.setAccessible true))
                      runtime#)]
       (.invoke (doto (.getDeclaredMethod (class jvm#) "getProcessId"
                                          (into-array Class []))
                  (.setAccessible true))
                jvm# (object-array [])))))

(defn- get-self-pid
  "Returns the process ID of the current JVM process."
  []
  (get-self-pid*))

#_(get-self-pid)

(defn- mk-vm [pid]
  (let [vm-class (get-virtualmachine-class)
        method (.getDeclaredMethod vm-class "attach" (into-array Class [String]))]
    (.invoke method nil (object-array [(str pid)]))))

(defmacro ^:private load-agent-and-detach
  "Call `VirtualMachine.loadAgent` and `VirtualMachine.detach` for the given agent
  jar. This macro expands to either reflective or non-reflective call, depending
  whether VirtualMachine class is available at compile time (on JDK9+)."
  [vm agent-jar]
  (let [vm-sym (if (try (resolve 'com.sun.tools.attach.VirtualMachine)
                        (catch ClassNotFoundException _))
                 (with-meta (gensym "vm") {:tag 'com.sun.tools.attach.VirtualMachine})
                 (gensym "vm"))]
    `(let [~vm-sym ~vm]
       (do (.loadAgent ~vm-sym ~agent-jar)
           (.detach ~vm-sym)))))

(def ^:private jamm-agent-loaded
  (delay (load-agent-and-detach (mk-vm (get-self-pid)) extracted-jamm-jar)
         true))

;;;; Public API

(defn meter-builder []
  @jamm-agent-loaded
  (let [mm-class (Class/forName "org.github.jamm.MemoryMeter")
        builder (.getDeclaredMethod mm-class "builder" (into-array Class []))]
    (.invoke builder nil (object-array 0))))

(def ^:private memory-meter
  (delay (.build (meter-builder))))

(defn- convert-to-human-readable
  "Taken from http://programming.guide/java/formatting-byte-size-to-human-readable-format.html."
  [bytes]
  (let [unit 1024]
    (if (< bytes unit)
      (str bytes " B")
      (let [exp (int (/ (Math/log bytes) (Math/log unit)))
            pre (nth "KMGTPE" (dec exp))]
        (format "%.1f %siB" (/ bytes (Math/pow unit exp)) pre)))))

#_(convert-to-human-readable 512)
#_(convert-to-human-readable 10e8)

(defn measure
  "Measure the memory usage of the `object`. Return a human-readable string.

  :shallow - if true, count only the object header and its fields, don't follow
             object references
  :bytes   - if true, return a number of bytes instead of a string
  :meter   - custom org.github.jamm.MemoryMeter object"
  [object & {:keys [debug shallow bytes meter]}]
  (let [m (or meter @memory-meter)
        byte-count (if shallow
                     (.measure m object)
                     (.measureDeep m object))]
    (if bytes
      byte-count
      (convert-to-human-readable byte-count))))

#_(measure (vec (repeat 100 "hello")) :debug true)
#_(measure (object-array (repeatedly 1000 (fn [] (String. "foobarbaz")))) :bytes true)
