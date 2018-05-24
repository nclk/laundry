(ns migrations.dml
  (:require [clojure.java.jdbc :as j]
            [cheshire.core :as json]
            [clj-yaml.core :as yaml]
            [clojure.tools.logging :refer [log]]
            [co.nclk.linen.core :as linen])
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
    (j/delete! conn :module [])
    ))

(defn maybe-column
  [prototype col-name contents]
  (if (col-name contents)
    (assoc prototype col-name (col-name contents))
    prototype))


(defn name-from-file
  [f rel]
  (-> (.getAbsolutePath f)
      (clojure.string/split (re-pattern (str "linen/" (name rel) "s/")))
      last
      (clojure.string/replace #"(\.yaml|\.yaml\.meta)$" "")))


(defn module
  [src [{name* :name
         provides :provides
         requires :requires} & body]]
  {:name name*
   :src src})


(defn program
  [src [{name* :name
         main :main
         documentation :documentation} & body]]
  {:name name*
   :main main
   :documentation {:pg-json documentation}})


(defn module-dependency-list
  [conn tree module]
  (if-let [src (-> conn (j/query ["select src from module where name = ?" module])
                        first :src)]
    (let [[_ & body] (linen/normalize-module (yaml/parse-all src))
          modules (->> (linen/harvest body :module)
                       (filter string?)
                       (remove (set (conj tree module))))]
      (if (empty? modules)
        (conj tree module)
        (reduce (partial module-dependency-list conn) (conj tree module) modules)))
    tree))


(defn migrate-dir [db-config rel dir]
  (let [sources* (->> dir ;; e.g., "linen/modules"
                      clojure.java.io/resource
                      clojure.java.io/file
                      .listFiles)
        sources (remove #(re-matches #".*\.meta$" (.getName %)) sources*)
        metas (filter #(re-matches #".*\.meta$" (.getName %)) sources*)]
    (doseq [m sources]
      (if (.isDirectory m)
        (migrate-dir db-config rel (str dir "/" (.getName m)))
        (let [file-name (name-from-file m rel)]
          (try
            (let [src (slurp m)
                  contents (yaml/parse-all src)
                  [{name* :name} & _] contents]
              (if-not (= name* file-name)
                (log :error (str "module not inserted: filename \""
                                 file-name
                                 "\" does not match module name \""
                                 name* "\"."))
                (j/with-db-transaction [conn db-config]
                  (j/insert! conn rel
                    (condp = rel
                      :module (module src contents)
                      :program (program src contents)))

                  (j/update! conn :module
                     {:dependencies (module-dependency-list conn [] name*)}
                     ["name = ?" name*])

                  (log :info (str (name rel) " inserted \"" name* "\"."))
                  (when-let [metaf (->> metas (filter #(= (name-from-file % rel) name*)) first)]
                    (let [meta* (-> metaf slurp yaml/parse-string)]
                      (j/update! conn rel
                        {:meta {:pg-json meta*}}
                        ["name = ?" name*])
                      (log :info (str "meta updated for " (name rel) " \"" name* "\"."))))
                  )))
            (catch org.postgresql.util.PSQLException psqle
              (log :warn (str (name rel)
                              " \""
                              (-> m .getAbsolutePath)
                              "\" not inserted: " (.getMessage psqle))))))))
    ))


(defn migrate
  [db-config]
  (migrate-dir db-config :module "linen/modules")
  (migrate-dir db-config :program "linen/programs"))


(defn -main [host & [direction]]
  (if (= direction "down")
    (rollback (config host))
    (migrate (config host))))

