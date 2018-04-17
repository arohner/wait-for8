(defproject arohner/wait-for8 "1.0.5"
  :description "wait-for, using java8 time instead of joda"
  :license {:name "Eclipse Public License 1.0"
            :url "https://www.eclipse.org/legal/epl-v10.html"
            :year 2017
            :key "epl-1.0"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [clojure.java-time "0.3.0"]
                 [slingshot "0.12.2"]]
  :profiles {:dev {:dependencies [[circleci/bond "0.3.0"]]
                   :exclusions [org.clojure/clojurescript
                                com.cemerick/clojurescript.test]}}
  :deploy-repositories [["releases" :clojars]])
