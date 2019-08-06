(ns zeal.os.core
  (:require ["electron" :refer [app BrowserWindow crashReporter globalShortcut]]))

;; using Electron for the OS
;; Todo move this into it's own sibling library
;; and require web from deps

(def main-window (atom nil))

;; init-app
;;   this removes the app from the doc,
;;   also enables the app to show up above fullscreen apps
;;   in combination with the visibility settings in init-browser
(.hide (.-dock app))

(defn init-browser []
  (let [win (BrowserWindow.
             (clj->js {:width          1200
                       :height         600
                       :frame          false
                       :webPreferences {;:nodeIntegration true
                                        :devTools true}}
                      ))]
    (doto win

      (.setAlwaysOnTop true "floating")
      (.setVisibleOnAllWorkspaces true)
      (.setFullScreenable false)

      ; Path is relative to the compiled js file (main.js in our case)
      (.loadURL
       "http://localhost:3400/"
       #_(str "file://" js/__dirname "/index.html"))

      (.on "blur" #(.hide app))
      (.on "close" (fn [e]
                     (.hide app)
                     (.preventDefault e))))


    (.openDevTools (.-webContents win))
    (reset! main-window win)))

(defn register-shortcuts []
  (letfn [(toggle-app []
            (if (.isVisible @main-window)
              (.hide app)
              (do (.show app)
                  (doto @main-window
                    (.show)
                    (.focus)))))]
    (.register globalShortcut "Option+Space" toggle-app)
    (.register globalShortcut "Option+Z" toggle-app))

  (letfn [(quit-app [] (.quit app))]
    (.register globalShortcut "CommandOrControl+Q" quit-app))

  )

(defn on-ready []
  (register-shortcuts)
  (init-browser))

(defn on-will-quit []
  (.unregisterAll globalShortcut))

(defn on-window-all-closed []
  (when-not (= js/process.platform "darwin")
    (.quit app)))

(defn main []
  ; CrashReporter can just be omitted
  (.start crashReporter
          (clj->js
           {:companyName "MyAwesomeCompany"
            :productName "MyAwesomeApp"
            :submitURL   "https://example.com/submit-url"
            :autoSubmit  false}))

  (.on app "will-quit" on-will-quit)
  (.on app "window-all-closed" on-window-all-closed)
  (.on app "ready" on-ready))
