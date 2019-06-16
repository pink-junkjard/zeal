(ns zeal.ui.core
  (:require [uix.dom.alpha :as uix.dom]
            [zeal.ui.talk]
            [zeal.ui.views :as views]))

(defn start []
  ;(mixed-media/render-example)
  (uix.dom/hydrate [views/app] js/root))

(defn ^:export init []
  ;; init is called ONCE when the page loads
  ;; this is called in the index.html and must be exported
  ;; so it is available even in :advanced release builds
  (start))

(defn stop []
  ;; stop is called before any code is reloaded
  ;; this is controlled by :before-load in the config
  (js/console.log "stop"))

