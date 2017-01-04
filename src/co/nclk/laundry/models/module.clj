(ns co.nclk.laundry.models.module
  (:require [clojure.java.jdbc :as j]
            [clj-yaml.core :as yaml]
            [cheshire.core :as json]
            [co.nclk.laundry.models.common :refer [config]]
            )
  (:import org.postgresql.util.PGobject))

(defn ett:name
  [mname]
  (first
    (j/with-db-transaction [conn config]
      (j/query conn
        [(str "select * from module "
              "where name = ?")
         mname]))))

