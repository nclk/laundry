(ns co.nclk.laundry.models.log-entry
  (:require [clojure.java.jdbc :as j]
            [clj-yaml.core :as yaml]
            [cheshire.core :as json]
            [co.nclk.laundry.models.common :refer [config]]
            )
  (:import org.postgresql.util.PGobject))

(defn log-entries|test-run-id
  [id]
  (try
    (j/with-db-transaction [conn config]
      (j/query conn
        ["select * from log_entry where test_run_id = ? order by created"
         (java.util.UUID/fromString id)]))
    (catch IllegalArgumentException iae nil)))

