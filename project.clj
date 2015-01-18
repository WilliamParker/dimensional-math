(defproject units "0.0.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/test.check "0.6.2"]]
  :source-paths ["src/main/clojure"]
  :java-source-paths ["src/main/java"]
  :global-vars {*assert* true
                *warn-on-reflection* true}
  :profiles {:test {:dependencies [[org.clojure/math.combinatorics "0.0.8"]]}})
