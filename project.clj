(defproject arohner/wait-for8 "1.0.0"
  :description "wait-for, using java8 time instead of joda"
  :dependencies [[org.clojure/clojure "1.9.0-RC1"]
                 [clojure.java-time "0.3.0"]
                 [slingshot "0.12.2"]]
  :profiles {:dev {:dependencies [[circleci/bond "0.3.0"]]
                   :exclusions [org.clojure/clojurescript
                                com.cemerick/clojurescript.test]}})
