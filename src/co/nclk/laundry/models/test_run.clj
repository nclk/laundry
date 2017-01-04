(ns co.nclk.laundry.models.test-run
  (:require [clojure.java.jdbc :as j]
            [clj-yaml.core :as yaml]
            [cheshire.core :as json]
            [co.nclk.laundry.models.common :refer [config]]
            )
  (:import org.postgresql.util.PGobject))

(defn test-runs
  []
  (j/with-db-transaction [conn config]
    (j/query conn
      ["select * from test_run"])))

(defn test-run|id
  [id & [exists?]]
  (try
    (first
      (j/with-db-transaction [conn config]
        (j/query conn
          [(if exists?
             "select count(*) from test_run where id = ?"
             "select * from test_run where id = ?")
           (java.util.UUID/fromString id)])))
    (catch IllegalArgumentException iae nil)))

