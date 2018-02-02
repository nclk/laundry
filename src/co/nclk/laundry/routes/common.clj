(ns co.nclk.laundry.routes.common
  (:require [compojure.core :refer :all]
            [compojure.coercions :refer :all]
            [co.nclk.laundry.models.common :refer [samling
                                                   ett
                                                   foga-in!
                                                   ändra!
                                                   ]]
            ))

(defn collect*
  [relation]
  (fn [& [filters
          {:keys [order
                  per-page
                  offset
                  page
                  direction]
           :or {page "1"
                per-page "50"}
           :as params}]]
    (let [page (as-int page)
          page (if (pos? page)
                 page 1)
          per-page (as-int per-page)
          offset (or (and offset (as-int offset))
                     (* (dec page) per-page))
          results
          (samling relation
                   :filters filters
                   :order order
                   :direction direction
                   :limit per-page
                   :offset offset)
          total (-> results second :count)
          next* (when (> total (* page per-page))
                  (inc page))
          previous (when (> page 1)
                     (dec page))
          last* (int (Math/ceil (/ total per-page)))]
      {:next next*
       :previous previous
       :last last*
       :count total
       :per-page per-page
       :current page
       :order order
       :direction direction
       :results (first results)})))

(defn one*
  [relation]
  (fn [& [filters]]
    (ett relation :filters filters
                  :limit 1)))

(defn insert!*
  [relation]
  (fn [data]
    (foga-in! relation data)))

(defn update!*
  [relation]
  (fn [data filters]
    (ändra! relation data filters)))

(defn data-access-fns
  [relation]
  {:collect (collect* relation)
   :one (one* relation)
   :insert! (insert!* relation)
   :update! (update!* relation)
   :delete! (fn [])})

(defn coerce-select-params
  [filter-keys params]
  (let [filters (for [f filter-keys]
                  (let [[k t] (map keyword
                                (-> f
                                  name
                                  (clojure.string/split #"\|" 2)))]
                    [k t]))
        params (for [[k v] params
                     [f t] filters]
                 (when (= k f)
                   [k (condp = t
                        :int (as-int v)
                        :uuid (as-uuid v)
                        :bool (= "true" v)
                        v)]))]
    (into {} (remove nil? params))))

(defn resource-uri
  [request & [endpoint]]
  (str (or (-> request :headers (get "x-original-protocol"))
           (-> request :scheme name))
       "://"
       (or (-> request :headers (get "x-original-host"))
           (-> request :headers (get "host")))
       endpoint))

(defn related-resources
  [resource controller request]
  (let [related (:related-resources controller)
        ind-href (:individual-href controller)
        format-fun
        (fn [fks]
          (apply format
                 (flatten
                   [(first fks)
                    (map #(% resource) (drop 1 fks))])))]
    (assoc resource
      :href (resource-uri request (format-fun ind-href))
      :related-resources
      (into {}
        (for [[relation fks] related]
          [relation
           (resource-uri request (format-fun fks))])))))
 
(defn method-handlers
  [request
   controller
   route*
   method
   params
   body
   {:keys [collect one insert! update! delete!]}]
  (condp = method
    :get
    (GET (:path route*) {params :params}
      (let [filters (coerce-select-params
                      (:filter-keys controller)
                      params)
            resp (if (:collection? route*)
                   (collect filters params)
                   (one filters))]
        (if (contains? resp :results)
          {:status 200 :body (assoc resp
                               :results
                               (map #(related-resources
                                      %
                                      controller
                                      request)
                                    (:results resp)))}
          (when-not (nil? resp)
            {:status 200 :body (related-resources
                                 resp
                                 controller
                                 request)}))))
    :post
    (POST (:path route*) {params :params}
      (let [ikeys (:insert-keys controller)
            data (coerce-select-params
                   (:insert-keys controller)
                   body)]
        (when (not= (set (keys body)) (set ikeys))
          (throw (co.nclk.laundry.APIException.
                   (format
                     (str "Posts to %s must contain values "
                          "for each of (and only) %s.")
                     (:uri request)
                     ikeys))))
        {:status 201
         :body (-> data
                 insert!
                 (related-resources
                   controller
                   request))}
      ))
    :put
    (PUT (:path route*) {params :params}
      (let [ukeys (:update-keys controller)
            filters (coerce-select-params
                      (:filter-keys controller)
                      params)
            data (coerce-select-params
                   (:insert-keys controller)
                   body)]
        (when-not (every? #(contains? (set ukeys) %)
                         (set (keys body)))
          (throw (co.nclk.laundry.APIException.
                   (format
                     (str "Puts to %s can only contain values "
                          "from %s.")
                     (:uri request)
                     ukeys))))
        {:status 200
         :body (update! data filters)}
      ))
    :delete
    (DELETE (:path route*) []
      {:status 501})))

(defn context-compile
  [ctrl]
  (apply routes
    (for [[label controller] ctrl]
      (context (:context controller) request
        (let [params (:params request)
              body (:body request)]
          (->>
            (for [route* (:routes controller)
                  method (:methods route*)]
              (method-handlers
                request
                controller
                route*
                method
                params
                body
                (data-access-fns
                  (:relation controller))))
            flatten
            (apply routes)))))))
