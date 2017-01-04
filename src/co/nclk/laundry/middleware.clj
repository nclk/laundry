(ns co.nclk.laundry.middleware
  (:require [cheshire.core :as json]
            [clj-yaml.core :as yaml]))

(defn wrap-json
  [handler]
  #(let [resp (handler %)]
    (if (false? (:json? resp))
      resp
      (-> resp
        (assoc-in [:headers "Content-Type"]
          "application/json")
        (assoc-in [:headers "Access-Control-Allow-Origin"]
          "*")
        (assoc :body (when (:body resp)
                       (json/generate-string (:body resp))))))))

(defn wrap-api-exception
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch co.nclk.laundry.APIException apie
        {:status 500
         :body {:type "APIException"
                :message (.getMessage apie)}}))))

(defn wrap-json-body
  [handler]
  (fn [request]
    (handler (assoc request
               :body
               (-> request :body
                 slurp
                 (clojure.string/replace #"\t" "  ")
                 yaml/parse-string)))))
