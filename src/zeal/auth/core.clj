(ns zeal.auth.core
  (:require [zeal.config :as config :refer [config]]
            [cheshire.core :as json]
            [medley.core :as md]
            [byte-streams :as bs]
            [aleph.http :as http]
            [ring.util.response :as response]
            [ring.middleware.oauth2 :refer [wrap-oauth2]]))

(def github-root
  "https://api.github.com")

(defn github-api-get [uri-segment token]
  (-> @(http/get
        (str github-root uri-segment)
        {:oauth-token      token
         :throw-exceptions false
         ; tzeee github https://developer.github.com/v3/#user-agent-required
         :headers          {:user-agent (config/get :github/app-name)}})
      (update :body #(some-> % bs/to-string (json/parse-string keyword)))))

(defn github-get-user-email [token]
  (let [{:keys [status body]}
        (github-api-get "/user/emails" token)]
    (case status
      200 (:email (md/find-first :primary body))
      body)))

(def config-creds
  {:client-id     (config/get :github/client-id)
   :client-secret (config/get :github/client-secret)})

(defn oauth-wrapper [handler]
  (wrap-oauth2
   handler
   {:github
    (merge
     config-creds
     {:authorize-uri    "https://github.com/login/oauth/authorize"
      :access-token-uri "https://github.com/login/oauth/access_token"
      :scopes           ["user:email"]
      :launch-uri       "/oauth2/github"
      :redirect-uri     "/oauth2/github/callback"
      :landing-uri      "/"})}))

(def redirect (constantly (response/redirect "/oauth2/github")))
