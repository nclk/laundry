(ns co.nclk.laundry.models.config-profile
  (:require [clojure.java.jdbc :as j]
            [clj-yaml.core :as yaml]
            [cheshire.core :as json]
            [co.nclk.laundry.models.common :refer [config ändra!]]
            )
  (:import org.postgresql.util.PGobject))

(defn samling
  []
  (j/with-db-transaction [conn config]
    (j/query conn
      ["select * from config_profile"])))

(defn ett:program-name+name
  [prg-name name*]
  (first
    (j/with-db-transaction [conn config]
      (j/query conn
        [(str "select * from config_profile "
              "where program_name = ? and name = ?")
         prg-name name*]))))

(defn samling:program-name+names
  [prg-name names]
  (j/with-db-transaction [conn config]
    (j/query conn
      (concat [(str "select * from config_profile "
                    "where program_name = ? and ("
                    (clojure.string/join
                      " or "
                      (repeat (count names)
                              "name = ?"))
                    ")")]
              (flatten [prg-name names])))))

(defn samling:program-name
  [prg-name]
  (j/with-db-transaction [conn config]
    (j/query conn
      [(str "select * from config_profile "
            "where program_name = ?")
       prg-name])))

(defn update!
  [prg-name name* data]
  (ändra! :config_profile
          (select-keys data [:program_name
                             :name])))

(defn update!
  [prg-name name* data]
  (j/with-db-transaction [conn config]
    (j/update! conn :config_profile
      data
      ["name = ? and program_name = ?" name* prg-name])))


