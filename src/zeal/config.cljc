(ns zeal.config
  (:refer-clojure :exclude [get])
  #?(:cljs (:require-macros [zeal.config :refer [get-config-macro]])))

(defonce get-config
  #?(:clj
     (read-string (slurp "config.edn"))))

#?(:clj
   (defmacro get-config-macro []
     get-config))

(def config (get-config-macro))

#?(:clj
   (defmacro get [k]
     (let [v (clojure.core/get get-config k)]
       (assert v (str "config value for " k " does not exist"))
       v)))
