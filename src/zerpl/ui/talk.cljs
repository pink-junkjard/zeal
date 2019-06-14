(ns zerpl.ui.talk
  (:require
   [zerpl.util.ws :as ws]
   [cljs.core.async :as a :refer [go <!]]))

(def ws-url "ws://localhost:3400/search-eval-log")
(def ws-in (a/chan 1))
(def ws-out (a/chan 1))
(defonce socket
  (ws/open ws-url ws-in ws-out))

(ws/send ws-out "foo")

#_(let [conn @(http/websocket-client "ws://localhost:3400/echo")]

    (s/put-all! conn
                (->> 10 range (map str)))

    (->> conn
         (s/transform (take 10))
         s/stream->seq
         doall));=> ("0" "1" "2" "3" "4" "5" "6" "7" "8" "9")
