(ns zeal.util.react-js
  #?(:cljs (:require [applied-science.js-interop :as j]
                     [react :as react])))

(defn make-component
  ([display-name m] (make-component display-name nil m))
  ([display-name construct m]
   #?(:cljs
      (let [cmp (fn [props context updater]
                  (cljs.core/this-as this
                    (react/Component.call this props context updater)
                    (when construct
                      (construct this))
                    this))]
        (j/extend! (.-prototype cmp) react/Component.prototype m)

        (when display-name
          (set! (.-displayName cmp) display-name)
          (set! (.-cljs$lang$ctorStr cmp) display-name)
          (set! (.-cljs$lang$ctorPrWriter cmp)
                (fn [this writer opt]
                  (cljs.core/-write writer display-name))))
        (set! (.-cljs$lang$type cmp) true)
        (set! (.. cmp -prototype -constructor) cmp)))))
