(ns co.nclk.laundry.models.checkpoint
  (:require [clojure.java.jdbc :as j]
            [clj-yaml.core :as yaml]
            [cheshire.core :as json]
            [co.nclk.laundry.models.common :refer [config]]
            )
  (:import org.postgresql.util.PGobject))

(defn checkpoints|test-run-id
  [id]
  (try
    (j/with-db-transaction [conn config]
      (j/query conn
        ["select * from checkpoint where test_run_id = ?"
         (java.util.UUID/fromString id)]))
    (catch IllegalArgumentException iae nil)))

(defn checkpoint|id
  [id]
  (try
    (first
      (j/with-db-transaction [conn config]
        (j/query conn
          ["select * from checkpoint where id = ?"
           (java.util.UUID/fromString id)])))
    (catch IllegalArgumentException iae nil)))

