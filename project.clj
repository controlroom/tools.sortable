(defproject controlroom/tools.sortable "0.1.0-SNAPSHOT"
  :description "Sortable objects using drag and drop"
  :url "https://ctrlrm.io/tools.sortable"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.329"]

                 [controlroom/wire "0.2.1"]
                 [controlroom/show "0.8.0-SNAPSHOT"]]

  :plugins [[lein-cljsbuild "1.1.7"]]

  :source-paths ["src"]
  :cljsbuild
   {:builds
    [{:id "basic"
      :source-paths ["examples/basic/src" "src"]
      :compiler {:output-dir "examples/basic/out"
                 :output-to  "examples/basic/main.js"}}
     {:id "hotspot"
      :source-paths ["examples/hotspot/src" "src"]
      :compiler {:output-dir "examples/hotspot/out"
                 :output-to  "examples/hotspot/main.js"}}
     {:id "updated"
      :source-paths ["examples/updated/src" "src"]
      :compiler {:output-dir "examples/updated/out"
                 :output-to  "examples/updated/main.js"}}
     {:id "project"
      :source-paths ["examples/project/src" "src"]
      :compiler {:output-dir "examples/project/out"
                 :output-to  "examples/project/main.js"}}]})
