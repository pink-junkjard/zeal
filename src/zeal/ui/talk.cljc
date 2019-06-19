(ns zeal.ui.talk
  (:require
   [cognitect.transit :as t]
   #?@(:cljs
       [[cljs.core.async :as a :refer [go <!]]
        [cljs-http.client :as http]]))
  (:refer-clojure :exclude [send]))

(def read-transit
  #?(:cljs
     (let [reader (t/reader :json)]
       (fn [transit]
         (t/read reader transit)))))

(def dispatch-url "http://localhost:3400/dispatch")

(defn send [dispatch-vec cb]
  #?(:cljs
     (go
      (cb
       (:body (<! (http/post
                   dispatch-url
                   {:transit-params dispatch-vec})))))))

(defn send-eval! [exec-ent cb]
  (send [:eval-and-log (dissoc exec-ent :result)] cb))

(defn send-search [q cb]
  (send [:search {:q q}] cb))

(defn history [exec-ent cb]
  (send [:history (select-keys exec-ent [:crux.db/id])] cb))

(comment
 (send-eval! {:snippet (pr-str '(* 14 7))} println)
 (send-eval! {:snippet (pr-str '(zipmap (range 10) (range 10)))} println)
 (send-eval! {:snippet (pr-str [{:first-name "josh"
                                 :last-name  "kornreich"
                                 :phone      1312123123}])} println)

 (history {:crux.db/id #uuid "a1081d19-c4c2-4c83-8f42-72af4f4d6dac"} println)
 )

#_(defonce socket
    (ws/open ws-url
             ws-out
             (fn [results-transit]
               (js/console.log :res results-transit)
               (js/console.log :TR (read-transit results-transit))
               (st/db-assoc :search-results (read-transit results-transit)))))

#_(defn send-search [q]
    (ws/send ws-out q))

;(send-search "foo")

;(ws/send ws-out "foo")

;"ws://localhost:3400/eval"

#_(let [conn @(http/websocket-client "ws://localhost:3400/echo")]

    (s/put-all! conn
                (->> 10 range (map str)))

    (->> conn
         (s/transform (take 10))
         s/stream->seq
         doall))                        ;=> ("0" "1" "2" "3" "4" "5" "6" "7" "8" "9")
