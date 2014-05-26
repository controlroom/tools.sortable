(defproject controlroom/ctrlrm.sortable "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2202"]]

  :plugins [[lein-cljsbuild "1.0.3"]]

  :source-paths ["src"]
  :cljsbuild
   {:builds
    [{:id "basic"
      :source-paths ["examples/basic/src" "src"]
      :compiler {:output-dir "examples/basic/out"
                 :output-to  "examples/basic/main.js"
                 :optimizations  :none
                 :output-wrapper false
                 :source-map     true }}]})
