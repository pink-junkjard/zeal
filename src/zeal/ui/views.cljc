(ns zeal.ui.views
  (:require [uix.dom.alpha :as uix.dom]
            [uix.core.alpha :as uix]
            [zeal.ui.macros :as m]
            [zeal.ui.state :as st :refer [<sub db-assoc db-assoc-in db-get db-get-in]]
            [clojure.core.async :refer [go go-loop <!]]
            [clojure.pprint :refer [pprint]]
            [zeal.ui.talk :as t]
            #?@(:cljs [[den1k.shortcuts :as sc :refer [global-shortcuts]]])
            #?@(:cljs [["codemirror" :as cm]
                       ["codemirror/mode/clojure/clojure"]
                       ["codemirror/addon/edit/closebrackets"]
                       ["parinfer-codemirror" :as pcm]])))

#?(:cljs
   (global-shortcuts
    {"cmd+/" #(when-let [search-node (db-get :search-node)]
                (.focus search-node)
                false)
     ;"cmd+shift+z" #(js/console.log "redo")
     ;"cmd+z"       #(js/console.log "undo")
     }))

(def new-snippet-text
  ";; New Snippet\n;; cmd+return to eval\n;; eval `help` for info")
(defn cm-set-value [cm s]
  (.setValue (.-doc cm) (str s)))

(defn init-parinfer [cm]
  #?(:cljs (pcm/init cm)))

(defn init-codemirror
  [{:as opts :keys [node on-cm from-textarea? on-change on-changes keyboard-shortcuts]}]
  #?(:cljs
     (let [cm-fn (if from-textarea?
                   (.-fromTextArea cm)
                   (fn [node opts]
                     (cm. node opts)))
           cm    (cm-fn node
                        (clj->js
                         (merge {:mode              "clojure"
                                 :autoCloseBrackets true}
                                (dissoc opts
                                        :node-ref :on-cm :from-textarea?
                                        :on-change :on-changes :keyboard-shortcuts))))]
       (when on-change
         (.on cm "change" on-change))
       (when on-changes
         (.on cm "changes" on-changes))
       (when keyboard-shortcuts
         (.setOption cm "extraKeys" (clj->js keyboard-shortcuts)))
       (on-cm cm))))


(defn codemirror [{:as props :keys [default-value cm-opts st-value-fn on-cm parinfer?]}]
  (let [node     (uix/ref)
        cm       (uix/ref)
        cm-init? (uix/state false)]
    (when st-value-fn
      (st/on-change st-value-fn #(cm-set-value @cm %)))
    [:div.w-50.h4
     (merge
      {:ref #(when-not @node
               (reset! node %)
               (init-codemirror
                (merge {:node         %
                        :on-cm        (fn [cm-instance]
                                        (reset! cm cm-instance)
                                        (reset! cm-init? true)
                                        (when parinfer? (init-parinfer cm-instance))
                                        (when on-cm (on-cm cm-instance)))
                        :value        default-value
                        :lineWrapping true
                        :lineNumbers  false}
                       cm-opts)))}
      (dissoc props :cm-opts :st-value-fn :on-cm :parinfer?))
     (when-not @cm-init?
       [:pre.f6.ma0.break-all.prewrap default-value])]))

(defn app []
  (let [search-query   (<sub :search-query)
        search-results (<sub :search-results)
        show-history?  (<sub :show-history?)
        history        (<sub :history)
        snippet        (<sub (comp :snippet :exec-ent))
        result         (<sub (comp :result :exec-ent))]
    [:main.app
     [:div                              ;search
      [:div.flex
       [:input.outline-0
        {:ref       #(db-assoc :search-node %)
         :value     search-query
         :on-change (fn [e]
                      (let [q (.. e -target -value)]
                        (db-assoc :search-query q)
                        (t/send-search q #(db-assoc :search-results %))))}]
       [:button
        {:on-click #(do
                      (cm-set-value (db-get-in [:editor :snippet-cm]) new-snippet-text)

                      (db-assoc :search-query ""
                                :search-results nil
                                :exec-ent {:snippet new-snippet-text
                                           :name    false}))}
        "new"]]
      (cond
        (or show-history? (and (not-empty search-results) (not-empty search-query)))
        [:div.ph2.overflow-auto
         {:style {:max-height :40%}}
         (for [{:as   exec-ent
                :keys [crux.db/id time name snippet result]}
               (if show-history?
                 history
                 search-results)]
           [:div.flex.pv2.align-center.overflow-hidden.hover-bg-light-gray.pointer.ph1.hide-child
            {:key      (str name "-" id "-" time)
             :style    {:max-height "3rem"}
             :on-click #(do (st/db-assoc :exec-ent exec-ent)
                            ;; setting directly instead of syncing editor with st-value-fn
                            ;; because when the editor value is reset on every change
                            ;; the caret moves to index 0
                            (cm-set-value (db-get-in [:editor :snippet-cm]) snippet))}
            [:div.w3.flex.items-center.f7.overflow-hidden
             {:contentEditable                true
              :suppressContentEditableWarning true
              :on-blur                        #(let [text (not-empty (.. % -target -innerText))]
                                                 (t/send [:merge-entity {:crux.db/id id
                                                                         :name       text}]
                                                         (fn [m]
                                                           ;; todo better to have a norm'd db here
                                                           (st/db-update :search-results
                                                                         (fn [res]
                                                                           (mapv (fn [{:as rm rid :crux.db/id}]
                                                                                   (if (= rid id)
                                                                                     m
                                                                                     rm))
                                                                                 res)))
                                                           (db-assoc :exec-ent m))))}
             (or name (subs (str id) 0 8))]
            ;[:pre.bg-gray.white.ma0 snippet]
            [:pre.w-50.f6.ma0.ml3
             {:style {:white-space :pre-wrap
                      :word-break  :break-all}}
             snippet]
            [:pre.w-50.f6.ma0.ml3
             {:style {:white-space :pre-wrap
                      :word-break  :break-all}}
             result]
            [:i.pointer.fas.fa-history.flex.self-center.pa1.br2.child.w2.tc
             {:class    (if show-history?
                          "bg-gray white hover-bg-white hover-black"
                          "hover-bg-white")
              :on-click (fn [_]
                          (if show-history?
                            (t/send-search search-query
                                           #(db-assoc :search-results %
                                                      :show-history? false))
                            (t/history exec-ent
                                       #(db-assoc :history %
                                                  :show-history? true))))}]])]
        (not-empty search-query)
        "No results")]
     [:div.bt.mv3]
     [:div.flex
      [codemirror
       {:default-value (or snippet new-snippet-text)
        :on-cm         #(db-assoc-in [:editor :snippet-cm] %)
        :parinfer?     true
        :cm-opts       {:keyboard-shortcuts
                        {"Cmd-Enter"
                         (fn [_cm]
                           (t/send-eval!
                            (db-get :exec-ent)
                            (fn [m]
                              (db-assoc :exec-ent m)
                              (if (db-get :show-history?)
                                (t/history m #(db-assoc :history %))
                                (t/send-search (db-get :search-query)
                                               #(db-assoc :search-results %)))
                              )))}

                        :on-changes
                        (fn [cm _]
                          (db-assoc-in [:exec-ent :snippet] (.getValue cm)))}}]
      [codemirror
       {:default-value (str result)
        :st-value-fn   (comp :result :exec-ent)
        :cm-opts       {:readOnly true}}]]]))


(defn document
  [{:as opts :keys [js styles links]}]
  [:html
   [:head
    (for [s styles]
      [:style {:type "text/css"} s])]
   (for [l links]
     [:link {:rel "stylesheet" :href l}])
   [:body
    [:div#root.sans-serif [app]]
    (for [{:keys [src script]} js]
      [:script (when src {:src src}) script])]])
