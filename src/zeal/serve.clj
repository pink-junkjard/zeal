(ns zeal.serve
  (:require [aleph.http :as http]
            [mount.core :as mount :refer [defstate]]
            [manifold.stream :as s]
            [cognitect.transit :as transit]
            [cheshire.core :as json]
            [uix.dom.alpha :as uix.dom]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [zeal.ui.views :as views]
            [zeal.ui.state :as ui-state]
            [zeal.core :as zc]
            [zeal.db :as db]
            [zeal.state :as st]
            [zeal.auth.core :as auth]
            [manifold.deferred :as d]
            [byte-streams :as bs]
            [medley.core :as md])
  (:import (java.io ByteArrayOutputStream InputStream)))

(defn transit-encode
  "Resolve and apply Transit's JSON/MessagePack encoding."
  [out type & [opts]]
  (let [output (ByteArrayOutputStream.)]
    (transit/write (transit/writer output type opts) out)
    (.toByteArray output)))

(defn parse-transit
  "Resolve and apply Transit's JSON/MessagePack decoding."
  [^InputStream in type & [opts]]
  (transit/read (transit/reader in type opts)))

(defn transit-encode-json-with-meta [out & [opts]]
  (transit-encode out :json (merge {:transform transit/write-meta} opts)))

(defn html []
  [:<>
   [:meta {:charset "UTF-8"}]
   [views/document
    {:meta   [{:name    "viewport"
               :content "width=device-width, initial-scale=1"}]
     :styles ["
    .CodeMirror { height: auto !important; }
    .prewrap { white-space: pre-wrap; }
    .break-all { word-break: break-all };"]
     :links  ["css/tachyons.css"
              "css/font-awesome/css/all.css"
              "css/codemirror.css"
              "css/codemirror-show-hint.css"
              "https://fonts.googleapis.com/css?family=Faster+One&display=swap"]
     :js     [{:script (str "__initState = "
                            (json/generate-string ui-state/*init-state*))}
              {:src "js/compiled/main.js"}
              {:script "zeal.ui.core.init()"}]}]])

(defn init-state [{:as req :keys [session]}]
  {:user (select-keys
          (:user session)
          [:user/email])})

(defn index [req]
  (let [res (s/stream)]
    (future
     (binding [ui-state/*init-state* (init-state req)]
       (uix.dom/render-to-stream
        [html] {:on-chunk #(s/put! res %)}))
     (s/close! res))
    (merge
     (select-keys req [:session])
     {:status  200
      :headers {"content-type" "text/html"}
      :body    res})))


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

(def multi-handler-req-dispatch-fn first)

(defmulti multi-handler-response-fn
  (fn [body _handled] (multi-handler-req-dispatch-fn body)))

(defmethod multi-handler-response-fn :eval-and-log
  [req handled]
  {:status  200
   :headers {"content-type" "application/transit+json"}
   :body    (try
              (transit-encode-json-with-meta handled)
              (catch Exception e
                ;; transit can't handle classes and vars so we fallback to string
                (println ::str-fallback e)
                (transit-encode-json-with-meta (update handled :result pr-str))))})

(defmethod multi-handler-response-fn :default
  [req handled]
  {:status  200
   :headers {"content-type" "application/transit+json"}
   :body    (transit-encode handled
                            :json
                            {:transform transit/write-meta})})

(defn- wrap-multi-handler
  ([handler] (fn [req] (wrap-multi-handler handler req)))
  ([handler req]
   (binding [st/*session* (:session req)]
     (let [body    (parse-transit (:body req) :json)
           handled (handler body)]
       (merge (select-keys req [:session])
              (multi-handler-response-fn body handled))))))

(defmulti multi-handler first)

(defmethod multi-handler :eval-and-log
  [[_ exec-ent]]
  (zc/eval-and-log-exec-ent! exec-ent))

(defmethod multi-handler :search
  [[_ {:keys [q]}]]
  (zc/search-eval-log q))

(defmethod multi-handler :recent-exec-ents
  [[_ opts]]
  (zc/recent-exec-ents opts))

(defmethod multi-handler :history
  [[_ {id :crux.db/id}]]
  (db/entity-history id {:with-history-info? true}))

(defmethod multi-handler :merge-entity
  [[_ {id :crux.db/id :as ent}]]
  (some-> id
          db/entity
          (merge ent)
          vector
          (db/put! {:blocking? true}))
  (db/entity id))

(def resource-handler
  (-> (constantly {:status 200})
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-not-modified)))

(defn default-middleware [handler]
  (-> handler
      (auth/oauth-wrapper)
      (wrap-params)
      (wrap-defaults
       (-> site-defaults
           (assoc-in [:session :cookie-attrs :same-site] :lax)
           (assoc :security {;:anti-forgery  true ; FIXME
                             :xss-protection       {:enable? true, :mode :block}
                             :frame-options        :sameorigin
                             :content-type-options :nosniff
                             })))))

(defn user-by-email [email]
  (db/q-entity [['?e :user/email email]]))

(defn user [req]
  (or (-> req :session :user not-empty)
      (when-let [token (auth/token req)]
        (let [email (auth/github-get-user-email token)]
          (if-let [usr (user-by-email email)]
            usr
            (let [usr {:user/email email}]
              (-> (db/put! [usr] {:blocking? true})
                  meta
                  :entities
                  first)))))))

(defn routes [{:as req :keys [uri]}]
  (let [handler
        (case uri
          "/" (if-let [usr (user req)]
                (index (assoc-in req [:session :user] usr))
                auth/redirect)
          "/echo" echo-handler
          "/dispatch" (wrap-multi-handler multi-handler)
          ;; todo add 404
          resource-handler)]
    (if (fn? handler)
      (handler req)
      handler)))

(def handler
  (default-middleware routes))

(defstate server
  :start (http/start-server handler {:port 3400})
  :stop (.close server))

(comment
 (mount/stop)
 (mount/start)
 )

