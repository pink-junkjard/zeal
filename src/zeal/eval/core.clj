(ns zeal.eval.core
  (:require [zeal.eval.util]))

(def set-ns
  (pr-str
   '(load-file "src/zeal/eval/sandbox.clj")
   '(in-ns 'zeal.eval.sandbox)))

(defn do-eval-string [s]
  (-> (str set-ns \newline \newline s)
      load-string
      pr-str))
