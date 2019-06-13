(ns zerpl.core
  (:require
   [crux.api :as crux]
   [mount.core :as mount :refer [defstate]]
   [zerpl.data :refer [data]])
  (:import (crux.api ICruxAPI)))

;;; Crux

(def crux-options
  {:kv-backend "crux.kv.memdb.MemKv"
   :db-dir     "data/db-dir-1"})

(defstate ^ICruxAPI crux
  :start (crux/start-standalone-system crux-options)
  :stop (.close crux))

(declare put! put-generations!)
(defstate _init-db
  :start (do (put! data)))

(def put-tx
  (map (fn [m]
         (let [id (or (:crux.db/id m) (java.util.UUID/randomUUID))]
           [:crux.tx/put id             ; id for Kafka
            (assoc m :crux.db/id id)
            (java.util.Date.)]))))

(defn put! [data]
  (crux/submit-tx
   crux
   (into [] put-tx data)))

(defn q [& args]
  (apply crux/q (crux/db crux) args))

(defn entity [eid]
  (crux/entity (crux/db crux) eid))
