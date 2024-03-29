(ns zeal.ui.views
  (:require [uix.dom.alpha :as uix.dom]
            [uix.core.alpha :as uix]
            [zeal.ui.state :as st :refer [<sub <get <get-in db-assoc db-assoc-in db-get db-get-in]]
            [clojure.core.async :refer [go go-loop <!]]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.set :as set]
            [kitchen-async.promise :as p]
            [zeal.ui.talk :as t]
            [zeal.eval.util :as eval.util]
            [zeal.ui.vega :as vega]
            [zeal.ui.util.select :as select]
            [zeal.util.react-js :refer [make-component]]
            [zeal.ui.util.geolocation :as gl]
            [zeal.ui.util.dom :as u.dom]
            #?@(:cljs [[den1k.shortcuts :as sc :refer [shortcuts global-shortcuts]]
                       [applied-science.js-interop :as j]
                       [goog.string :as gstr]])
            #?@(:cljs [["codemirror" :as CodeMirror]
                       ["codemirror/mode/clojure/clojure"]
                       ["codemirror/addon/edit/closebrackets"]
                       ["codemirror/addon/hint/show-hint.js"]
                       ["parinfer-codemirror" :as pcm]])))

(def app-background "hsla(27, 14%, 97%, 1)")

(defn add-device-location []
  (gl/location #(st/add :device-geolocation %)))

(defonce geolocation-loop
  ;; once a minute
  #?(:clj nil
     :cljs
          (do (add-device-location)
              (js/setInterval add-device-location 6e4))))

(defn focus-search []
  (when-let [search-node (db-get :search-node)]
    (.focus search-node)
    true))

#?(:cljs
   (global-shortcuts
    {"cmd+/" #(when (focus-search) false)
     ;"cmd+shift+z" #(println "redo")
     ;"cmd+z"       #(println "undo")
     }))

(def new-snippet-text
  ";; New Snippet
;; cmd+return to eval
;; ctrl+s to inline snippet results
;; eval `help` for info")

(defn update-child-opts [[tag x & more] f & args]
  (into (if (map? x)
          [tag (apply f x args)]
          [tag (apply f {} args) x])
        more))

(defn cm-set-value [cm s]
  #?(:cljs
     (let [s (str s)]
       (when (not= (j/call cm :getValue) s)
         (j/call (j/get cm :doc) :setValue s)))))

(defn cm-focus [cm]
  #?(:cljs
     (j/call cm :focus)))

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
    [:div
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
      (dissoc props :cm-opts :st-value-fn :on-cm :parinfer? :default-value))
     (when-not @cm-init?
       [:pre.f6.ma0.break-all.prewrap default-value])]))

(defn ->clipboard
  ([clipboard-text-fn child] (->clipboard clipboard-text-fn child child))
  ([clipboard-text-fn child on-copied-child]
   (let [copied? (uix/state false)
         child   (update-child-opts
                  child assoc :on-click
                  (fn [_]
                    #?(:cljs
                       (u.dom/copy-to-clipboard
                        clipboard-text-fn
                        (fn []
                          (reset! copied? true)
                          (js/setTimeout #(reset! copied? false) 1000))))))]
     (if-not @copied?
       child
       on-copied-child))))

(defn show-recent-results []
  (when (and (empty? (db-get :full-command)) (not (db-get :show-history?)))
    (t/send [:recent-exec-ents {:n 10}]
            #(st/add :search-results %))
    true))

(defn new-snippet-btn []
  [:button
   {:class    ["f7 link dim br2 ph3 pv2 dib b--light-gray ba h2 b"]
    :on-click #(do
                 (cm-set-value (db-get-in [:editor :snippet-cm]) new-snippet-text)

                 (st/add :full-command ""
                         :search-results nil
                         :show-history? false
                         :history nil
                         :history-ent nil
                         :exec-ent {:snippet new-snippet-text
                                    :name    false}))}
   "NEW"])

(defn on-search-result-select [{:as exec-ent :keys [snippet]}]
  (st/add :pre-select-exec-ent (or (db-get :pre-select-exec-ent)
                                   (db-get :exec-ent)))
  (st/add :exec-ent exec-ent
          :exec-result-error-state nil)
  ;; setting directly instead of syncing editor with st-value-fn
  ;; because when the editor value is reset on every change
  ;; the caret moves to index 0
  (some-> (db-get-in [:editor :snippet-cm]) (cm-set-value snippet))
  (focus-search))

(declare clear-command-input)

(defn on-search-result-pick
  ([exec-ent] (on-search-result-pick exec-ent true))
  ([exec-ent {:as opts :keys [clear-command-input? focus-editor? close-results?]}]
   (on-search-result-select exec-ent)
   (st/add :pre-select-exec-ent nil)
   (when clear-command-input? (clear-command-input))
   (when focus-editor? (some-> (db-get-in [:editor :snippet-cm]) cm-focus))
   (when close-results? (st/add :search-results nil
                                :show-history? false
                                :history nil
                                :history-ent nil))))

(defn on-search-results-unselect []
  (when-let [exec-ent (st/db-get :pre-select-exec-ent)]
    (on-search-result-select exec-ent)
    (st/add :pre-select-exec-ent nil)))

(def search-item-select
  (select/select {:on-item-select           on-search-result-select
                  :on-item-pick             on-search-result-pick
                  :on-unselect              on-search-results-unselect
                  :relative-container-level 2}))

(def possible-commands #{"-cb" ">" ">cb"})

(def commands-regex
  "Matches commands and surrounding whitespace."
  (re-pattern (str "\\s?(" (str/join "|" possible-commands) ")+\\s*")))

(def args-regex #"\:\??[\w-]+\s\:?(\w+\s?)+")
(def t "> hello :?arg1 5 :arg2 foo and more :a__ASAdsrg-3 meow :Ar_gthis4 :bar")

(re-seq args-regex t)

(def number-or-keyword-regex #"\:{1}\w+|[0-9]\.?")

(defn parse-arg-val
  "Reads keywords and numbers as edn and treats everything else as a string"
  [[v & _ :as vals]]
  (if (and (not-empty vals) (re-find number-or-keyword-regex v))
    #?(:clj  (read-string v)
       :cljs (cljs.reader/read-string v))
    (str/trim (str/join " " vals))))

(defn parse-args
  "parses \"> hello :?arg1 5 :arg2 foo :arg-3 meow and more :arg-this4 bar\"
  into {:?arg1 5, :arg2 \"foo\", :arg-3 \"meow and more\", :arg-this4 \"bar\"}"
  [s]
  (when-let [matches (not-empty (re-seq args-regex s))]
    matches
    (not-empty
     (into {}
           (comp
            (map first)
            (map (fn [match] (str/split match #" ")))
            (map (fn [[k & val-or-vals]]
                   [(keyword (subs k 1)) ; remove colon
                    (parse-arg-val val-or-vals)])))
           matches))))

(comment
 (parse-args "> hello :?arg1 5 :arg2 foo and more :a__ASAdsrg-3 meow :Ar_gthis4 :bar")
 )

(defn parse-commands [s]
  (when-let [words (not-empty (into #{} (str/split s #" ")))]
    (not-empty (set/intersection possible-commands words))))

(defn sans-commands+args [s]
  (some-> s
          (str/replace commands-regex "")
          (str/replace args-regex "")
          not-empty
          str/trim))

(defn parse-command-input [s]
  {:commands (parse-commands s)
   :args     (parse-args s)
   :query    (sans-commands+args s)})

(defn exec-ent->clipboard-text
  ([] (exec-ent->clipboard-text (db-get :exec-ent)))
  ([{:as exec-ent :keys [result renderer]}]
   #?(:cljs
      (case renderer
        :vega-lite
        (let [vega-view (db-get :renderer-vega-view)]
          ; returns a promise
          (j/call vega-view :toSVG))
        result))))

(defn exec-exec-ent
  ([] (exec-exec-ent (constantly nil)))
  ([on-result] (exec-exec-ent (db-get :exec-ent) on-result))
  ([exec-ent on-result]
   #?(:cljs
      (t/send-eval!
       (-> exec-ent
           (update :snippet gstr/trim))
       (fn [{:as m :keys [snippet]}]
         (cm-set-value (db-get-in [:editor :snippet-cm]) snippet)
         (st/add :exec-ent m)
         (on-result exec-ent)
         (if (db-get :show-history?)
           (t/history m #(st/add :history %))
           (t/send-search (db-get :search-query)
                          #(st/add :search-results %))))))))

(defn execute-commands [exec-ent commands]
  (doseq [cmd commands]
    (case cmd
      "-cb" (u.dom/copy-to-clipboard
             #(exec-ent->clipboard-text exec-ent)
             focus-search)
      ">cb" (exec-exec-ent
             exec-ent
             (fn [new-exec-ent]
               (u.dom/copy-to-clipboard
                #(exec-ent->clipboard-text new-exec-ent)
                focus-search)))
      ">" (exec-exec-ent exec-ent (constantly nil)))))

(defn clear-command-input []
  (st/add :search-results nil
          :full-command ""
          :search-query ""
          :show-history? false
          :history nil
          :history-ent nil))

(defn command-input []
  (let [full-command   (<get :full-command)
        search-results (<sub (fn [db] (if
                                       (:show-history? db)
                                        (st/get* db :history)
                                        (st/get* db :search-results))))
        {:keys [on-item-pick]} (search-item-select search-results)]
    [:div.flex
     [:input.outline-0.bn.br2.w5.f6.ph3.pv2.shadow-4.h2
      (merge
       #?(:cljs
          (shortcuts {"arrowdown" #(do (select/next search-item-select) false)
                      "arrowup"   #(do (select/previous search-item-select) false)
                      "enter"     (fn [e]
                                    (let [full-query (.. e -target -value)
                                          {:keys [args commands]} (parse-command-input full-query)
                                          exec-ent   (select/selected search-item-select)]
                                      (execute-commands (assoc exec-ent :args args) commands)
                                      (on-item-pick exec-ent {:close-results?       (some? args)
                                                              :focus-editor?        (and (nil? commands) (nil? args))
                                                              :clear-command-input? (nil? commands)})
                                      (.preventDefault e)))
                      "escape"    #(do
                                     (on-search-results-unselect)
                                     (clear-command-input))}))
       {:style     {:box-shadow    "rgba(0, 0, 0, 0.03) 0px 4px 3px 0px"
                    :padding-right 30}
        :ref       #(st/add :search-node %)
        :value     full-command
        :on-focus  #(show-recent-results)
        :on-change (fn [e]
                     (let [full-query (.. e -target -value)
                           {:as prsd :keys [query]} (parse-command-input full-query)]
                       (st/add :full-command full-query)
                       (st/add :search-query query)
                       (or (show-recent-results)
                           (if (db-get :show-history?)
                             (t/history (db-get :history-ent) #(st/add :history %))
                             (t/send-search query #(st/add :search-results %))))))})]
     (when (or (not-empty full-command) (not-empty search-results))
       [:span.flex.items-center
        {:style {:margin-left -27}}
        [:i.fas.fa-times-circle.mh1.gray.hover-black
         {:on-click #(st/add :search-results nil
                             :full-command ""
                             :search-query "")}]])]))

(defn search-results []
  (let [search-query     (<get :search-query)
        search-results   (<get :search-results)
        show-history?    (<get :show-history?)
        history          (<get :history)
        current-exec-ent (<sub (fn [db] (or (st/get* db :pre-select-exec-ent)
                                            (st/get* db :exec-ent))))
        {:keys [item-handlers-fn parent-handlers]} search-item-select
        _                (<sub (:state search-item-select) identity) ; sub to rerender
        select-idx       @search-item-select
        results?         (or show-history? (not-empty search-results))
        no-results?      (if show-history?
                           (empty? history)
                           (empty? search-results))
        show-no-results? (and no-results? (not-empty search-query))]
    [:div.overflow-auto
     {:style {:max-height (if (or results? show-no-results?)
                            :40%
                            0)
              :transition "max-height 0.3s ease-in-out"}}
     (cond
       results?
       [:div.ph2.mb2
        parent-handlers
        (for [[idx {:as           exec-ent
                    :keys         [time name snippet result result-string]
                    :crux.db/keys [id content-hash]}]
              (map-indexed vector (if show-history?
                                    history
                                    search-results))
              :let [name-id-or-hash   (or name (subs (str (if show-history?
                                                            content-hash
                                                            id)) 0 7))
                    current-exec-ent? (= exec-ent current-exec-ent)
                    selected?         (= idx select-idx)]]
          [:div.flex.mv1.pointer.pv2.ph1.hide-child.code
           (merge
            (item-handlers-fn exec-ent idx)
            {:key   (st/id-fn exec-ent)
             :style (merge {:max-height "4rem"}
                           (when current-exec-ent?
                             {:outline-color "gray"
                              :outline-style "dashed"}))
             :class [(when current-exec-ent? "outline")
                     (when selected? "bg-white")]})
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
                             (t/history m #(st/add :history %))
                             (st/add m))))))}
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
            (or result-string result)]
           [:i.pointer.fas.fa-history.flex.self-center.pa1.br2.child.w2.tc
            {:class    (if show-history?
                         "bg-gray white hover-bg-white hover-black"
                         "hover-bg-white")
             :on-click (fn [e]
                         (if show-history?
                           (if (empty? search-query)
                             (t/send [:recent-exec-ents {:n 10}]
                                     #(do
                                        (st/add :search-results %)
                                        (db-assoc :show-history? false)))
                             (t/send-search search-query
                                            #(do
                                               (st/add :search-results %)
                                               (db-assoc :show-history? false))))

                           (t/history exec-ent
                                      #(st/add :history %
                                               :history-ent exec-ent
                                               :show-history? true))))}]])]
       show-no-results?
       "No results")]))

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
           (fn [id]
             (st/add :exec-ent-dep id))

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
                                                                       #js {:className "bg-washed-yellow"
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
                      (assoc-dep-path (uuid (.-id sel)))))

               ret))]
       (js/Promise.
        (fn [resolve]
          (if (empty? word)
            (t/send [:recent-exec-ents {:n 10}]
                    #(resolve (ents->cm-list %)))
            (t/send-search word
                           #(resolve (ents->cm-list %)))))))))

(defn snippet-editor []
  (let [snippet (:snippet (<get :exec-ent))]
    [:div.w-50.ba.b--light-gray.br2.overflow-hidden.bg-white.dn.db-ns
     [codemirror
      {;:class ["w-50"]
       :default-value
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
              (do
                (st/add :full-command ""
                        :search-results nil
                        :show-history? false
                        :history nil
                        :history-ent nil)
                (t/send-eval!
                 (-> (db-get :exec-ent)
                     (dissoc :args)
                     (update :snippet gstr/trim))
                 (fn [{:as m :keys [snippet copy?]}]
                   (letfn [(editor-thunk []
                             (let [cm (db-get-in [:editor :snippet-cm])]
                               (cm-set-value cm snippet)
                               (cm-focus cm)))]
                     (if-not copy?
                       (editor-thunk)
                       (u.dom/copy-to-clipboard
                        #(exec-ent->clipboard-text m)
                        editor-thunk)))
                   (st/add :exec-ent m)
                   (if (db-get :show-history?)
                     (t/history m #(st/add :history %))
                     (t/send-search (db-get :full-command)
                                    #(st/add :search-results %))))))))
         "Ctrl-S"
         (fn [cm-inst]
           #?(:cljs
              (.showHint
               cm-inst
               #js {:completeSingle false
                    :hint           deps-completion})))
         }

        :on-changes
        (fn [cm _]
          (st/add :exec-ent (assoc (db-get :exec-ent) :snippet (.getValue cm))))}}]]))

(defn cm-renderer []
  (let [result        (<get-in [:exec-ent :result])
        result-string (<get-in [:exec-ent :result-string])]
    [codemirror
     {:default-value (str (or result-string result))
      :st-value-fn   (fn [db]
                       (let [exec-ent (or
                                       (st/get* db :exec-ent-dep)
                                       (st/get* db :exec-ent))]
                         (or (:result-string exec-ent)
                             (:result exec-ent))))
      :cm-opts       {:readOnly true}}]))

(defn hiccup-renderer []
  (let [result (<get-in [:exec-ent :result])]
    [:div
     {:ref (fn [node]
             (when node
               (uix.dom/render result node)))}]))

(defn vega-lite-renderer []
  (let [result (<get-in [:exec-ent :result])]
    [:div {:ref (fn [node]
                  (when node
                    (vega/init-vega-lite
                     node
                     {:spec     result
                      :renderer :canvas
                      :on-view  (fn [vega-view]
                                  (db-assoc :renderer-vega-view vega-view))})))}]))

(def renderers
  {:default   cm-renderer
   :hiccup    hiccup-renderer
   :vega-lite vega-lite-renderer})

(defn <current-renderer []
  #?(:clj  :default
     :cljs (<sub (fn [db] (st/get-in* db [:exec-ent :renderer] :default)))))

(defn renderer []
  (let [renderer (<current-renderer)]
    [(get renderers renderer)]))

(def error-boundary
  #?(:cljs
     (make-component
      "error-boundary"
      #js {:componentDidCatch (fn [error info]
                                (this-as this
                                  (j/call-in this
                                             [:props :onError]
                                             {:error error :info info})))
           :render            (fn []
                                (this-as this
                                  (j/get-in this [:props :children])))})))

(defn exec-result->clipboard-text
  "Inlining this fn for the clipboard feature causes remounts on every render."
  []
  #?(:cljs
     (let [{:keys [renderer result]} (db-get :exec-ent)]
       (case renderer
         :vega-lite
         (let [vega-view (db-get :renderer-vega-view)]
           ; returns a promise
           (j/call vega-view :toSVG))
         result))))

(defn exec-result []
  (let [rndr        (<current-renderer)
        error-state (<get :exec-result-error-state)
        on-error    (fn [err-info]
                      (st/add :exec-result-error-state err-info))]
    [:div.w-50-ns.w-100.h-100.ml1.ba.b--light-gray.br2.bg-white
     [:div.flex.justify-between.items-center
      {:style {:background app-background}}
      ; renderer tabs
      (into [:div.flex]
            (map (fn [r]
                   [:div.pointer.pv1.ph2.br2.br--top.hover-bg-light-gray
                    {:suppress-hydration-warning true
                     :class                      (when (= rndr r)
                                                   "bg-black-10")
                     :on-click                   #(t/send [:merge-entity
                                                           {:crux.db/id (:crux.db/id (db-get :exec-ent))
                                                            :renderer   r}]
                                                          (fn [m]
                                                            (st/add :exec-ent m)
                                                            (on-error nil)))}
                    (name r)]))
            (keys renderers))
      [->clipboard
       exec-ent->clipboard-text
       [:i.far.fa-copy.ph1.gray.hover-black.pointer.f6]
       [:i.fas.fa-check.f6.ph1]]]

     [:div.overflow-scroll.h-100
      (if-let [err (:error error-state)]
        [:div.pa2.bg-washed-red
         (str "Error using renderer " rndr)
         [:pre.break-all.prewrap (str err)]]
        #?(:cljs [:> error-boundary {:on-error on-error} [renderer]]))]]))

(defn logo []
  [:span.f2.pl3
   {:style {:font-family "'Faster One', cursive"}}
   [:a.link.black {:href "/"}
    "Z"]])

(defn app []
  [:main.app.h-100.flex.flex-column.overflow-hidden
   {:style {:background app-background}}
   [:div
    [:div.flex.justify-between.items-center.pv2.ph3
     [:div.w3
      [logo]]
     [command-input]
     [:div.w3.dn.di-ns
      [new-snippet-btn]]]
    [search-results]]
   [:div.flex.h-100.w-100.ph2
    [snippet-editor]
    [exec-result]]])


(defn document
  [{:as opts :keys [js styles links meta]}]
  [:html
   [:head
    (for [m meta]
      [:meta m])
    (for [s styles]
      [:style {:type "text/css"} s])
    (for [l links]
      [:link {:rel "stylesheet" :href l}])
    [:title "Zeal"]]
   [:body
    [:div#root.sans-serif [app]]
    (for [{:keys [src script]} js]
      [:script
       (if src
         {:src src}
         {:dangerouslySetInnerHTML {:__html script}})])]])
