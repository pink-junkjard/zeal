(ns zeal.eval.sandbox
  "Stub ns for eval'ing code. See zeal.eval.core."
  (:require [clojure.tools.deps.alpha.repl :refer [add-lib]]))

(def help
  "Dynamic Deps
    - run `(add-lib 'clj-http {:mvn/version \"3.10.0\"})`")

(defn snippet-result [dep-str]
  (let [id (zeal.eval.util/dep-str->id dep-str)
        {:keys [result]} (zeal.db/entity id)]
    result))
