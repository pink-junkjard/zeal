(ns zeal.eval.core
  (:require [zeal.eval.sandbox]))

(def set-ns
  (pr-str
   '(in-ns 'zeal.eval.sandbox)))

(defn do-eval-string [s]
  (-> (str set-ns \newline \newline s)
      load-string))
