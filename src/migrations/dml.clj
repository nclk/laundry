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
  (doto (PGobject.)
    (.setType "jsonb")
      (.setValue (json/generate-string value))))

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

(defn migrate-dir [db-config rel dir]
  (let [sources (-> dir ;; e.g., "linen/modules"
                    clojure.java.io/resource
                    clojure.java.io/file
                    .listFiles)]
    (doseq [m sources]
      (let [nm (-> m .getName (clojure.string/replace #".yaml$" ""))]
        (try
          (j/with-db-transaction [conn db-config]
            (j/insert! conn rel
              (let [contents (-> m slurp yaml/parse-string)
                    prototype
                    {:name nm
                     :data (if (:data contents)
                             (:data contents)
                             contents)}]
                (if (:documentation contents)
                  (assoc prototype :documentation
                         (:documentation contents))
                  prototype)))
            (log :info (str "source \"" nm "\" inserted.")))
          (catch org.postgresql.util.PSQLException psqle
            (log :warn (str "source \""
                            nm
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
              (concat programs (reduce go programs (.listFiles f)))
              (let [dir (.getParentFile f)
                    files (.listFiles dir)
                    program (get-file-by-name "program.yaml" files)]
                (when program
                  (conj programs
                        [dir program])))))
          #{}
          (.listFiles f))]
    (doseq [[parent program] programs]
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
            (doseq [cp config-profiles]
              (j/insert! conn :config_profile_program_map
                {:program program-name
                 :config_profile cp})))
          (catch org.postgresql.util.PSQLException psqle
            (log :warn (str "source \""
                            program-name
                            "\" not inserted: " (.getMessage psqle)))
            ))))))

(defn -main [host & [direction]]
  (if (= direction "down")
    (rollback (config host))
    (migrate (config host))))

