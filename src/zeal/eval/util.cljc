(ns zeal.eval.util
  (:require [clojure.string :as str]))

(defn dep-str-expr [tag id]
  (str "(snippet-result \"" tag ":" id "\")"))

(defn dep-str->id [dep-str]
  (let [[_ id] (str/split dep-str #":")]
    id))
