(ns co.nclk.laundry.actions
  (:require [clojure.java.jdbc :as j]
            [clj-yaml.core :as yaml]
            [clojure.tools.logging.impl :as logp]
            [clojure.tools.logging :refer [log *logger-factory*]]
            [co.nclk.linen.core :as linen]
            ;[co.nclk.linen.connector.http :refer [connector]]
            [co.nclk.linen.connector.handler :refer [connector]]
            [co.nclk.laundry.models.common :as models]
            ))

;(def ctor (connector))

(def dbconfig models/config)

(def testrun-map
  (atom {}))

(defn remove-test! [id]
  (swap! testrun-map
    #(dissoc % id)))

(defn handlerfn
  [r]
  (fn [s]
    (let [rs (j/with-db-transaction [conn dbconfig]
               (j/query conn
                 [(format "select * from %s where name=?" r)
                  s]))]
      (when-not (empty? rs)
        (-> rs first :data)))))

(def ctor
  (connector
    (handlerfn "program")
    (handlerfn "module")))

(defn run [program configs & [hkeys seed]]
  (let [seed (or seed (.getTime (java.util.Date.)))
        laundry-config (when-let [laundry-file
                                  (clojure.java.io/resource "laundry.yaml")]
                         (-> laundry-file slurp yaml/parse-string))
        default-config {:env (or (:env laundry-config {}))
                        :effective seed
                        :harvest (or hkeys [])
                        :log-checkpoints? true
                        :data-connector ctor
                        :genv (atom
                                (into {}
                                  (for [[k v] (System/getenv)]
                                    [(keyword k) v])))
                        :merge-global-environment true
                        :program (:data program)}
        config
        (loop [args configs
               config (merge laundry-config default-config)]
          (if (empty? args)
            config
            (recur
              (drop 1 args)
              (let [env (:env config)
                    arg (first args)]
                (assoc config :env
                              (merge env
                                     (linen/evaluate
                                       {(-> arg :name keyword) (-> arg :data)}
                                       config)))))))
        test-run-id (java.util.UUID/randomUUID)]
    (println "Evaluated environment:")
    (clojure.pprint/pprint config)
    (j/with-db-transaction [conn dbconfig]
      (j/insert! conn :test_run
        {:id test-run-id
         :seed seed
         :program_name (:name program)
         :env (:env config)}))
    (let [thread
          (Thread.
            (fn []
              (try
                (let [logger
                      (reify clojure.tools.logging.impl.Logger
                        (enabled? [self level] true)
                        (write! [self level throwable message]
                          (j/with-db-transaction [conn dbconfig]
                            (if (= level :checkpoint)
                              (j/insert! conn :checkpoint
                                {:id (-> message :runid)
                                 :test_run_id test-run-id
                                 :success (-> message :success :value true?)
                                 :data message})
                              (if throwable
                                ;; FIXME not sure what "throwable" means
                                ;; in this context but we never get here:
                                (j/insert! conn :log_entry
                                  {:test_run_id test-run-id
                                   :level (name level)
                                   :message (str "throwable: " message)})
                                (j/insert! conn :log_entry
                                  {:test_run_id test-run-id
                                   :level (name level)
                                   :message (or message "[empty]")}))))))
                      lfactory
                      (reify clojure.tools.logging.impl.LoggerFactory
                        (get-logger [self namesp]
                          logger)
                        (name [self] "dblogger"))]
                  (binding [*logger-factory* lfactory]
                    (let [idx (atom 0)
                          return (try (linen/run config)
                                      (catch Exception e
                                        (loop [e e]
                                          (when-not (nil? e)
                                            (log :fatal (.getMessage e))
                                            (log :trace
                                                 (with-out-str
                                                   (clojure.pprint/pprint
                                                     (.getStackTrace e))))
                                            (recur (.getCause e))))))]

                      (doseq [hkey (-> config :harvest)]
                        (when-let [data (linen/harvest return hkey)]
                          (j/with-db-transaction [conn dbconfig]
                            (j/insert! conn :harvest
                              {:name hkey
                               :test_run_id test-run-id
                               :data data}))))

                      (let [checkpoints (linen/returns return)]
                        (j/with-db-transaction [conn dbconfig]
                          (j/update! conn :test_run
                            {:status (if checkpoints "complete" "interrupted")
                             :num_checkpoints (count checkpoints)
                             :num_failures (->> checkpoints
                                             (filter
                                               #(-> % :success :value false?))
                                             count)}
                            ["id = ?" test-run-id]))

                        (j/with-db-transaction [conn dbconfig]
                          (j/insert! conn :raw_result
                            {:test_run_id test-run-id
                             :raw return})))
                      (remove-test! (str test-run-id))
                      nil)))
                (catch Exception ie
                  (remove-test! (str test-run-id))
                  (j/with-db-transaction [conn dbconfig]
                    (j/update! conn :test_run
                      {:status "error"}
                      ["id = ?" test-run-id]))))))]
      (j/with-db-transaction [conn dbconfig]
        (j/update! conn :test_run
          {:status "running"}
          ["id = ?" test-run-id]))
      (swap! testrun-map
        #(assoc % (str test-run-id) thread))
      (.start thread)
      test-run-id)))

(defn interrupt
  [testrun-id]
  (when-let [thread (-> @testrun-map (get testrun-id))]
    (.interrupt thread)))

(defn kill
  [testrun-id]
  (when-let [thread (-> @testrun-map (get testrun-id))]
    (.stop thread)
    ;; this doesn't work because linen seems to be catching all errors and
    ;; returning nil
    (j/with-db-transaction [conn dbconfig]
      (j/update! conn :test_run
        {:status "killed"}
        ["id = ?" (java.util.UUID/fromString testrun-id)]))
    (remove-test! testrun-id)))

