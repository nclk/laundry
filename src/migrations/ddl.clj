(ns migrations.ddl
  (:require [clj-yaml.core :as yaml]
            [ragtime.jdbc :refer [sql-database
                                  load-resources]]
            [ragtime.repl :refer [migrate rollback]]))

(defn config [host]
  {:datastore (sql-database (-> "db.yaml"
                              clojure.java.io/resource
                              slurp
                              yaml/parse-string
                              (assoc :host host)))
   :migrations (load-resources "migrations")})

(defn -main [host & [direction steps]]
  (let [steps (or steps "1")]
    (if (= direction "down")
      (loop [steps (Integer/parseInt steps)]
        (when (pos? steps)
          (rollback (config host))
          (recur (dec steps))))
      (migrate (config host)))))

