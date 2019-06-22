(ns zeal.serve
  (:require [aleph.http :as http]
            [mount.core :as mount :refer [defstate]]
            [manifold.stream :as s]
            [aleph.http.client-middleware :refer [parse-transit transit-encode]]
            [uix.dom.alpha :as uix.dom]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
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
    {:styles [".prewrap { white-space: pre-wrap; } .break-all { word-break: break-all };"]
     :links  ["css/tachyons.css"
              "css/codemirror.css"
              "css/font-awesome/css/all.css"]
     :js     [{:src "js/compiled/main.js"}
              {:script "zeal.ui.core.init()"}]}]])

(defn index [_]
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

(defn- wrap-multi-handler
  ([handler] (fn [req] (wrap-multi-handler handler req)))
  ([handler req]
   {:status  200
    :headers {"content-type" "application/transit+json"}
    :body    (transit-encode
              (handler (parse-transit (:body req) :json))
              :json)}))

(defmulti multi-handler first)

(defmethod multi-handler :eval-and-log
  [[_ exec-ent]]
  (zc/eval-and-log-exec-ent! exec-ent))

(defmethod multi-handler :search
  [[_ {:keys [q]}]]
  (zc/search-eval-log q))

(defmethod multi-handler :history
  [[_ {id :crux.db/id}]]
  (zc/entity-history id))

(defmethod multi-handler :merge-entity
  [[_ {id :crux.db/id :as ent}]]
  (some-> id
          zc/entity
          (merge ent)
          vector
          (zc/put! {:blocking? true}))
  (zc/entity id))

(def resource-handler
  (-> (constantly {:status 200})
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-not-modified)))

(defn handler [{:as req :keys [uri]}]
  (let [handle
        (case uri
          "/" index
          "/echo" echo-handler
          "/dispatch" (wrap-multi-handler multi-handler)
          ;; todo add 404
          resource-handler)]
    (handle req)))

(defstate server
  :start (http/start-server handler {:port 3400})
  :stop (.close server))

(comment
 (mount/stop)
 (mount/start)
 )

