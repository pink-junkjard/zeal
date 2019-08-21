(ns zeal.ui.util.visibility
  #?(:cljs (:require [applied-science.js-interop :as j])))

(defn bounding-client-rect
  "Returns a map of the bounding client rect of `elem`
   as a map with [:top :left :right :bottom :width :height]"
  [elem]
  (let [r      (.getBoundingClientRect elem)
        height (.-height r)]
    {:top    (.-top r)
     :bottom (.-bottom r)
     :left   (.-left r)
     :right  (.-right r)
     :width  (.-width r)
     :height height
     :middle (/ height 2)}))

(defn- parent-node [el]
  #?(:cljs
     (j/get el :parentNode)))

(defn visible?
  ([el]
   (visible? el {:container (parent-node el)}))
  ([el {:as opts :keys [container parent-level]}]
   #?(:cljs
      (let [container (if parent-level
                        (nth (iterate parent-node el) parent-level)
                        (or container (parent-node el)))
            {c-top :top c-bottom :bottom} (bounding-client-rect container)
            {el-top :top el-bottom :bottom} (bounding-client-rect el)]
        (and (>= el-top c-top) (<= el-bottom c-bottom))))))


