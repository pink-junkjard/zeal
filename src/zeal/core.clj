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
                  {:find  '[?e ?t]
                   :where '[[?e :name ?n]
                            [?e :snippet ?s]
                            [?e :result ?r]
                            [?e :time ?t]
                            [(zeal.core/some-strings-include? ?search-string ?n ?s ?r)]]
                   :args  [{:?search-string q-str}]
                   :limit    1000
                   :order-by '[[?t :desc]]})
        names    (filter :name res)
        no-names (remove :name res)]
    (vec (concat names no-names))))

;;; eval

(defn search
  "Takes a query, a collection of maps and keys to strings in each map to search.
  Returns a coll with maps that matched ordered by date."
  [q coll ks]
  (if (empty? q)
    nil
    (let [q (str/lower-case q)]
      (->> coll
           (filter
            (fn [m]
              (some
               (fn [k]
                 (let [text (k m)]
                   (cond-> text
                     (not (string? text)) pr-str
                     true (-> str/lower-case (str/includes? q))))) ks)))
           (sort-by :time >)
           vec))))

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
  (try
    (let [execd (-> exec-ent
                    (merge
                     {:time   (.getTime (Date.))
                      :result (eval/do-eval-string snippet)}
                     (when-not name {:name false}))
                    db/add-id-if-none-exists)]
      (db/put! [execd] {:blocking? true})
      execd)
    (catch Exception e
      (println e))))


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
