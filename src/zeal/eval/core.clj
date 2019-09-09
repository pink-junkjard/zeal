(ns zeal.eval.core
  (:require [zeal.eval.sandbox :as sb]))

(def set-ns
  (pr-str
   '(in-ns 'zeal.eval.sandbox)))

(defn do-eval-string [s]
  (-> (str set-ns \newline \newline s)
      load-string))

(defn do-eval-exec-ent [{:as exec-ent :keys [snippet]}]
  (binding [sb/*snippet* exec-ent]
   (do-eval-string snippet)))
