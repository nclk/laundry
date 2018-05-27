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
  [src name*]
  {:name name*
   :src src})


(defn dependency-list
  [conn rel tree name*]
  (if-let [src (-> conn (j/query [(str "select src from " (name rel) " where name = ?")
                                  name*])
                        first :src)]
    (let [[_ & body] (linen/normalize-module (yaml/parse-all src))
          modules (->> (linen/harvest body :module)
                       (filter string?)
                       (remove (set (conj tree name*))))]
      (if (empty? modules)
        (conj tree name*)
        (reduce (partial dependency-list conn :module) (conj tree name*) modules)))
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
                (log :error (str (name rel) " not inserted: filename \""
                                 file-name
                                 "\" does not match " (name rel)  " name \""
                                 name* "\"."))
                (j/with-db-transaction [conn db-config]
                  (j/insert! conn rel
                    (condp = rel
                      :module (module src contents)
                      :program (program src name*)))
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
  (migrate-dir db-config :program "linen/programs")
  (doseq [rel [:module :program]]
    (j/with-db-transaction [conn db-config]
      (doseq [{name* :name} (j/query conn [(str "select name from " (name rel))])]
        (j/update! conn rel
           {:dependencies (dependency-list conn rel [] name*)}
           ["name = ?" name*])))))


(defn -main [host & [direction]]
  (if (= direction "down")
    (rollback (config host))
    (migrate (config host))))

