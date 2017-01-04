(ns co.nclk.laundry.models.common
  (:require [clojure.java.jdbc :as j]
            [clj-yaml.core :as yaml]
            [cheshire.core :as json]
            )
  (:import org.postgresql.util.PGobject))

(extend-protocol j/IResultSetReadColumn
  PGobject
  (result-set-read-column [pgobj metadata idx]
    (let [type  (.getType pgobj)
          value (.getValue pgobj)]
      (case type
        "jsonb" (json/parse-string value true)
        :else value))))

(defn value-to-json-pgobject [value]
  (doto (PGobject.)
    (.setType "jsonb")
      (.setValue (json/generate-string value))))

(extend-protocol j/ISQLValue
  clojure.lang.IPersistentMap
  (sql-value [value] (value-to-json-pgobject value))

  ;;clojure.lang.IPersistentVector
  java.util.Collection
  (sql-value [value] (value-to-json-pgobject value)))

(def config
  (let [conf
        (-> "db.yaml"
          clojure.java.io/resource
          slurp
          yaml/parse-string)
        host (System/getenv "DB_HOST")]
    (if (nil? host)
      conf
      (assoc conf :host host))))

(defn filters-to-where
  [params]
  ;; where this=? and (this=? or this=?) and ...
  ;; bindings
  (let [pairs (for [[k v] params]
                (if (coll? v)
                  [(format
                     "(%s)"
                     (clojure.string/join
                       " or "
                       (for [vv v]
                         (format "%s=?" (name k)))))
                   v]
                  [(format "%s=?" (name k)) v]))]
    [(when-not (empty? pairs)
       (clojure.string/join " and " (map first pairs)))
     (flatten (map second pairs))]))

(defn query-string
  [relation count? & [where order direction limit offset]]
  (format
    "select %s from %s %s %s %s %s"
    (if count? "count(*)" "*")
    (name relation)
    (if where
      (str "where " where)
      "")
    (if order
      (format "order by %s %s"
              (name order)
              (condp = (keyword direction)
                :asc "asc"
                :desc "desc"
                ""))
      "")
    (if limit
      (format "limit %s" limit)
      "")
    (if offset
      (format "offset %s" offset)
      "")))

(defn samling
  [relation & {:keys [count?
                      filters
                      order
                      direction
                      limit
                      offset]
               :or {count? true direction "asc"}}]
  (let [[where bindings] (filters-to-where filters)
        qstr (query-string relation
               false
               where
               order
               direction
               limit
               offset)
        _ (println qstr)
        results (j/with-db-transaction [conn config]
                  [(j/query conn
                     (flatten
                       [qstr
                        bindings]))
                   (when count?
                     (-> conn
                       (j/query
                         (flatten
                           [(query-string
                              relation count? where)
                            bindings]))
                       first))])]
    results))

(defn ett
  [relation & {:keys [filters order offset]}]
  (ffirst (samling relation :count? false
                            :filters filters
                            :order order
                            :limit 1
                            :offset offset)))

(defn foga-in!
  [relation data]
  (first
    (j/with-db-transaction [conn config]
      (j/insert! conn (keyword relation)
        data))))

(defn ändra!
  [relation data & [filters]]
  (let [[where bindings] (filters-to-where filters)]
    (j/with-db-transaction [conn config]
      (j/update! conn (keyword relation)
        data
        (flatten [where bindings])))))

(defn radera!
  [relation & [filters]])
