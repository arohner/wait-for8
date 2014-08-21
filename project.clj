(defproject arohner/wait-for "1.0.1"
  :description "a HOF for specifying retries"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-time "0.8.0"]
                 [circle/util "1.0.0"]]
  :profiles {:dev
             {:dependencies
              [[midje "1.5.0" :exclusions [org.clojure/clojure]]
               [bond "0.2.5" :exclusions [org.clojure/clojure]]]}}
  :plugins [[lein-midje "3.0.0"]])
