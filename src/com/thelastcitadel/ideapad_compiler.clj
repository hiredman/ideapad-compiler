(ns com.thelastcitadel.ideapad-compiler
  (:require [cemerick.drawbridge :as cdb]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [nrepl.cljs.middleware.compile]
            [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [cemerick.friend.credentials :as creds]
            [compojure.core :refer [GET ANY POST defroutes]]
            [compojure.handler :refer [site]]))

;; drawbridge clobbers ring sessions, fix it
(alter-var-root #'cdb/ring-handler
                (fn [f]
                  (fn [& args]
                    (let [f (apply f args)]
                      (fn [req]
                        (update-in (f req) [:session] merge (:session req)))))))

(def nrepl-handler
  (-> (cdb/ring-handler
       :nrepl-handler
       (-> (clojure.tools.nrepl.server/default-handler)
           nrepl.cljs.middleware.compile/compile-cljs))
      ((fn [f]
         (fn [req]
           (println "Evaluating code")
           (f req))))
      (friend/wrap-authorize #{::authenticated})))

(def users {"root" {:username "root"
                    :password (creds/hash-bcrypt "password")
                    :roles #{::authenticated}}})

(defroutes app
  (ANY "/" request {:body "Nothing to see here"})
  (ANY "/login" request {:body "Yo"})
  (ANY "/compile" request nrepl-handler)
  (friend/logout
   (ANY "/logout" request (ring.util.response/redirect "/"))))

(def handler
  (-> #'app
      (friend/authenticate
       {:credential-fn (partial creds/bcrypt-credential-fn users)
        :workflows [(workflows/interactive-form)]})
      site))
