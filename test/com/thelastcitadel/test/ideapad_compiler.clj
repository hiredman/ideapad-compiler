(ns com.thelastcitadel.test.ideapad-compiler
  (:require [com.thelastcitadel.ideapad-compiler :refer :all]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.test :refer :all]
            [ring.adapter.jetty :as jetty]))

(use-fixtures :once
              (fn [f]
                (let [j (jetty/run-jetty #'handler
                                         {:port 25156
                                          :join? false})]
                  (try
                    (f)
                    (finally
                      (.stop j))))))

(deftest f
  (let [store (atom nil)]
    (let [{:keys [cookies]} (http/post "http://localhost:25156/login"
                                       {:form-params {:username "root"
                                                      :password "password"}
                                        ;; redirects eat cookies
                                        :follow-redirects false})]
      (reset! store cookies))
    (let [{:keys [body cookies]} (http/post "http://localhost:25156/compile"
                                            {:form-params {:op "eval"
                                                           :code "(+ 1 2)"}
                                             :cookies @store})]
      (is (= [] (json/decode body))))
    (let [{:keys [body cookies]} (http/post "http://localhost:25156/compile"
                                            {:form-params {:op "eval"
                                                           :code "(+ 1 2)"}
                                             :cookies @store})]
      (is (= [] (json/decode body))))
    (let [[r _] (for [i (range)
                      item (let [{:keys [body trace-redirects]}
                                 (http/get "http://localhost:25156/compile"
                                           {:headers {"REPL-Response-Timeout" (str 10000)}
                                            :cookies @store})]
                             (json/decode body true))]
                  (do
                    (Thread/sleep 100)
                    item))]
      (is (= 3 (read-string (:value r)))))))
