(ns zeal.ui.views
  (:require [uix.dom.alpha :as uix.dom]
            [uix.core.alpha :as uix]
            [zeal.ui.macros :as m]
            [zeal.ui.state :as st :refer [<sub db-assoc db-assoc-in db-get db-get-in]]
            [clojure.core.async :refer [go go-loop <!]]
            [clojure.pprint :refer [pprint]]
            [zeal.ui.talk :as t]
            [zeal.eval.util :as eval.util]
            #?@(:cljs [[den1k.shortcuts :as sc :refer [global-shortcuts]]
                       [applied-science.js-interop :as j]
                       [goog.string :as gstr]])
            #?@(:cljs [["codemirror" :as CodeMirror]
                       ["codemirror/mode/clojure/clojure"]
                       ["codemirror/addon/edit/closebrackets"]
                       ["codemirror/addon/hint/show-hint.js"]
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
                   (.-fromTextArea CodeMirror)
                   (fn [node opts]
                     (CodeMirror. node opts)))
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
    [:div.w-50.h-100
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

(defn show-recent-results []
  (when (and (empty? (db-get :search-query)) (not (db-get :show-history?)))
    (t/send [:recent-exec-ents {:n 10}]
            #(db-assoc :search-results %))
    true))

(defn new-snippet-btn []
  [:button
   {:on-click #(do
                 (cm-set-value (db-get-in [:editor :snippet-cm]) new-snippet-text)

                 (db-assoc :search-query ""
                           :search-results nil
                           :show-history? false
                           :history nil
                           :history-ent nil
                           :exec-ent {:snippet new-snippet-text
                                      :name    false}))}
   "new"])

(defn search-input []
  (let [search-query (<sub :search-query)]
    [:input.outline-0
     {:ref       #(db-assoc :search-node %)
      :value     search-query
      :on-focus  #(show-recent-results)
      ;; better to have sth like clicked outside
      #_#_:on-blur (fn [_]
                     (when (and (empty? search-query) (not (db-get :show-history?)))
                       (db-assoc :search-results nil
                                 :show-history? false
                                 :history nil
                                 :history-ent nil))
                     )
      :on-change (fn [e]
                   (let [q (.. e -target -value)]
                     (db-assoc :search-query q)
                     (or (show-recent-results)
                         (if (db-get :show-history?)
                           (t/history (db-get :history-ent) #(db-assoc :history %))
                           (t/send-search q #(db-assoc :search-results %))))))}]))

(defn search-results []
  (let [search-query   (<sub :search-query)
        search-results (<sub :search-results)
        show-history?  (<sub :show-history?)
        history        (<sub :history)]
    (cond
      (or show-history? (not-empty search-results))
      [:div.ph2.overflow-auto
       {:style {:max-height :40%}}
       (for [{:as           exec-ent
              :keys         [time name snippet result]
              :crux.db/keys [id content-hash]
              :crux.tx/keys [tx-id]}
             (if show-history?
               history
               search-results)
             :let [name-id-or-hash (or name (subs (str (if show-history?
                                                         content-hash
                                                         id)) 0 7))]]
         [:div.flex.pv2.align-center.hover-bg-light-gray.pointer.ph1.hide-child.code
          {:key      (str name "-" id "-" tx-id)
           :style    {:max-height "3rem"}
           :on-click #(do (st/db-assoc :exec-ent exec-ent)
                          ;; setting directly instead of syncing editor with st-value-fn
                          ;; because when the editor value is reset on every change
                          ;; the caret moves to index 0
                          (cm-set-value (db-get-in [:editor :snippet-cm]) snippet))}
          [:div.w3.items-center.f7.outline-0.self-center.overflow-hidden
           ; to .truncate need to remove on focus and add back on blur
           {:contentEditable
            true
            :suppressContentEditableWarning
            true
            :on-blur
            (fn [e]
              (let [text (not-empty (.. e -target -innerText))]
                (set! (.. e -target -innerHTML) name-id-or-hash)
                (t/send [:merge-entity {:crux.db/id id
                                        :name       text}]
                        (fn [m]
                          (if (db-get :show-history?)
                            (t/history m #(db-assoc :history %))
                            (st/db-update :search-results
                                          (fn [res]
                                            (mapv (fn [{:as rm rid :crux.db/id}]
                                                    (if (= rid id)
                                                      m
                                                      rm))
                                                  res))))))))}
           name-id-or-hash]
          [:pre.w-50.f6.ma0.ml3.self-center.overflow-hidden
           {:style {:white-space :pre-wrap
                    :word-break  :break-all
                    :max-height  :3rem}}
           snippet]
          [:pre.w-50.f6.ma0.ml3.self-center.overflow-hidden
           {:style {:white-space :pre-wrap
                    :word-break  :break-all
                    :max-height  :3rem}}
           result]
          [:i.pointer.fas.fa-history.flex.self-center.pa1.br2.child.w2.tc
           {:class    (if show-history?
                        "bg-gray white hover-bg-white hover-black"
                        "hover-bg-white")
            :on-click (fn [e]
                        (if show-history?
                          (if (empty? search-query)
                            (t/send [:recent-exec-ents {:n 10}]
                                    #(db-assoc :search-results %
                                               :show-history? false))
                            (t/send-search search-query
                                           #(db-assoc :search-results %
                                                      :show-history? false)))

                          (t/history exec-ent
                                     #(db-assoc :history %
                                                :history-ent exec-ent
                                                :show-history? true))))}]])]
      (not-empty search-query)
      "No results")))

(def exec-ent-dep-result-path [:exec-ent-dep :result])

(defn deps-completion [cm-inst option]
  #?(:cljs
     (let [cursor
           (.getCursor cm-inst)

           token
           (-> cm-inst
               (j/call :getTokenAt cursor))

           {:keys [ch line]} (j/lookup cursor)
           {:keys [start]} (j/lookup token)

           word
           (-> (j/get token :string) gstr/trim)

           assoc-dep-path
           (fn [v]
             (db-assoc-in exec-ent-dep-result-path v))

           ents->cm-list
           (fn [ents]
             (let [completions
                   (reduce
                    (fn [out {:keys [crux.db/id name result]}]
                      (let [dtext        (or name (subs (str id) 0 7))
                            replace-text (eval.util/dep-str-expr dtext id)
                            res          #js {:id          (str id)
                                              :result      result
                                              :hint        (fn [cm data completion]
                                                             (let [{:keys [from to]} (j/lookup data)]

                                                               (j/call cm :replaceRange
                                                                       replace-text
                                                                       from to "complete")
                                                               (j/call cm :markText
                                                                       from
                                                                       #js {:line (j/get from :line)
                                                                            :ch   (+ (j/get from :ch) (count replace-text))}
                                                                       #js {:className "bg-orange"
                                                                            :atomic    true})))
                                              :displayText dtext
                                              :text        replace-text}]
                        (j/push! out res)))
                    #js[]
                    ents)
                   ret
                   #js {:from (j/call CodeMirror :Pos line start)
                        :to   (j/call CodeMirror :Pos line ch)
                        :list completions}]

               (.on CodeMirror ret "pick"
                    #(assoc-dep-path nil))
               (.on CodeMirror ret "close"
                    #(assoc-dep-path nil))
               (.on CodeMirror ret "select"
                    (fn [sel _]
                      (assoc-dep-path (.-result sel))))

               ret))]
       (js/Promise.
        (fn [resolve]
          (if (empty? word)
            (t/send [:recent-exec-ents {:n 10}]
                    #(resolve (ents->cm-list %)))
            (t/send-search word
                           #(resolve (ents->cm-list %)))))))))

(defn snippet-editor []
  (let [snippet (<sub (comp :snippet :exec-ent))]
    [codemirror
     {:default-value
      (or snippet new-snippet-text)

      :on-cm
      #(db-assoc-in [:editor :snippet-cm] %)

      :parinfer?
      true

      :cm-opts
      {:keyboard-shortcuts
       {"Cmd-Enter"
        (fn [_cm]
          #?(:cljs
             (t/send-eval!
              (-> (db-get :exec-ent)
                  (update :snippet gstr/trim))
              (fn [{:as m :keys [snippet]}]
                (cm-set-value (db-get-in [:editor :snippet-cm]) snippet)
                (db-assoc :exec-ent m)
                (if (db-get :show-history?)
                  (t/history m #(db-assoc :history %))
                  (t/send-search (db-get :search-query)
                                 #(db-assoc :search-results %)))))))
        "Ctrl-S"
        (fn [cm-inst]
          #?(:cljs
             (.showHint
              cm-inst
              #js {:completeSingle false
                   :hint deps-completion})))
        }

       :on-changes
       (fn [cm _]
         (db-assoc-in [:exec-ent :snippet] (.getValue cm)))}}]))

(defn exec-result []
  (let [result (<sub (comp :result :exec-ent))]
    [codemirror
     {:default-value (str result)
      :st-value-fn   #(or
                       (get-in % exec-ent-dep-result-path)
                       (-> % :exec-ent :result))
      :cm-opts       {:readOnly true}}]))

(defn app []
  [:main.app
   [:div
    [:div.flex
     [search-input]
     [new-snippet-btn]]
    [search-results]]
   [:div.bt.mv3]
   [:div.flex.h-100
    [snippet-editor]
    [exec-result]]])


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
