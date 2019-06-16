(ns zerpl.ui.state
  (:require [uix.core.alpha :as uix]))

(defonce db (atom {:search-query ""}))

(defn db-assoc [& args]
  (apply swap! db assoc args))

(defn db-assoc-in [& args]
  (apply swap! db assoc-in args))

(defn <sub [f]
  #?(:clj
     nil
     :cljs
     (let [state* (uix/state #(f @db))]
       (uix/effect!
        (fn []
          (let [id            (random-uuid)
                unsub?        (atom false)
                check-updates (fn [n]
                                (let [nf (f n)]
                                  (when (and (not ^boolean @unsub?) (not= @state* nf))
                                    (-reset! state* nf))))]
            (add-watch db id #(check-updates %4))
            (check-updates @db)
            #(do
               (reset! unsub? true)
               (remove-watch db id))))
        [f])
       @state*)))
