(ns zeal.ui.talk
  (:require
   [cognitect.transit :as t]
   [cljs.core.async :as a :refer [go <!]]
   [cljs-http.client :as http]))

(def eval-url "http://localhost:3400/eval-log")

(def read-transit
  (let [reader (t/reader :json)]
    (fn [transit]
      (t/read reader transit))))

(defn send-eval! [exec-ent cb]
  (go
   (cb
    (:body (<! (http/post
                eval-url
                {:transit-params (dissoc exec-ent :result)}))))))

(def search-url "http://localhost:3400/search-eval-log")

(defn send-search [q cb]
  (go
   (cb
    (:body (<! (http/post
                search-url
                {:transit-params {:q q}}))))))

(comment
 (send-eval! (pr-str '(* 14 7)) println)
 (send-eval! (pr-str '(zipmap (range 10) (range 10))) println)
 (send-eval! (pr-str [{:first-name "josh"
                       :last-name  "kornreich"
                       :phone      1312123123}]) println)

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
