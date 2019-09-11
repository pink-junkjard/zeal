(ns zeal.ui.util.dom
  (:require [clojure.string :as str]
            #?(:cljs [applied-science.js-interop :as j])))

(defn mobile-device? []
  #?(:cljs
   (let [user-agent (str/lower-case (j/get-in js/window [:navigator :userAgent]))
         rxp        #"android|webos|iphone|ipad|ipod|blackberry|iemobile|opera mini"]
     (boolean (re-find rxp user-agent)))))
