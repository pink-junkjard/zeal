(ns zeal.ui.vega
  #?(:cljs
     (:require ["vega" :refer (parse View)]
               ["vega-lite" :refer (compile)]
               [applied-science.js-interop :as j])))

(defn set-data-url [js-spec url]
  #?(:cljs
     (j/assoc! js-spec "data" #js {:url url})))

(defn init-vega-js
  [{:as   opts
    :keys [signals data-url on-view-did-run renderer js-spec]
    :or   {renderer :svg}}]
  #?(:cljs
     (fn [node]
       (when node
         (cond-> js-spec data-url (set-data-url data-url))
         (let [view (doto (new View (parse js-spec))
                      (.renderer (name renderer)) ; svg or canvas
                      (.initialize node)
                      (.hover)
                      (.run))]
           (doseq [[n cb] signals]
             (.addSignalListener view (name n) (fn [_ datum] (cb datum))))
           (when on-view-did-run
             (.runAfter view on-view-did-run)))))))



(defn init-vega [opts]
  #?(:cljs
     (init-vega-js (update opts :js-spec clj->js))))

(defn init-vega-lite-js
  [opts]
  #?(:cljs
     (init-vega-js (update opts :js-spec #(.-spec (compile %))))))

(defn init-vega-lite
  ([{:as opts :keys [spec js-spec]}]
   #?(:cljs
      (let [minimal-schema
            {:$schema "https://vega.github.io/schema/vega-lite/v3.0.0-rc6.json"}]
        (init-vega-lite-js
         (cond-> opts
           (nil? js-spec) (assoc :js-spec (clj->js (merge minimal-schema spec))))))))
  ([node opts]
   ((init-vega-lite opts) node)))
