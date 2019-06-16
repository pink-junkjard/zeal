(ns zeal.util)

(defn project
  ([f coll] (project {} f coll))
  ([to f coll] (into to (map f) coll)))

(defn project-as-keys
  [key-fn coll]
  (project {} (fn [x] [(key-fn x) x]) coll))

