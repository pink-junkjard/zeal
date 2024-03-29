(ns zeal.snippet
  (:require
   [mount.core :as mount :refer [defstate]]
   [clojure.string :as str]
   [zeal.db :as db]
   [zeal.state]
   [zeal.eval.core :as eval]
   [zprint.core :as zp])
  (:import
   (java.util Date)))

(defn some-strings-include? [q & strings]
  (let [q (str/lower-case q)]
    (boolean (some #(and (string? %)
                         (str/includes? (str/lower-case %) q)) strings))))

(defn search-snippets [q-str]
  (let [res      (db/q-entities
                  {:find     '[?e ?t]
                   :where    '[[?e :name ?n]
                               [?e :snippet ?s]
                               [?e :result ?r]
                               [?e :result-string ?rs]
                               [?e :time ?t]
                               [(zeal.snippet/some-strings-include? ?search-string ?n ?s ?r ?rs)]]
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
    (search-snippets q)))

(defn valid-edn [x]
  (let [s (pr-str x)]
    (try
      (read-string s)
      [x s]
      (catch Exception _
        [nil s]))))

(defn add-author [ent]
  (->> zeal.state/*session*
       :user
       :crux.db/id
       (assoc ent :author)))

(defn pretty-result-string [[evald-edn evald-str]]
  (if evald-edn
    (if (string? evald-edn)
      evald-edn
      (zp/zprint-str evald-edn 50))
    evald-str))

(defn eval-and-log-exec-ent! [{:keys [name snippet] :as exec-ent}]
  (let [exec-ent
        (-> exec-ent
            (merge
             {:time (.getTime (Date.))}
             (when-not name {:name false}))
            db/add-id-if-none-exists
            add-author)
        ret-exec-ent
        (try
          (let [[evald-edn evald-str :as eval-edn+str]
                (-> exec-ent
                    eval/do-eval-exec-ent
                    valid-edn)
                edn-meta  (some-> evald-edn meta (select-keys [:renderer :copy?]))
                evald-edn (cond-> evald-edn (some? edn-meta) (with-meta nil))
                execd     (merge
                           exec-ent
                           {:result        (or evald-edn evald-str)
                            :result-string (pretty-result-string eval-edn+str)}
                           edn-meta)]
            execd)
          (catch Exception e
            ;; Return error as string
            (merge
             exec-ent
             {:result        (pr-str e)
              :result-string false})))]
    (db/put! [ret-exec-ent] {:blocking? true})
    ret-exec-ent))

(comment
 ;; DANGER WIPE DB
 (let [all (db/q '{:find  [?e]
                   :where [[?e :crux.db/id]]})]
   (crux.api/submit-tx
    db/node
    (into []
          (comp
           (map first)
           (map (fn [eid]
                  [:crux.tx/evict eid])))
          all)))
 )
