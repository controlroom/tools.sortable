(defproject controlroom/tools.sortable "0.1.0-SNAPSHOT"
  :description "Sortable objects using drag and drop"
  :url "https://ctrlrm.io/tools.sortable"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.329"]

                 [controlroom/wired-show "0.1.0"]]

  :plugins [[lein-cljsbuild "1.1.7"]]

  :profiles {:dev {:dependencies [[cider/piggieback "0.3.6"]
                                  [org.clojure/tools.nrepl "0.2.13"]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}}

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
     {:id "swapping"
      :source-paths ["examples/swapping/src" "src"]
      :compiler {:output-dir "examples/swapping/out"
                 :output-to  "examples/swapping/main.js"}}
     {:id "project"
      :source-paths ["examples/project/src" "src"]
      :compiler {:output-dir "examples/project/out"
                 :output-to  "examples/project/main.js"}}]})
