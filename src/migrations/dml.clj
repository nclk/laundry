(ns migrations.dml
  (:require [clojure.java.jdbc :as j]
            [cheshire.core :as json]
            [clj-yaml.core :as yaml]
            [clojure.tools.logging :refer [log]])
  (:import org.postgresql.util.PGobject))

(def config*
  (-> "db.yaml"
    clojure.java.io/resource
    slurp
    yaml/parse-string))

(println config*)

(defn config [host]
  (assoc config* :host host))

(extend-protocol j/IResultSetReadColumn
  PGobject
  (result-set-read-column [pgobj metadata idx]
    (let [type  (.getType pgobj)
          value (.getValue pgobj)]
      (case type
        "jsonb" (json/parse-string value true)
        :else value))))

(defn value-to-json-pgobject [value]
  (let [value (get value :pg-json value)]
    (doto (PGobject.)
      (.setType "jsonb")
        (.setValue (json/generate-string value)))))

(extend-protocol j/ISQLValue
  clojure.lang.IPersistentMap
  (sql-value [value] (value-to-json-pgobject value))

  clojure.lang.IPersistentVector
  (sql-value [value] (value-to-json-pgobject value)))

(defn rollback [config]
  (j/with-db-transaction [conn config]
    (j/delete! conn :program [])
    ;(j/delete! conn :test_run [])
    ;(j/delete! conn :log_entry [])
    ;(j/delete! conn :checkpoint [])
    (j/delete! conn :module [])
    ;(j/delete! conn :raw_result [])
    (j/delete! conn :config_profile_program_map [])
    (j/delete! conn :config_profile [])
    ))

(defn maybe-column
  [prototype col-name contents]
  (if (col-name contents)
    (assoc prototype col-name (col-name contents))
    prototype))

(defn migrate-dir [db-config rel dir]
  (let [sources (-> dir ;; e.g., "linen/modules"
                    clojure.java.io/resource
                    clojure.java.io/file
                    .listFiles)]
    (doseq [m sources]
      (let [nm (-> m .getName (clojure.string/replace #".yaml$" ""))]
        (try
          (doseq [contents (-> m slurp yaml/parse-all)]
            (let [nm (get contents :name nm)]
              (j/with-db-transaction [conn db-config]
                (j/insert! conn rel
                  (let [prototype
                        {:name nm
                         :data {:pg-json (get contents :data contents)}}]
                    (-> prototype
                      (maybe-column :documentation contents)
                      (maybe-column :meta contents))))
                (log :info (str (name rel) " \"" nm "\" inserted.")))))
          (catch org.postgresql.util.PSQLException psqle
            (log :warn (str (name rel)
                            " \""
                            (-> m .getName)
                            "\" not inserted: " (.getMessage psqle)))))))))

(defn get-file-by-name
  [name files]
  (->> files
    (filter #(= (.getName %) name))
    first))

(defn migrate
  [db-config]
  (migrate-dir db-config :module "linen/modules")
  (migrate-dir db-config :config_profile "linen/config-profiles")
  (let [f (-> "linen/programs"
              clojure.java.io/resource
              clojure.java.io/file)
        programs
        (reduce
          (fn go [programs f]
            (if (.isDirectory f)
              (clojure.set/union
                programs
                (reduce go programs (.listFiles f)))
              (if-not (-> f .getName (.contains ".yaml"))
                programs
                (conj programs f))))
          #{}
          (.listFiles f))]
    ;;(println programs) (System/exit 0)
    (doseq [program programs]
      (let [data (-> program slurp yaml/parse-string)
            program-name (:name data)
            documentation (:documentation data)
            config-profiles (:config-profiles data)]
        (try
          (j/with-db-transaction [conn db-config]
            (j/insert! conn :program
              {:name program-name
               :data data
               :documentation documentation})
            (log :info
              (str "program " "\"" program-name "\" inserted."))
            (doseq [cp config-profiles]
              (j/insert! conn :config_profile_program_map
                {:program program-name
                 :config_profile cp})
              (log :info
                (str "config_profile_program_map "
                     "\"" program-name "/" cp "\" inserted."))))
          (catch org.postgresql.util.PSQLException psqle
            (log :warn (str "source \""
                            program-name
                            "\" not inserted: " (.getMessage psqle)))
            ))))))

(defn -main [host & [direction]]
  (if (= direction "down")
    (rollback (config host))
    (migrate (config host))))

