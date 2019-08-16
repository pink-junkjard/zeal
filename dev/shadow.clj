(ns shadow
  (:require [shadow.cljs.devtools.api :as shadow.api]
            [shadow.cljs.devtools.server :as shadow.server]
            [zeal.os.core :refer [os-app]]
            [zeal.serve :refer [server]]
            [mount.core :as mount :refer [defstate]]))

(defstate shadow-web
  :start (do (shadow.server/start!)
             (shadow.api/watch :app))
  :stop (shadow.api/stop-worker :app))

(defstate shadow-os
  :start (do (shadow.server/start!)
             (shadow.api/watch :os))
  :stop (shadow.api/stop-worker :os))

(defn release-electron []
  ;; todo package backend
  (shadow.api/release :app)
  (shadow.api/release :os))

(comment
 (mount/start)
 (mount/start #'shadow-os)
 (mount/start
  #'zeal.serve/server #'zeal.db/node #'zeal.db/_init-db)
 (mount/stop)
 (mount/stop #'zeal.serve/server #'zeal.db/node #'zeal.db/_init-db)
 (mount/find-all-states)
 (mount/running-states)

 (shadow.api/repl :app)
 (shadow.api/repl :os)
 :cljs/quit

 (shadow.server/stop!)

 (release-electron)
 )
