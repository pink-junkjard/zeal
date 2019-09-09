(ns zeal.ui.util.geolocation
  #?(:cljs (:require [applied-science.js-interop :as j])))

(defn location [cb]
  #?(:cljs
     (when-let [gl (j/get js/navigator :geolocation)]
       (j/call gl :getCurrentPosition
               (fn [pos]
                 (let [coords (j/get pos :coords)]
                   (cb
                    {:latitude (j/get coords :latitude)
                     :longitude (j/get coords :longitude)})))))))
