(ns zeal.ui.util.select
  (:refer-clojure :exclude [next])
  (:require [zeal.ui.util.visibility :as uvis]
            #?(:cljs [applied-science.js-interop :as j])))

(defn- advance [idx items dir]
  {:pre [(integer? dir)]}
  (let [next        (+ idx dir)
        items-count (count items)]
    (cond
      (neg? next) (dec items-count)
      (= next items-count) 0
      :else next)))

(defprotocol ISelect
  (next [_])
  (previous [_])
  (selected [_])
  (clear-override-idx [_])
  (set-idx-from-item [_ item])
  (set-override-idx-from-item [_ item]))

(defrecord Select [state on-item-select]
  #?@(:clj  [clojure.lang.IDeref
             (deref [_]
               (let [{:keys [override-idx idx]} @state]
                 (or override-idx idx)))
             clojure.lang.IFn
             (invoke [this items]
               (when (not= items (:items @state))
                 (swap! state assoc
                        :items (vec items)
                        :idx 0
                        :override-idx nil))
               this)]
      :cljs [IDeref
             (-deref [_]
                     (let [{:keys [override-idx idx]} @state]
                       (or override-idx idx)))
             IFn
             (-invoke [this items]
                      (when (not= items (:items @state))
                        (swap! state assoc
                               :items (vec items)
                               :idx 0
                               :override-idx nil)
                        (some-> items first on-item-select))
                      this)])

  ISelect
  (next [this]
    (let [{:keys [items override-idx]} @state
          k (if override-idx :override-idx :idx)]
      (swap! state update k advance items +1)
      (on-item-select (selected this))
      this))
  (previous [this]
    (let [{:keys [items override-idx]} @state
          k (if override-idx :override-idx :idx)]
      (swap! state update k advance items -1)
      (on-item-select (selected this))
      this))
  (selected [this]
    (get (:items @state) @this))
  (set-idx-from-item [this item]
    (let [{:keys [items idx]} @state
          next-idx (count (take-while #(not= item %) items))]
      (when (not= idx next-idx)
        (swap! state assoc :idx next-idx)
        (on-item-select (selected this)))
      this))
  (clear-override-idx [this]
    (swap! state assoc :override-idx nil)
    this)
  (set-override-idx-from-item [this item]
    (let [{:keys [items override-idx]} @state
          next-idx (count (take-while #(not= item %) items))]
      (when (not= override-idx next-idx)
        (swap! state assoc :override-idx next-idx)
        (on-item-select (selected this)))
      this)))

(def handler-ks [:on-item-select :on-item-pick :on-unselect])

(defn add-handlers
  [select {:as opts :keys [on-item-pick on-unselect relative-container-level]}]
  (merge
   (select-keys opts handler-ks)
   {:parent-handlers
    {:on-mouse-leave (fn [_]
                       (clear-override-idx select)
                       (on-unselect))}

    :item-handlers-fn
    (fn [item idx]
      {:ref            (fn [node] (when (and node (= idx @select))
                                    (when-not
                                     (uvis/visible? node {:parent-level relative-container-level})
                                      #?(:cljs
                                         (j/call node
                                                 :scrollIntoView
                                                 #js {:block  "nearest"
                                                      :inline "nearest"})))))
       :on-mouse-enter (fn [_]
                         (set-override-idx-from-item select item))
       :on-mouse-down  (fn [e]
                         (set-idx-from-item select item)
                         (on-item-pick item)
                         (.stopPropagation e))})}))

(defn- noop-handlers [ks]
  (zipmap ks (repeat (constantly nil))))

(defn select
  [{:as   opts
    :keys [items item idx-atom override-idx]
    :or   {idx-atom 0}}]
  (let [opts (merge (noop-handlers handler-ks) opts)
        sel  (map->Select (merge
                           opts
                           {:state (atom {:items        (vec items)
                                          :idx          idx-atom
                                          :override-idx override-idx})}))]
    (cond-> sel
      (and item (not override-idx)) (set-idx-from-item item)
      true (merge (add-handlers sel opts)))))
