(ns zeal.ui.views
  (:require [uix.dom.alpha :as uix.dom]
            [uix.core.alpha :as uix]
            [zeal.ui.macros :as m]
            #?(:cljs [zeal.ui.talk :as t])
            [zeal.ui.state :as st :refer [<sub db-assoc db-assoc-in]]
            [clojure.core.async :refer [go go-loop <!]]
            [clojure.pprint :refer [pprint]]
            )
  )

#?(:cljs
   (set! cljs.pprint/*print-right-margin* 40))


(defn app []
  (let [search-query   (<sub :search-query)
        search-results (<sub :search-results)
        show-editor?   (<sub :show-editor?)
        snippet        (<sub (comp :snippet :editor))
        result         (<sub (comp :result :editor))]
    [:main.app
     [:div
      [:div.flex
       [:input {:value     search-query
                :on-change (fn [e]
                             (let [q (.. e -target -value)]
                               (db-assoc :search-query q)
                               #?(:cljs (t/send-search q #(db-assoc :search-results %)))))}]
       [:button
        {:on-click #(db-assoc :search-query ""
                              :search-results nil
                              :show-editor? true)}
        "new"]]
      (when search-results
        (for [{:keys [id date snippet result]} search-results]
          ^{:key date}
          [:div.flex.mb3.hover-bg-light-gray
           [:div
            (subs (str id) 0 8)]
           [:pre.bg-gray.white.ma0 snippet]
           [:pre.ma0 result]]))
      (when show-editor?
        [:div
         [:button {:on-click #(do
                                (println :EVAL snippet)
                                #?(:cljs
                                   (t/send-eval!
                                    snippet
                                    (fn [{r :result :as m}]
                                      (println :RETURN m (type r))
                                      (db-assoc-in [:editor :result] r)))))}
          "eval"]
         [:div.flex
          [:div "editor"
           [:pre
            {:on-input                       #(db-assoc-in [:editor :snippet]
                                                           (.. % -target -innerText))
             :content-editable               true
             :suppressContentEditableWarning true}
            snippet]]
          [:div "result"
           [:pre
            result
            ]]]])]]))


(defn document
  [{:as opts :keys [js styles links]}]
  [:html
   [:head
    (for [s styles]
      [:style {:type "text/css"} s])]
   (for [l links]
     [:link {:rel "stylesheet" :href l}])
   [:body.sans-serif
    [:div#root [app]]
    (for [{:keys [src script]} js]
      [:script (when src {:src src}) script])]])

