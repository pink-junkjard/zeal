(ns zeal.eval.util.deps
  (:refer-clojure :exclude [add-classpath])
  (:require [dynapath.util :as dp]
            [clojure.java.io :as io]
            [clojure.tools.deps.alpha.repl :as deps.repl]
            [clojure.tools.deps.alpha.libmap :as lm]))

; from https://github.com/lambdaisland/kaocha/blob/master/src/kaocha/classpath.clj

(defn ensure-compiler-loader
  "Ensures the clojure.lang.Compiler/LOADER var is bound to a DynamicClassLoader,
  so that we can add to Clojure's classpath dynamically."
  []
  (when-not (bound? Compiler/LOADER)
    (.bindRoot Compiler/LOADER (clojure.lang.DynamicClassLoader. (clojure.lang.RT/baseLoader)))))

(defn- classloader-hierarchy
  "Returns a seq of classloaders, with the tip of the hierarchy first.
   Uses the current thread context ClassLoader as the tip ClassLoader
   if one is not provided."
  ([]
   (ensure-compiler-loader)
   (classloader-hierarchy (deref clojure.lang.Compiler/LOADER)))
  ([tip]
   (->> tip
        (iterate #(.getParent ^ClassLoader %))
        (take-while boolean))))

(defn- modifiable-classloader?
  "Returns true iff the given ClassLoader is of a type that satisfies
   the dynapath.dynamic-classpath/DynamicClasspath protocol, and it can
   be modified."
  [cl]
  (dp/addable-classpath? cl))

(defn add-classpath
  "A corollary to the (deprecated) `add-classpath` in clojure.core. This implementation
   requires a java.io.File or String path to a jar file or directory, and will attempt
   to add that path to the right classloader (with the search rooted at the current
   thread's context classloader)."
  ([jar-or-dir classloader]
   (if-not (dp/add-classpath-url classloader (.toURL (.toURI (io/file jar-or-dir))))
     (throw (IllegalStateException. (str classloader " is not a modifiable classloader")))))
  ([jar-or-dir]
   (let [classloaders (classloader-hierarchy)]
     (if-let [cl (filter modifiable-classloader? classloaders)]
       ;; Add to all classloaders that allow it. Brute force but doesn't hurt.
       (run! #(add-classpath jar-or-dir %) cl)
       (throw (IllegalStateException. (str "Could not find a suitable classloader to modify from "
                                           classloaders)))))))

(def cl
  (let [cl (.getContextClassLoader (Thread/currentThread))]
    (.setContextClassLoader (Thread/currentThread) (clojure.lang.DynamicClassLoader. cl))
    cl))

(defn- add-paths []
  (doseq [p (mapcat :paths (vals (lm/lib-map)))]
    (add-classpath p)))

(defn add-lib [lib coord]
  (let [ret (deps.repl/add-lib lib coord)]
    (when ret (add-paths))
    ret))
