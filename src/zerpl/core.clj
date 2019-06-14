(ns zerpl.core
  (:require
   ;[crux.api :as crux]
   [mount.core :as mount :refer [defstate]]
   [zerpl.data :refer [data]])
  (:import                              ;(crux.api ICruxAPI)
   (java.util Date)))

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

(defn eval-string [s]
  (eval (read-string s)))

(def eval-log (atom [{:time 1560496898883, :snippet "foo", :result "foo"}
                     {:time 1560496898885, :snippet "(+ 1 1)", :result "2"}]))

(defn search
  "Takes a query, a collection of maps and keys to strings in each map to search.
  Returns a coll with maps that matched ordered by date."
  [q coll ks]
  (->> coll
       (filter (fn [m] (some (fn [k] (clojure.string/includes? (k m) q)) ks)))
       (sort-by :time >)))

(defn search-eval-log [q]
  (search q @eval-log [:snippet :result]))

(defn eval-and-log! [s]
  (let [ret {:time    (.getTime (Date.))
             :snippet s
             :result  (eval-string s)}]
    (do (swap! eval-log conj ret)
        ret)))

(comment
 (do
   (swap! eval-log empty)
   (doseq [test-k [:foo :bar :baz :bandoles :chicken]]
     (eval-and-log!
      (pr-str `(zipmap (range 2) (repeat ~test-k))))))
 )

;(search "zipmap" @eval-log [:snippet :result])
