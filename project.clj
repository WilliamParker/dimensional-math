(defproject dimensional-math "0.0.3-SNAPSHOT"
  :description "A library for validation of units in numeric operations with compile-time switching between checked and unchecked operations."
  :url "https://github.com/WilliamParker/dimensional-math"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.apache.directory.studio/org.apache.commons.collections "3.2.1"]]
  :source-paths ["src/main/clojure"]
  :profiles {:dev {:dependencies [[org.clojure/math.combinatorics "0.0.9"]
                                  ;; TODO: Replace the combinatorics functions.  The newest math.combinatorics release requires that combinations be calculated on sets
                                  ;; with no equal elements, while the tests here that use these functions require that combinations be calculated based on object
                                  ;; equality.  Note that the units library does not create this behavior; it would result if the combinatorics functions were used
                                  ;; on simple Java numerics with repeated numbers as well.
                                  [org.clojure/test.check "0.7.0"]]
                   :plugins [[codox "0.8.10"]]
                   :global-vars {*assert* true}
                   :injections [(require 'wparker.dimensional-math.core)
                                (intern 'wparker.dimensional-math.core '*compile-with-unit-checks* true)]}})
