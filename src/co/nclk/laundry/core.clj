(ns co.nclk.laundry.core
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.defaults :refer [wrap-defaults
                                              api-defaults]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [co.nclk.laundry.actions :as actions]
            [co.nclk.laundry.models.common :as models]
            [co.nclk.laundry.middleware :refer [wrap-json
                                                wrap-json-body
                                                wrap-api-exception]]
            [co.nclk.laundry.routes.common :refer :all]
            [co.nclk.laundry.routes.config-profiles :refer :all]
            )
  (:import (java.net NetworkInterface Inet4Address)))

(defn ip-filter [inet]
  (and (.isUp inet)
       (not (.isVirtual inet))
       (not (.isLoopback inet))))

(defn ip-extract [netinf]
  (let [inets (enumeration-seq (.getInetAddresses netinf))]
   (map #(vector (.getHostAddress %1) %2) (filter #(instance? Inet4Address %) inets ) (repeat (.getName netinf)))))

(defn ips []
  (let [ifc (NetworkInterface/getNetworkInterfaces)]
     (mapcat ip-extract (filter ip-filter (enumeration-seq ifc)))))

(defn controller
  [api-base]
  {"Config Profiles"
   {:context "/config-profiles"
    :relation :config_profile
    :description
    (str "Config profiles are resources that persist useful "
         "configurations "
         "for a particular test program. When used with the "
         "`/actions/run` endpoint, each config from the given "
         "list is `assoc`ed into the previous one in sequence, "
         "providing a mechanism for overriding defaults.")
    :related-resources {:config-profile-program-maps
                        [(str api-base
                              "/config-profile-program-maps/?config_profile=%s")
                         :name]
                        :user [(str api-base "/users/%s")
                               :username]}
    :filter-keys #{:name :username}
    :insert-keys #{:name :data :username}
    :update-keys #{:name :data}
    :delete-keys #{:name}
    :individual-href [(str api-base "/config-profiles/%s") :name]
    :routes [{:path "/"
              :methods #{:get :post}
              :collection? true}
             {:path "/:name"
              :methods #{:get :put :delete}}
             ]}
   "Config Profile/Program Maps"
   {:relation :config_profile_program_map
    :context "/config-profile-program-maps"
    :individual-href [(str api-base "/config-profile-program-maps/%s/%s")
                      :config_profile :program]
    :related-resources {:config_profile
                        [(str api-base "/config-profiles/%s")
                         :config_profile]
                        :program
                        [(str api-base "/programs/%s")
                         :program]}
    :filter-keys #{:program :config_profile}
    :insert-keys #{:program :config_profile}
    :routes [{:path "/"
              :methods #{:get :post}
              :collection? true}
             {:path "/:config_profile/:program"
              :methods #{:get :put :delete}}]}
   "Users"
   {:relation :usr
    :context "/users"
    :description "Registered users."
    :individual-href [(str api-base "/users/%s") :username]
    :related-resources {"Config profiles"
                        [(str api-base "/config-profiles/?username=%s")
                         :username]}
    :routes [{:path "/"
              :methods #{:get}
              :collection? true}
             {:path "/:username"
              :methods #{:get :put :delete}}]}
   "Programs"
   {:relation :program
    :context "/programs"
    :description "A test program written in the flax/linen paradigm."
    :individual-href [(str api-base "/programs/%s")
                      :name]
    :related-resources {:config-profile-program-maps
                        [(str api-base "/config-profile-program-maps/?program=%s")
                         :name]
                        :test-runs
                        [(str api-base "/test-runs/?program_name=%s")
                         :name]}
    :filter-keys #{:name}
    :insert-keys #{:name :data}
    :update-keys #{:name :data}
    :routes [{:path "/"
              :methods #{:get :post}
              :collection? true}
             {:path "/:name"
              :methods #{:get :put :delete}}]}
   "Modules"
   {:relation :module
    :context "/modules"
    :description (str "A module written in the flax/linen paradigm "
                      "to be used with linen programs.")
    :individual-href [(str api-base "/modules/%s")
                      :name]
    :filter-keys #{:name}
    :routes [{:path "/"
              :methods #{:get :post}
              :collection? true}
             {:path "/:name"
              :methods #{:get :put :delete}}]}
   "Test Runs"
   {:relation :test_run
    :context "/test-runs"
    :description (str "Holds data specific to an individual run of a "
                      "program together with its configuration/"
                      "overrides.")
    :individual-href [(str api-base "/test-runs/%s")
                      :id]
    :related-resources {:program
                        [(str api-base "/programs/%s")
                         :program_name]
                        :checkpoints
                        [(str api-base "/checkpoints/?test_run_id=%s")
                         :id]
                        :failures
                        [(str api-base "/checkpoints/?test_run_id=%s&success=false")
                         :id]
                        :successes
                        [(str api-base "/checkpoints/?test_run_id=%s&success=true")
                         :id]
                        :log-entries
                        [(str api-base "/log-entries/?test_run_id=%s")
                         :id]
                        :raw-result
                        [(str api-base "/raw-results/%s")
                         :id]
                        :harvests
                        [(str api-base "/harvests/?test_run_id=%s")
                         :id]
                        }
    :filter-keys #{:id|uuid :program_name :status
                   :seed|int :num_failures|int :num_checkpoints|int}
    :routes [{:path "/"
              :methods #{:get}
              :collection? true}
             {:path "/:id"
              :methods #{:get :delete}}]}
   "Raw Results"
   {:relation :raw_result
    :context "/raw-results"
    :description (str "The fully \"realized\" test program (useful in "
                      "rare cases for debugging.")
    :individual-href [(str api-base "/raw-results/%s")
                      :test_run_id]
    :related-resources {:test-run
                        [(str api-base "/test-runs/%s")
                         :test_run_id]}
    :filter-keys #{:test_run_id|uuid}
    :routes [{:path "/"
              :methods #{:get}
              :collection? true}
             {:path "/:test_run_id"
              :methods #{:get :delete}}]}
   "Checkpoints"
   {:relation :checkpoint
    :context "/checkpoints"
    :description (str "Individual linen checkpoint, which represents "
                      "an \"atomic\" test or subroutine.")
    :individual-href [(str api-base "/checkpoints/%s")
                      :id]
    :related-resources {:test-run
                        [(str api-base "/test-runs/%s")
                         :test_run_id]}
    :filter-keys #{:test_run_id|uuid :success|bool}
    :routes [{:path "/"
              :methods #{:get}
              :collection? true}
             {:path "/:id"
              :methods #{:get}}]}
   "Harvest"
   {:relation :harvest
    :context "/harvests"
    :description (str "Custom reports")
    :individual-href [(str api-base "/harvests/%s")
                      :id|int]
    :related-resources {:test-run
                        [(str api-base "/test-runs/%s")
                         :test_run_id]}
    :filter-keys #{:test_run_id|uuid :id|int}
    :routes [{:path "/"
              :methods #{:get}
              :collection? true}
             {:path "/:name"
              :methods #{:get}
              :collection? true}]}
   "Log Entries"
   {:relation :log_entry
    :context "/log-entries"
    :description "Individual log lines from test-runs."
    :individual-href [(str api-base "/log-entries/%s")
                      :id]
    :related-resources {:test-run
                        [(str api-base "/test-runs/%s")
                         :test_run_id]}
    :filter-keys #{:test_run_id|uuid :id|int :level}
    :insert-keys #{}
    :delete-keys #{:test_run_id|uuid :id|int :level}
    :routes [{:path "/"
              :methods #{:get :delete}
              :collection? true}
             {:path "/:id"
              :methods #{:get :delete}}]}
   })

(defn base
  [api-base]
  (GET "/" request
    (let [controller
          (->> api-base
            controller
            (map (fn [[k v]]
                   [k (assoc v :href
                               (resource-uri
                                 request
                                 (str api-base (:context v) "/")))]))
            (into {}))]
      
      {:status 200 :body controller})))

(defn actions
  [api-base]
  (context "/actions" []

    (GET "/" request
      {:status 200
       :body
       [{:name "run"
         :description (str "Runs a test program against "
                           "a set of config profiles")
         :parameters {:program-name {:type "string"
                                     :required true}
                      :config-profiles {:type "list<config-profile-name>"
                                        :default ["default"]
                                        :required false}}
         :returns {:test_run_id {:type "uuid"}}}]})
    
    (POST "/run" request
      (let [data (:body request)
            ;;config-profiles (:config_profiles data ["default"])
            prg-name (:program_name data)
            prg (models/ett :program :filters {:name prg-name})
            ;;[configs _] (models/samling
            ;;              :config_profile
            ;;              :filters {:program_name prg-name
            ;;                        :name config-profiles})
            configs (conj []
                      {:name "__$$$__"
                       :data (reduce merge (:env data))})
            _ (println configs)
            resp (actions/run
                   prg
                   configs
                   (:harvest_keys data))]
        (if (clojure.string/blank? (:error resp))
          {:status 201
           :body {:test_run_id resp}}
          {:status 400
           :body (:error resp)})))
    ))

(defn api [api-base]
  (context api-base []
    (base api-base)
    (actions api-base)
    (context-compile (controller api-base))
    (route/not-found nil)))

(defn laundry [api-base]
  (-> (routes (api api-base))
    wrap-json-body
    (wrap-defaults api-defaults)
    wrap-api-exception
    wrap-json
    ))

