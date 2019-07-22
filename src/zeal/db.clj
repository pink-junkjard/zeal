(ns zeal.db
  (:require [crux.api :as crux]
            [zeal.data :refer [data]]
            [mount.core :as mount :refer [defstate]])
  (:import (java.util UUID)
           (crux.api ICruxAPI)
           (java.time Duration)))

;;;; Crux

(def crux-options
  {:kv-backend    "crux.kv.rocksdb.RocksKv"
   :event-log-dir "data/eventlog-1"
   :db-dir        "data/db-dir-1"})

(defstate ^ICruxAPI node
  :start (crux/start-standalone-node crux-options)
  :stop (.close node))

(declare put! put-generations!)
(defstate _init-db
  :start (do (put! data)))

(defn add-id-if-none-exists
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
              node
              (into [] put-tx data))]
     (when blocking? (crux/sync node (:crux.tx/tx-time ret) (Duration/ofSeconds 2)))
     ret)))

(defn q [& args]
  (apply crux/q (crux/db node) args))

(defn entity [eid]
  (crux/entity (crux/db node) eid))

(defn q-entity [q-expr]
  (->> (q q-expr)
       (map (comp entity first))))

(defn history [eid]
  (crux/history node eid))

(defn entity-history
  ([eid] (entity-history eid {:with-history-info? false}))
  ([eid {:keys [with-history-info?]}]
   (let [h (history eid)]
     (for [{:as h-ent :keys [crux.tx/tx-time crux.db/id]} h]
       (cond-> (crux/entity (crux/db node tx-time tx-time) id)
         with-history-info? (merge (dissoc h-ent :crux.db/id)))))))
