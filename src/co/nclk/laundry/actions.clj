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

(defn run [program configs & [hkeys]]
  (let [seed (.getTime (java.util.Date.))
        config
        (loop [args configs
               config {:env {}
                       :effective seed
                       :harvest (or hkeys [])
                       :log-checkpoints? true
                       :data-connector ctor
                       :genv (atom
                               (into {}
                                 (for [[k v] (System/getenv)]
                                   [(keyword k) v])))
                       :merge-global-environment true
                       :program (:data program)}]
          (if (empty? args)
            config
            (recur
              (drop 1 args)
              (assoc config :env
                            (merge (:env config)
                                   (linen/evaluate
                                     (-> args first :data)
                                     config))))))
        test-run-id (java.util.UUID/randomUUID)]
    (println "Evaluated environment:")
    (clojure.pprint/pprint config)
    (j/with-db-transaction [conn dbconfig]
      (j/insert! conn :test_run
        {:id test-run-id
         :seed seed
         :program_name (:name program)
         :env (:env config)}))
    ;;(log :checkpoint "hello world") (Thread/sleep 10000) (System/exit 0)
    (future
      (let [logger
            (reify clojure.tools.logging.impl.Logger
              (enabled? [self level] true)
              (write! [self level throwable message]
                (j/with-db-transaction [conn dbconfig]
                  (if (= level :checkpoint)
                    ;;;
                    (do #_(println (type message) (type (into {} message)) (-> message :success))
                    #_(System/exit 0)
                    (j/insert! conn :checkpoint
                      {:id (-> message :runid)
                       :test_run_id test-run-id
                       :success (-> message :success :value true?)
                       :data message}))
                    (if throwable
                      ;; FIXME not sure what "throwable" means
                      ;; in this context but we never get here:
                      (j/insert! conn :log_entry
                        {:test_run_id test-run-id
                         :level "fatal"
                         :message "lalalala"})
                      (j/insert! conn :log_entry
                        {:test_run_id test-run-id
                         :level (name level)
                         :message message}))))
                ))
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
                                  (recur (.getCause e))
                                  ))))]

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
                  {:status "complete"
                   :num_checkpoints (count checkpoints)
                   :num_failures (->> checkpoints
                                   (filter
                                     #(-> % :success :value false?))
                                   count)}
                  ["id = ?" test-run-id]))

              (j/with-db-transaction [conn dbconfig]
                (j/insert! conn :raw_result
                  {:test_run_id test-run-id
                   :raw return})
                #_(doseq [[idx checkpoint] (map-indexed vector checkpoints)]
                  (j/insert! conn :checkpoint
                    {:idx idx
                     :test_run_id test-run-id
                     :success (-> checkpoint :success :value true?)
                     :data checkpoint}))))

            nil))))
    (j/with-db-transaction [conn dbconfig]
      (j/update! conn :test_run
        {:status "running"}
        ["id = ?" test-run-id]))
    test-run-id
    ))

