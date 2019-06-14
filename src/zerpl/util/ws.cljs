(ns zerpl.util.ws
  (:require [cljs.core.async :as a :refer [go <!]]))

(defn open [ws-url ws-in ws-out]
  (let [ws (js/WebSocket. ws-url)]
    (doto ws
      (.addEventListener "open"
                         (fn [e]
                           ;(fp/transact! reconciler [(tx/ws-open)])

                           (go (loop []
                                 (when-some [msg (<! ws-out)]
                                   (js/console.log "WS-OUT" msg)
                                   (.send ws msg)
                                   (recur)))

                               (a/close! ws-in)
                               (.close ws))

                           (go (loop []
                                 (when-some [msg (<! ws-in)]
                                   (js/console.log "WS-IN" msg)
                                   ;(process-ws reconciler msg)
                                   (recur)))

                               (a/close! ws-out)
                               (.close ws))))

      (.addEventListener "close"
                         (fn [e]
                           ;(fp/transact! reconciler [(tx/ws-close)])
                           (js/console.warn "WS-CLOSE" e)))

      (.addEventListener "message"
                         (fn [e]
                           (when-not (a/offer! ws-in (.. e -data))
                             (js/console.warn "WS-IN OVERLOAD!" e)))))))

(defn send [ws-out msg]
  (when-not (a/offer! ws-out msg)
    (js/console.warn "WS-OUT OVERLOAD!" msg)))
