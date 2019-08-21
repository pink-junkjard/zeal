(ns zeal.ui.state
  (:require [uix.core.alpha :as uix]))

(def ^:dynamic *init-state* {})

(defonce db (atom (merge {:full-command ""
                          :search-query ""}
                         #?(:cljs (js->clj js/__initState :keywordize-keys true)))))

(defn db-get [k]
  (get @db k))

(defn db-get-in [path]
  (get-in @db path))

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
