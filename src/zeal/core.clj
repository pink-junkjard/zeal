(ns zeal.core
  (:require
   ;[crux.api :as crux]
   [mount.core :as mount :refer [defstate]]
   [zeal.data :refer [data]]
   [clojure.string :as str])
  (:import                              ;(crux.api ICruxAPI)
   (java.util Date UUID)))

;;;; Crux
;
;(def crux-options
;  {:kv-backend "crux.kv.memdb.MemKv"
;   :db-dir     "data/db-dir-1"})
;
;(defstate ^ICruxAPI crux
;  :start (crux/start-standalone-system crux-options)
;  :stop (.close crux))
;
;(declare put! put-generations!)
;(defstate _init-db
;  :start (do (put! data)))
;
;(def put-tx
;  (map (fn [m]
;         (let [id (or (:crux.db/id m) (java.util.UUID/randomUUID))]
;           [:crux.tx/put id             ; id for Kafka
;            (assoc m :crux.db/id id)
;            (java.util.Date.)]))))
;
;(defn put! [data]
;  (crux/submit-tx
;   crux
;   (into [] put-tx data)))
;
;(defn q [& args]
;  (apply crux/q (crux/db crux) args))
;
;(defn entity [eid]
;  (crux/entity (crux/db crux) eid))

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
  (search q @eval-log [:snippet :result]))

(defn eval-and-log-string! [s]
  (let [ret {:id      (UUID/randomUUID)
             :time    (.getTime (Date.))
             :snippet s
             :result  (pr-str (eval (read-string (str "(do " s ")"))))}]
    (do (swap! eval-log conj ret)
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
