(ns zeal.ui.util.dom
  (:require [clojure.string :as str]
            [kitchen-async.promise :as p]
            #?(:cljs [applied-science.js-interop :as j])))

(defn mobile-device? []
  #?(:cljs
     (let [user-agent (str/lower-case (j/get-in js/window [:navigator :userAgent]))
           rxp        #"android|webos|iphone|ipad|ipod|blackberry|iemobile|opera mini"]
       (boolean (re-find rxp user-agent)))))

(defn copy-to-clipboard
  ([clipboard-text-fn] (copy-to-clipboard clipboard-text-fn (constantly nil)))
  ([clipboard-text-fn on-copied]
   #?(:cljs
      (p/then
       (clipboard-text-fn)
       (fn [txt]
         (p/then (j/call-in js/navigator [:clipboard :writeText] txt)
                 on-copied))))))

(defn read-clipboard-promise []
  #?(:cljs
     (j/call-in js/navigator [:clipboard :readText])))

(defn read-clipboard [on-content]
  #?(:cljs
     (p/then (read-clipboard-promise) on-content)))
