(ns zeal.eval.core
  (:require [zeal.eval.sandbox :as sb]))

(def set-ns
  (pr-str
   '(in-ns 'zeal.eval.sandbox)))

(defn do-eval-string [s]
  (-> (str set-ns \newline \newline s)
      load-string))

(defn- takes-args? [f]
  (->> f meta :arglists (some (comp pos? count)) boolean))

(defn- fn-or-var-of-fn? [x]
  (or (fn? x) (and (var? x) (fn? (var-get x)))))

(defn do-eval-exec-ent [{:as exec-ent :keys [snippet args]}]
  (binding [sb/*snippet* exec-ent]
    (let [ret (do-eval-string snippet)]
      (if-not (fn-or-var-of-fn? ret)
        ret
        (let [takes-args? (takes-args? ret)]
          (println args takes-args?)
          (cond
            (and args takes-args?) (ret args)
            takes-args? (var-get ret)
            :else (ret)))))))
