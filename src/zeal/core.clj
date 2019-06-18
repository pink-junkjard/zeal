(ns zeal.core
  (:require
   [crux.api :as crux]
   [mount.core :as mount :refer [defstate]]
   [zeal.data :refer [data]]
   [clojure.string :as str])
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

(def put-tx
  (map (fn [{:as m id :crux.db/id}]
         (let [m (if id m (assoc m :crux.db/id (UUID/randomUUID)))]
           [:crux.tx/put m]))))

(defn put! [data]
  (crux/submit-tx
   crux
   (into [] put-tx data)))

(defn q [& args]
  (apply crux/q (crux/db crux) args))

(defn entity [eid]
  (crux/entity (crux/db crux) eid))

(defn some-strings-include? [q & strings]
  (let [q (str/lower-case q)]
    (boolean (some #(str/includes? (str/lower-case %) q) strings))))

(defn crux-search [q-str]
  (->> (q {:find  '[?e]
       :where '[[?e :snippet ?s]
                [?e :result ?r]
                [(zeal.core/some-strings-include? ?search-string ?s ?r) ?match]]
       :args  [{:?search-string q-str}]})
       (map (comp entity first))
       (sort-by :time >)
       vec))

;;; eval

(def eval-log (atom []))

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

(defn search-eval-log [q]
  (if (empty? q)
    nil
    (crux-search q))
  #_(search q @eval-log [:snippet :result]))

(defn eval-and-log-string! [s]
  (let [ret {:id      (UUID/randomUUID)
             :time    (.getTime (Date.))
             :snippet s
             :result  (pr-str (eval (read-string (str "(do " s ")"))))}]
    (do
      (put! [(dissoc ret :id)])
      (swap! eval-log conj ret)
      ret)))

(defn eval-and-log! [s]
  (let [ret {:id      (UUID/randomUUID)
             :time    (.getTime (Date.))
             :snippet s
             :result  (eval s)}]
    (do (swap! eval-log conj ret)
        ret)))


(comment
 (do
   (swap! eval-log empty)
   (doseq [test-k [:foo :bar :baz :bandoles :chicken]]
     (eval-and-log!
      `(zipmap (range 2) (repeat ~test-k)))))
 )

;(search "zipmap" @eval-log [:snippet :result])
