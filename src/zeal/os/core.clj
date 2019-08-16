(ns zeal.os.core
  (:require
   [mount.core :as mount :refer [defstate]]
   [zeal.serve :refer [server]]
   [zeal.util.shell :as sh]
   [clojure.string :as str]))

;; Unfortunately I haven't found a way to start *and kill* the electron process
;; from Java

(defstate os-app
  :start (do (defonce app
               (let [p (sh/proc "electron" "resources/public/electron.js")]
                 (future (sh/stream-to-out p :out))
                 p))
             app))
