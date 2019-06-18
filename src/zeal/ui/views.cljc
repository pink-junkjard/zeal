(ns zeal.ui.views
  (:require [uix.dom.alpha :as uix.dom]
            [uix.core.alpha :as uix]
            [zeal.ui.macros :as m]
            [zeal.ui.state :as st :refer [<sub db-assoc db-assoc-in db-get db-get-in]]
            [clojure.core.async :refer [go go-loop <!]]
            [clojure.pprint :refer [pprint]]
            #?@(:cljs [[zeal.ui.talk :as t]
                       [den1k.shortcuts :as sc :refer [global-shortcuts]]])
            #?@(:cljs [["codemirror" :as cm]
                       ["codemirror/mode/clojure/clojure"]])))

(global-shortcuts
 {"cmd+/" #(when-let [search-node (db-get :search-node)]
             (.focus search-node)
             false)
  ;"cmd+shift+z" #(js/console.log "redo")
  ;"cmd+z"       #(js/console.log "undo")
  })

(defn init-codemirror
  [{:as opts :keys [node cm-ref from-textarea? on-change on-changes keyboard-shortcuts]}]
  #?(:cljs
     (let [cm-fn (if from-textarea?
                   (.-fromTextArea cm)
                   (fn [node opts]
                     (cm. node opts)))
           cm    (cm-fn node
                        (clj->js
                         (merge {:mode "clojure"}
                                (dissoc opts
                                        :node-ref :cm-ref :from-textarea?
                                        :on-change :on-changes :keyboard-shortcuts))))]
       (when on-change
         (.on cm "change" on-change))
       (when on-changes
         (.on cm "changes" on-changes))
       (when keyboard-shortcuts
         (.setOption cm "extraKeys" (clj->js keyboard-shortcuts)))
       (reset! cm-ref cm)))
  )


(defn codemirror [{:as props :keys [default-value cm-opts st-value-fn]}]
  (let [node (uix/ref)
        cm   (uix/ref)]
    (when st-value-fn
      (st/on-change st-value-fn #(.setValue (.-doc @cm) (str %))))
    [:div.w-50.h4
     (merge
      {:ref #(when-not @node
               (reset! node %)
               (init-codemirror
                (merge {:node         %
                        :cm-ref       cm
                        :value        default-value
                        :lineWrapping true
                        :lineNumbers  false}
                       cm-opts)))}
      (dissoc props :cm-opts :st-value-fn))]))

(defn app []
  (let [search-query   (<sub :search-query)
        search-results (<sub :search-results)
        snippet        (<sub (comp :snippet :editor))
        result         (<sub (comp :result :editor))]
    [:main.app
     [:div
      [:div.flex
       [:input
        {:ref       #(db-assoc :search-node %)
         :value     search-query
         :on-change (fn [e]
                      (let [q (.. e -target -value)]
                        (db-assoc :search-query q)
                        #?(:cljs (t/send-search q #(db-assoc :search-results %)))))}]
       [:button
        {:on-click #(db-assoc :search-query ""
                              :search-results nil
                              :show-editor? true)}
        "new"]]
      (cond
        (and (not-empty search-results) (not-empty search-query))
        [:div.bg-light-gray.ph2.overflow-auto
         {:style {:max-height :40%}}
         (for [{:keys [id time snippet result]} search-results]
           [:div.flex.pv2.align-center.overflow-hidden.hover-bg-black-60.hover-white.pointer.ph1
            {:key      id
             :style    {:max-height "3rem"}
             :on-click #(st/db-update :editor
                                      assoc :snippet snippet :result result)}
            [:div.w3.flex.items-center.f7.overflow-hidden
             (subs (str id) 0 8)]
            ;[:pre.bg-gray.white.ma0 snippet]
            [:pre.w-50.ws-normal.f6.ma0.ml3
             {:style {:white-space :pre-wrap}}
             snippet]
            [:pre.w-50.ws-normal.f6.ma0.ml3
             {:style {:white-space :pre-wrap}}
             result]])]
        (not-empty search-query)
        "No results")
      [:div.flex
       [codemirror
        {:default-value (str snippet)
         :st-value-fn   (comp :snippet :editor)
         :cm-opts       {:keyboard-shortcuts
                         {"Cmd-Enter"
                          (fn [_cm]
                            #?(:cljs
                               (t/send-eval!
                                (db-get-in [:editor :snippet])
                                (fn [{r :result :as m}]
                                  (db-assoc-in [:editor :result] r)))))}

                         :on-changes
                         (fn [cm _]
                           (db-assoc-in [:editor :snippet] (.getValue cm)))}}]
       [codemirror
        {:default-value (str result)
         :st-value-fn   (comp :result :editor)
         :cm-opts       {:readOnly true}}]]]]))


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

