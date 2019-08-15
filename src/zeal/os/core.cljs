(ns zeal.os.core
  (:require ["electron" :refer [app BrowserWindow crashReporter globalShortcut]]
            ["electron-is-dev" :as dev?]
            ["child_process" :as child-process]))

(defn start-backend
  ([] (start-backend {}))
  ([{:keys [on-stdout on-stderr on-close]
     :or   {on-stdout (partial js/console.log "stdout")
            on-stderr (partial js/console.log "stderr")
            on-close  (partial js/console.log "clj-process exited with code:")}}]
   (js/console.log "Starting Backend Process")
   (js/console.log "resources"
                   (.-resourcesPath js/process))
   (let [clj-process
         (child-process/spawn
          "java"
          #js["-cp" "zeal.jar" "clojure.main" "-m" "zeal.serve"]
          #js{:cwd (.-resourcesPath js/process)}
          )]
     (doto clj-process
       (-> .-stdout (.on "data" (fn [d] (on-stdout (.toString d)))))
       (-> .-stderr (.on "data" (fn [d] (on-stderr (.toString d)))))
       (.on "close" (fn [d] (on-close (.toString d))))))))

;; https://nodejs.org/api/child_process.html#child_process_child_process_exec_command_options_callback
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
             (clj->js {:width          (if dev? 1200 900)
                       :height         600
                       :frame          false
                       :webPreferences {;:nodeIntegration true
                                        :devTools dev?}}
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
  (.on app "will-quit" on-will-quit)
  (.on app "window-all-closed" on-window-all-closed)
  (start-backend
   {:on-stdout
    (fn [s]
      (when (= s "Zeal is ready.")
        ; CrashReporter can just be omitted
        (.start crashReporter
                (clj->js
                 {:companyName "MyAwesomeCompany"
                  :productName "MyAwesomeApp"
                  :submitURL   "https://example.com/submit-url"
                  :autoSubmit  false}))

        (on-ready)
        #_(.on app "ready" on-ready)))}))
