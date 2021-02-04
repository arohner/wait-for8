(defproject sh.griffin/wait-for8 "1.0.7-SNAPSHOT"
  :description "wait-for, using java8 time instead of joda"
  :url "https://github.com/griffinbank/wait-for8"
  :license {:name "Eclipse Public License 1.0"
            :url "https://www.eclipse.org/legal/epl-v10.html"
            :year 2017
            :key "epl-1.0"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [clojure.java-time "0.3.0"]
                 [slingshot "0.12.2"]]
  :profiles {:dev {:dependencies [[circleci/bond "0.3.0"]
                                  [org.clojure/test.check "1.1.0"]]
                   :exclusions [org.clojure/clojurescript
                                com.cemerick/clojurescript.test]}}

  :plugins [[s3-wagon-private "1.3.4"]]

  ;; this isn't actually no-auth, it uses the standard AWS auth mechanisms
  :repositories [["releases" {:url "s3p://griffin-maven-development" :no-auth true}]
                 ["snapshots" {:url "s3p://griffin-maven-development" :no-auth true}]]
  )
