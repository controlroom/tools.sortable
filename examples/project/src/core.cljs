(ns examples.project.core
  (:require
    [show.core :as show]
    [show.dom :as dom]
    [wire.up.show :as wired]
    [wire.core :as w]
    [tools.sortable :refer [Sortable]]))

(show/defcomponent Task [component]
  (initial-state
    {:editing false})
  (render [{:as props :keys [wire description]}
           {:as state :keys [editing]}]
    (dom/div {:class "task"}
      description)))

(show/defcomponent Lane [component]
  (render [{:as props :keys [hotspot-wire wire title items]} _]
    (dom/div {:class "lane"}
      (wired/h2 hotspot-wire title)
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
  ;; (w/taps wire)
  wire
  )

(show/defcomponent App [component]
  ;; Allow a wire to be passed in as props
  (default-props
    {:wire (w/wire)})
  ;; Tap wire and add items to state. This is where we will update them
  (initial-state [{:as props :keys [wire]}]
    {:wire (app-tap wire component)
     :items items})
  (render [_ {:as state :keys [wire items]} ]
    (Sortable
      {:name :project-lane
       :items items
       :item-component Lane
       :item-key :id
       :container-id "lanes"
       :wire wire})))

(show/render-to-dom
  (App)
  (.getElementById js/document "app"))
