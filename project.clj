(defproject co.nclk/laundry "3.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 ;;[co.nclk/linen "3.0.0-SNAPSHOT"]
                 ;[co.nclk/linen "2.1.6-SNAPSHOT"]
                 [co.nclk/linen "4.0.0-SNAPSHOT"]
                 [co.nclk/clj-yaml "1.0.0"]
                 [org.fusesource.jansi/jansi "1.11"]
                 [ring/ring-core "1.5.0"]
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-jetty-adapter "1.5.0"]
                 [cheshire "5.8.0"]
                 [compojure "1.5.1"]
                 [org.clojure/java.jdbc "0.7.0-alpha1"]
                 [org.postgresql/postgresql "9.4.1212"]
                 [ragtime "0.6.0"]
                 [com.novemberain/pantomime "2.10.0"]
                 [hiccup "1.0.5"]
                 [org.clojure/core.async "0.2.385"]
                 ]
  :plugins [[lein-ring "0.9.7"]]
  :ring {:handler co.nclk.laundry.core/main}
  :aliases {"ddl" ["run" "-m" "migrations.ddl"]
            "dml" ["run" "-m" "migrations.dml"]}
  :aot [co.nclk.laundry.APIException])

