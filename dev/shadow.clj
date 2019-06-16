(ns shadow
  (:require [shadow.cljs.devtools.api :as shadow.api]
            [shadow.cljs.devtools.server :as shadow.server]
            [zerpl.core]
            [zerpl.serve :refer [server]]
            [mount.core :as mount :refer [defstate]]))

(defstate shadow
  :start (do (shadow.server/start!)
             (shadow.api/watch :app))
  :stop (do (shadow.server/stop!)
            (shadow.api/stop-worker :app)))


(comment

(mount/start)

(shadow.api/repl :app)
 :cljs/quit
 )
