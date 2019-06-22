(ns zeal.core
  (:require
   [crux.api :as crux]
   [mount.core :as mount :refer [defstate]]
   [clojure.string :as str]
   [zeal.data :refer [data]]
   [zeal.eval.core :as eval])
  (:import                              ;(crux.api ICruxAPI)
   (java.util Date UUID)
   (crux.api ICruxAPI)))

;;;; Crux

(def crux-options
  {:kv-backend    "crux.kv.rocksdb.RocksKv"
   :event-log-dir "data/eventlog-1"
   :db-dir        "data/db-dir-1"})

(defstate ^ICruxAPI crux
  :start (crux/start-standalone-system crux-options)
  :stop (.close crux))

(declare put! put-generations!)
(defstate _init-db
  :start (do (put! data)))

(defn- add-id-if-none-exists
  [{:as e id :crux.db/id}]
  {:pre [(map? e)]}
  (cond-> e (nil? id) (assoc :crux.db/id (UUID/randomUUID))))

(def put-tx
  (map (fn [m]
         (let [m (add-id-if-none-exists m)]
           [:crux.tx/put m]))))

(defn put!
  ([data] (put! data {:blocking? false}))
  ([data {:keys [blocking?]}]
   (let [ret (crux/submit-tx
              crux
              (into [] put-tx data))]
     (when blocking? (crux/sync crux (:crux.tx/tx-time ret) nil))
     ret)))

(defn q [& args]
  (apply crux/q (crux/db crux) args))

(defn entity [eid]
  (crux/entity (crux/db crux) eid))

(defn q-entity [q-expr]
  (->> (q q-expr)
       (map (comp entity first))))

(defn history [eid]
  (crux/history crux eid))

(defn entity-history
  ([eid] (entity-history eid {:with-history-info? false}))
  ([eid {:keys [with-history-info?]}]
   (let [h (history eid)]
     (for [{:as h-ent :keys [crux.tx/tx-time crux.db/id]} h]
       (cond-> (crux/entity (crux/db crux tx-time tx-time) id)
         with-history-info? (merge (dissoc h-ent :crux.db/id)))))))

(defn some-strings-include? [q & strings]
  (let [q (str/lower-case q)]
    (boolean (some #(str/includes? (str/lower-case %) q) strings))))


(defn crux-search [q-str]
  (let [res      (->> (q {:find  '[?e]
                          :where '[[?e :name ?n]
                                   [?e :snippet ?s]
                                   [?e :result ?r]
                                   [(zeal.core/some-strings-include? ?search-string ?n ?s ?r) ?match]]
                          :args  [{:?search-string q-str}]})
                      (map (comp entity first)))
        sort-fn  #(sort-by :time > %)
        names    (->> res (filter :name) sort-fn)
        no-names (->> res (remove :name) sort-fn)]
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
  (let [res      (->> (q {:find  '[?e]
                          :where '[[?e :snippet]
                                   [?e :result]]})
                      (map (comp entity first)))]
    (->> res
         (sort-by :time >)
         (take n)
         vec)))

(defn search-eval-log [q]
  (if (empty? q)
    nil
    (crux-search q)))

(defn eval-and-log-exec-ent! [{:keys [snippet] :as exec-ent}]
  (try
    (let [execd (-> exec-ent (assoc :time (.getTime (Date.))
                                    :result (eval/do-eval-string snippet))
                    add-id-if-none-exists)]
      (put! [execd] {:blocking? true})
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
 (let [all (q '{:find  [?e]
                :where [[?e :crux.db/id]]})]
   (crux/submit-tx
    crux
    (into []
          (comp
           (map first)
           (map (fn [m]
                  [:crux.tx/evict m])))
          all)))
 )
