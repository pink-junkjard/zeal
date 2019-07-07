(ns zeal.core
  (:require
   [mount.core :as mount :refer [defstate]]
   [clojure.string :as str]
   [zeal.db :as db]
   [zeal.eval.core :as eval])
  (:import
   (java.util Date)))

(defn some-strings-include? [q & strings]
  (let [q (str/lower-case q)]
    (boolean (some #(and (string? %)
                         (str/includes? (str/lower-case %) q)) strings))))

(defn crux-search [q-str]
  (let [res      (db/q-entity
                  {:find     '[?e ?t]
                   :where    '[[?e :name ?n]
                               [?e :snippet ?s]
                               [?e :result ?r]
                               [?e :result-string ?rs]
                               [?e :time ?t]
                               [(zeal.core/some-strings-include? ?search-string ?n ?s ?r ?rs)]]
                   :args     [{:?search-string q-str}]
                   :limit    1000
                   :order-by '[[?t :desc]]})
        names    (filter :name res)
        no-names (remove :name res)]
    (vec (concat names no-names))))

;;; eval


(defn recent-exec-ents [{:keys [n]}]
  (->> (db/q {:find     '[?e ?t]
              :where    '[[?e :snippet]
                          [?e :result]
                          [?e :time ?t]]
              :order-by '[[?t :desc]]
              :limit    n})
       (map (comp db/entity first))
       vec))

(defn search-eval-log [q]
  (if (empty? q)
    nil
    (crux-search q)))

(defn eval-and-log-exec-ent! [{:keys [name snippet] :as exec-ent}]
  (let [exec-ent
        (-> exec-ent
                     (merge
                      {:time (.getTime (Date.))}
                      (when-not name {:name false}))
                     db/add-id-if-none-exists)
        ret-exec-ent
                 (try
          (let [evald (eval/do-eval-string snippet)
                execd (merge
                       exec-ent
                       {:result        evald
                        :result-string (if (string? evald) false (pr-str evald))})]
            execd)
          (catch Exception e
            (merge
             exec-ent
             {:result (pr-str e)})))]
    (db/put! [ret-exec-ent] {:blocking? true})
    ret-exec-ent))


(comment
 (do
   (swap! eval-log empty)
   (doseq [test-k [:foo :bar :baz :bandoles :chicken]]
     (eval-and-log!
      `(zipmap (range 2) (repeat ~test-k)))))
 )

;(search "zipmap" @eval-log [:snippet :result])

(comment
 ;; DANGER WIPE DB
 (let [all (db/q '{:find  [?e]
                   :where [[?e :crux.db/id]]})]
   (crux.api/submit-tx
    db/crux
    (into []
          (comp
           (map first)
           (map (fn [m]
                  [:crux.tx/evict m])))
          all)))
 )
