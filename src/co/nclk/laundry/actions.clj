(ns co.nclk.laundry.actions
  (:require [clojure.java.jdbc :as j]
            [clj-yaml.core :as yaml]
            [cheshire.core :as json]
            [clojure.tools.logging.impl :as logp]
            [clojure.tools.logging :refer [log *logger-factory*]]
            [clojure.core.async :as a]
            [co.nclk.linen.core :as linen]
            ;[co.nclk.linen.connector.http :refer [connector]]
            [co.nclk.linen.connector.handler :refer [connector]]
            [co.nclk.laundry.models.common :as models]
            ))


(defn pret
  [x]
  (println x)
  x)


(def dbconfig models/config)

(def laundry-config
  (when-let [laundry-file (clojure.java.io/resource "laundry.yaml")]
    (-> laundry-file slurp yaml/parse-string)))

(def testrun-map
  (atom {}))

(defn add-test! [id config]
  (swap! testrun-map #(assoc % id config)))
(defn remove-test! [id]
  (swap! testrun-map #(dissoc % id)))
(defn get-test [id]
  (-> @testrun-map (get id)))



(defn test-status!
  [status test-run-id]
  (j/with-db-transaction [conn dbconfig]
    (j/update! conn :test_run
      {:status status}
      ["id = ?" test-run-id])))


;;;; Channels

(def job-queue (a/chan))

(defn do-job!
  [{:keys [job test-run-id config]}]
  (add-test! test-run-id (assoc config :thread (Thread/currentThread)))
  (test-status! "running" test-run-id)
  (job)
  (remove-test! test-run-id))

(defn add-job-thread
  []
  (a/thread
    (loop []
      (when-let [job-config (a/<!! job-queue)]
        (do-job! job-config)
        (recur)))))

(def thread-pool
  ;; defaults to 1
  (doseq [_ (range 0 (-> laundry-config (:run-concurrency 1)))]
    (add-job-thread)))
;;;;

(defn handlerfn
  [modules]
  (fn [s]
    (yaml/parse-all
      (if-let [data (-> s keyword modules)]
        data
        (let [rs (j/with-db-transaction [conn dbconfig]
                   (j/query conn
                     ["select * from module where name=?" s]))]
          (-> rs first :src))))))


(defn merge-evaluated-configs
  [configs config]
  (if (empty? configs)
    config
    (recur
      (drop 1 configs)
      (let [env (:env config)
            arg (first configs)]
        (assoc config :env
                      (merge env
                             (linen/evaluate
                               {(-> arg :name) (-> arg :data)}
                               config)))))))


(defn get-logger
  [test-run-id]
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
               :message (or message "[empty]")})))))))


(defn get-logger-factory
  [test-run-id]
  (let [logger (get-logger test-run-id)]
    (reify clojure.tools.logging.impl.LoggerFactory
      (get-logger [self namesp] logger)
      (name [self] "dblogger"))))


(defn log-run-exception!
  [e]
  (when-not (nil? e)
    (log :fatal (.getMessage e))
    (log :trace
         (with-out-str
           (clojure.pprint/pprint
             (.getStackTrace e))))
    (recur (.getCause e))))


(defn do-run
  [config]
  (try (linen/run config)
    (catch Exception e
      (log-run-exception! e))))


(defn harvest!
  [config return test-run-id]
  (doseq [hkey (-> config :harvest)]
    (when-let [data (linen/harvest return hkey)]
      (j/with-db-transaction [conn dbconfig]
        (j/insert! conn :harvest
          {:name hkey
           :test_run_id test-run-id
           :data data})))))


(defn process-checkpoints!
  [return test-run-id config]
  ;(let [checkpoints (linen/harvest return :checkpoint)]
  (j/with-db-transaction [conn dbconfig]
    (let [checkpoints
          (j/query conn
                   ["select * from checkpoint where test_run_id = ?" test-run-id])]
      (j/update! conn :test_run
        {:status (if @(:runnable? config) "complete" "interrupted")
         :num_checkpoints (count checkpoints)
         :num_failures (->> checkpoints
                         (filter
                           #(-> % :success false?))
                         count)}
        ["id = ?" test-run-id]))))


(defn raw-result!
  [return test-run-id]
  (j/with-db-transaction [conn dbconfig]
    (j/insert! conn :raw_result
      {:test_run_id test-run-id
       :raw return})))


(defn test-run!
  [test-run-id seed program-name env]
  (j/with-db-transaction [conn dbconfig]
    (j/insert! conn :test_run
      {:id test-run-id
       :seed seed
       :program_name program-name
       :env env})))


(defn run-with-logger
  [test-run-id config]
  (try
    (let [lfactory (get-logger-factory test-run-id)]
      (binding [*logger-factory* lfactory]
        (let [return (do-run config)]
          (harvest! config return test-run-id)
          (process-checkpoints! return test-run-id config)
          (raw-result! return test-run-id)
          nil)))
    (catch Exception ie
      (test-status! "error" test-run-id)
      (log-run-exception! ie))
    (finally (remove-test! (str test-run-id)))))


(defn default-config
  [seed hkeys program main modules]
  {:env {}
   :runnable? (atom true)
   :effective (long seed)
   :harvest (or hkeys [])
   :log-checkpoints? true
   :main (yaml/parse-all ((keyword main) modules))
   :data-connector (connector
                     (fn [s] nil) ;; no program resolution anymore
                     (handlerfn modules))
   :genv (fn []
           (into {}
             (for [[k v] (System/getenv)]
               [(keyword k) v])))
   :merge-global-environment false
   :program (:data program)})


(defn run [program main modules & [hkeys seed]]
  (let [seed (or seed (.getTime (java.util.Date.)))
        config (merge (default-config seed hkeys program main modules) laundry-config)
        test-run-id (java.util.UUID/randomUUID)]

    (println "Seed:" seed)
    (println "Evaluated environment:")
    (clojure.pprint/pprint config)

    (println "Modules:")
    (doseq [m modules] (println (val m)))

    (test-run! test-run-id seed (:name program) modules)

    (test-status! "pending" test-run-id)
    (a/go (a/>! job-queue {:job #(run-with-logger test-run-id config)
                           :config config
                           :test-run-id test-run-id}))
    test-run-id))


(defn interrupt
  [testrun-id]
  (when-let [config (get-test testrun-id)]
    (test-status! "interrupting" testrun-id)
    (swap! (:runnable? config) (fn [_] false))
    ))

(defn kill
  [testrun-id]
  (test-status! "killing" testrun-id)
  (when-let [config (get-test testrun-id)]
    (swap! (:runnable? config) (fn [_] false))
    (when-let [thread (-> config :thread)]
      (.interrupt thread)
      
      (test-status! "killed" testrun-id)))
  (remove-test! testrun-id))

