(ns zerpl.serve
  (:require [aleph.http :as http]
            [mount.core :as mount :refer [defstate]]
            [manifold.stream :as s]
            [uix.dom.alpha :as uix.dom]
            [zerpl.ui.views :as views]))

(defn handler [req]
  {:status  200
   :headers {"content-type" "text/plain"}
   :body    "hello!"})

(defn html []
  [:<>
   [:meta {:charset "UTF-8"}]
   [views/document
    {:styles [#_(rnd-stylesheet) "body { font-family: menlo }"]
     :links  ["css/tachyons.css"]
     :js     [{:src "js/compiled/main.js"}
              {:script "zerpl.ui.core.init()"}]}]])

(defn index []
  (let [res (s/stream)]
    (future
     (uix.dom/render-to-stream
      [html] {:on-chunk #(s/put! res %)})
     (s/close! res))
    {:status  200
     :headers {"content-type" "text/html"}
     :body    res}))

(defn dev-handler [req]
  {:status  200
   :headers {"content-type" "text/html"}
   :body    (uix.dom/render-to-string [html])})

(defn handler [{:as req :keys [uri]}]
  (case uri
    "/" (index)

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


