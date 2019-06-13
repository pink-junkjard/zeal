(ns zerpl.ui.views
  (:require [uix.dom.alpha :as uix.dom]
            [zerpl.ui.macros :as m]
            )
  )

(defn app []
  [:main.app
   "MEOW"
   ])

(defn document
  [{:as opts :keys [js styles links]}]
  [:html
   [:head
    (for [s styles]
      [:style {:type "text/css"} s])]
   (for [l links]
     [:link {:rel "stylesheet" :href l}])
   [:body
    [:div#root [app]]
    (for [{:keys [src script]} js]
      [:script (when src {:src src}) script])]])

