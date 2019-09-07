(ns zeal.ui.state
  (:require [uix.core.alpha :as uix]
            [zeal.util :as u]))

(def ^:dynamic *init-state* {})

(defn id-fn [{:keys [crux.db/id crux.tx/tx-id]}]
  (when id
   (cond-> (str id)
     tx-id (str "__" tx-id))))

(defonce db (atom (merge {:full-command ""
                          :search-query ""}
                         #?(:cljs (js->clj js/__initState :keywordize-keys true)))))

(defn db-assoc [& args]
  (apply swap! db assoc args))

(defn db-assoc-in [& args]
  (apply swap! db assoc-in args))

(defn db-update [& args]
  (apply swap! db update args))

(defn <sub
  ([f] (<sub db f))
  ([db f]
   #?(:clj
      nil
      :cljs
      (let [state* (uix/state #(f @db))] ;; this shit is not working
        (uix/effect!
         (fn []
           (let [id            (random-uuid)
                 unsub?        (atom false)
                 check-updates (fn [o n]
                                 (let [of (f o)
                                       nf (f n)]
                                   (when (and (false? ^boolean @unsub?) (not= nf of))
                                     (-reset! state* nf))))]
             (add-watch db id #(check-updates %3 %4))
             (check-updates nil @db)
             #(do
                (reset! unsub? true)
                (remove-watch db id))))
         [f])
        @state*))))

(defn on-change [f cb]
  #?(:clj
     nil
     :cljs
     (uix/effect!
      (fn []
        (let [id            (random-uuid)
              unsub?        (atom false)
              check-updates (fn [o n]
                              (let [of (f o)
                                    nf (f n)]
                                (when (and (false? ^boolean @unsub?) (not= nf of))
                                  (cb nf))))]
          (add-watch db id #(check-updates %3 %4))
          (check-updates nil @db)
          #(do
             (reset! unsub? true)
             (remove-watch db id))))
      [f])))

(defn normalize [coll]
  (u/project-as-keys (sorted-map) id-fn (u/ensure-vec coll)))

;; could spec this out instead
(defn entity? [x]
  (boolean (and (map? x) (id-fn x))))

(defn entities? [x]
  (and (or (set? x) (sequential? x)) (every? entity? x)))

(defn entity-or-entitites? [x]
  (or (entity? x) (entities? x)))

(defn add*
  ([db ent-or-coll]
   (assert entity-or-entitites? ent-or-coll)
   (into db (normalize ent-or-coll)))
  ([db k val-ent-or-ents]
   (let [entity?   (entity? val-ent-or-ents)
         entities? (when-not entity? (entities? val-ent-or-ents))]
     (if (or entity? entities?)
       (let [normd       (normalize val-ent-or-ents)
             ref-or-refs (cond
                           entity? (id-fn val-ent-or-ents)
                           entities? (-> normd keys vec))]
         (-> db
             (into normd)
             (assoc k ref-or-refs)))
       (assoc db k val-ent-or-ents))))
  ([db k val & k-coll-pairs]
   (assert (even? (count k-coll-pairs)))
   (reduce (fn [db [k v]] (add* db k v))
           (add* db k val)
           (partition 2 k-coll-pairs))))

(defn add
  ([coll] (swap! db add* coll))
  ([k coll] (swap! db add* k coll))
  ([k coll & k-coll-pairs] (swap! db #(apply add* % k coll k-coll-pairs))))

(defn get*
  ([db k]
   (when-let [v (get db k)]
     (if (vector? v)
       (mapv #(get db %) v)
       (get db v v))))
  ([db k not-found]
   (or (get* db k) not-found)))

(defn get-in*
  ([db [k & ks]]
   (get-in (get* db k) ks))
  ([db ks not-found]
   (or (get-in* db ks) not-found)))

(defn db-get [k]
  (get* @db k))

(defn db-get-in [[k & ks]]
  (get-in (get* @db k) ks))

(defn <get
  ([k] (<get k identity))
  ([k f] (<sub (fn [db] (f (get* db k))))))

(defn <get-in
  ; not recursive
  [[k & ks]] (<get k (fn [ent] (get-in ent ks))))


(comment
 (-> {}
     (add* #_#_:search-results (into []
                                     (map #(do {id-fn %}))
                                     (range 10)) ; ents
      :user {id-fn :meow}               ; ent
      :garb "sasdasd")                  ; val

     ;(get* :search-results)
     ;(get* :user)
     ;(get* :garb)

     ))
