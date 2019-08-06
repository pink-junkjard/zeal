(ns shadow
  (:require [shadow.cljs.devtools.api :as shadow.api]
            [shadow.cljs.devtools.server :as shadow.server]
            [zeal.serve :refer [server]]
            [mount.core :as mount :refer [defstate]]))

(defstate shadow-web
  :start (do (shadow.server/start!)
             (shadow.api/watch :app))
  :stop (shadow.api/stop-worker :app))

(defstate shadow-os
  :start (do #_(shadow.server/start!)
           (shadow.api/watch :os))
  :stop (shadow.api/stop-worker :os))

(comment

 (mount/start)
 (mount/stop)

 (shadow.api/repl :app)
 (shadow.api/repl :os)
 :cljs/quit

 (shadow.server/stop!)
 )
