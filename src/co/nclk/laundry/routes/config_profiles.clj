(ns co.nclk.laundry.routes.config-profiles
  (:require [compojure.core :refer :all]
            [co.nclk.laundry.routes.common
             :refer [data-access-fns]]))

  
;;(def config-profiles
;;  (let [filter-keys #{:program_name :name}
;;        {:keys [collect
;;                one
;;                update!
;;                delete!]}
;;        (data-access-fns
;;          :config_profile
;;          filter-keys)]
;;
;;    (context "/config-profiles" []
;;
;;      (GET "/" request
;;        {:status 200
;;         :body (collect (:params request))})
;;      (POST "/" request
;;        {:status 501})
;;      (GET "/:program_name/" request
;;        {:status 200
;;         :body (collect (:params request))})
;;
;;      (GET "/:program_name/:name" request
;;        (if-let [resp (one (:params request))]
;;          {:status 200
;;           :body resp}
;;          {:status 404}))
;;
;;      ;;;; No content
;;      ;;(PUT "/:prg-name/:cp-name" [prg-name cp-name :as request]
;;      ;;  (if (= 1 (first (config-profile/update!
;;      ;;                    prg-name
;;      ;;                    cp-name
;;      ;;                    (-> request
;;      ;;                      :body
;;      ;;                      slurp
;;      ;;                      json/parse-string))))
;;      ;;    {:status 204}
;;      ;;    {:status 404}))
;;      ;;(DELETE "/:prg-name/:name" request
;;      ;;  {:status 501})
;;
;;      )))
;;
