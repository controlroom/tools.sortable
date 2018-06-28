(ns examples.trello.core
  (:require
    [show.core :as show]
    [show.dom :as dom]
    [wire.core :as w]
    [ctrlrm.sortable :refer [Sortable]]))

(show/defcomponent Task [component]
  (initial-state
    {:editing false})
  (render [{:as props :keys [wire description]}
           {:as state :keys [editing]}]
    (dom/div {:class "task"}
      description)))

(show/defcomponent Lane [component]
  (render [{:as props :keys [wire title items]} _]
    (dom/div {:class "lane"}
      (dom/h2 title)
      (Sortable
        {:name :project-task
         :group-id :project-tasks
         :item-component Task
         :items items}))))

(def items
  "Initial trello data state"
  [{:id 1 :title "Todo" :items []}
   {:id 2 :title "Working on" :items []}
   {:id 3 :title "Done" :items []}])

(defn app-tap
  "App component wiretap. Responsible for updating the task data"
  [wire component]
  (w/taps wire))

(show/defcomponent App [component]
  ;; Allow a wire to be passed in as props
  (default-props
    {:wire (w/wire)})
  ;; Tap wire and add items to state. This is where we will update them
  (initial-state [{:as props :keys [wire]}]
    {:wire (app-tap wire)
     :items items})
  (render [{:as props :keys [items]}
           {:as state :keys [wire]} ]
    (Sortable
      {:name :project-lane
       :items items
       :item-component Lane
       :item-key :id
       :wire wire})))

(show/render-to-dom
  (App)
  (.getElementById js/document "app"))

(comment
  ;; tools.sortable
  ;; Dragula / Trello as uber examples

  ;; - Nested sortables
  ;; - Multiple Containers driven by data
  ;; - Sortable options to allow for Dragula features
  ;;
  ;; - Grid Mode
  ;; - List Mode
  ;;
  (Sortable
    {;; Debug & identifing
     :name "type of sortable"

     ;; Grouping (allow for multiple
     :group-id "identifier to allow items to traverse between sortables"

     ;; Modes (interchangable between groups)
     :mode "list or grid (config varies based)"

     :items "vector of hash-maps"
     :item-key "keyword that identifies identifier"
     :item-compnent "A single component that data flows to"
     :wire "wire to tap on sorting events"
     }
    )
  )

