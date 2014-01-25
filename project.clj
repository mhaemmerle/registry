(defproject registry "0.1.0-SNAPSHOT"
  :description "Simple registry based on ZooKeeper"
  :url "https://github.com/mhaemmerle/registry"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/mhaemmerle/registry"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.slf4j/slf4j-api "1.6.6"]
                 [org.slf4j/slf4j-log4j12 "1.6.6"]
                 [org.clojure/tools.logging "0.2.3"]
                 [zookeeper-clj "0.9.2"]
                 [cheshire "4.0.3"]]
  :dev-dependencies [[criterium "0.2.1"]]
  :plugins [[lein-marginalia "0.7.1"]]
  :jvm-opts ["-server"
             "-Djava.awt.headless=true"])
