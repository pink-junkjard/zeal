(ns zeal.serve
  (:require [aleph.http :as http]
            [mount.core :as mount :refer [defstate]]
            [manifold.stream :as s]
            [aleph.http.client-middleware :refer [parse-transit transit-encode]]
            [uix.dom.alpha :as uix.dom]
            [zeal.ui.views :as views]
            [clojure.core.async :as a]
            [zeal.core :as zc]
            [manifold.deferred :as d]
            [byte-streams :as bs]))

(defn handler [req]
  {:status  200
   :headers {"content-type" "text/plain"}
   :body    "hello!"})

(defn html []
  [:<>
   [:meta {:charset "UTF-8"}]
   [views/document
    {:styles []
     :links  ["css/tachyons.css" "css/codemirror.css"]
     :js     [{:src "js/compiled/main.js"}
              {:script "zeal.ui.core.init()"}]}]])

(defn index []
  (let [res (s/stream)]
    (future
     (uix.dom/render-to-stream
      [html] {:on-chunk #(s/put! res %)})
     (s/close! res))
    {:status  200
     :headers {"content-type" "text/html"}
     :body    res}))


(def non-websocket-request
  {:status  400
   :headers {"content-type" "application/text"}
   :body    "Expected a websocket request."})

(defn ws-search-eval-log-handler
  [req]
  (-> (http/websocket-connection req)
      (d/chain
       (fn [socket]
         (s/consume
          (fn [msg]
            (let [search-res (zc/search-eval-log msg)]
              (s/put! socket
                      (transit-encode search-res :json))))
          socket)
         socket))
      (d/catch
       (fn [_]
         non-websocket-request))))
;(mount/start)

(defn search-eval-log-handler [req]
  {:status  200
   :headers {"content-type" "application/transit+json"}
   :body    (transit-encode
             (zc/search-eval-log (:q (parse-transit (-> req :body) :json)))
             :json)})

(defn eval-log-handler [req]
  {:status  200
   :headers {"content-type" "application/transit+json"}
   :body    (transit-encode
             (zc/eval-and-log-exec-ent! (parse-transit (:body req) :json))
             :json)})

(defn echo-handler
  [req]
  (-> (http/websocket-connection req)
      (d/chain
       (fn [socket]
         (s/connect socket socket)))
      (d/catch
       (fn [_]
         non-websocket-request))))

(defn dev-handler [req]
  {:status  200
   :headers {"content-type" "text/html"}
   :body    (uix.dom/render-to-string [html])})

(defn handler [{:as req :keys [uri]}]
  (case uri
    "/" (index)
    "/echo" (echo-handler req)
    "/eval-log" (eval-log-handler req)
    "/search-eval-log" (search-eval-log-handler req)

    ;; todo add 404
    {:status  200
     :headers {"content-type" "application/javascript"}
     :body    (slurp (str "resources/public" (:uri req)))}))

(defstate server
  :start (http/start-server handler {:port 3400})
  :stop (.close server))

(comment
 (mount/stop)
 (mount/start)
 )
