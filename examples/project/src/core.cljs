(ns examples.project.core
  (:require
    [show.core :as show]
    [show.dom :as dom]
    [wire.up.show :as wired]
    [wire.core :as w]
    [tools.sortable :refer [wiretap Sortable]]))

(show/defcomponent Task [component]
  (initial-state
    {:editing false})
  (render [{:as props :keys [wire description]}
           {:as state :keys [editing]}]
    (dom/div {:class "task"}
      description)))

(show/defcomponent Lane [component]
  (render [{:as props :keys [items hotspot-wire wire title id]} _]
    (dom/div {:class "lane"}
      (wired/h2 hotspot-wire title)
      (Sortable
        {:group "tasks"
         :group-id id
         :item-component Task
         :items items}))))

(def items
  "Initial project data state"
  [{:id "165e5651-3a3f-4f87-afba-724ab90ee528" :group "projects" :title "A" :pos 0}
   {:id "f7c83429-0565-4602-bd9b-fcdbe87f8b9e" :group "projects" :title "B" :pos 1}
   {:id "24788a8c-6e52-43b5-a527-3794138c668f" :group "projects" :title "C" :pos 2}
   ;; Project Items
   {:id "b58b74a2-727d-427a-abe2-b5325f843489"
    :group "tasks"
    :parent-group-id "165e5651-3a3f-4f87-afba-724ab90ee528"
    :description "Eat Chicken"
    :pos 0}
   ])

(defn app-tap
  "App component wiretap. Responsible for updating the task data"
  [wire component]
  ;; (w/taps wire)
  wire
  )

(show/defcomponent App [component]
  ;; Allow a wire to be passed in as props
  (default-props
    {:wire (wiretap (w/wire))})
  ;; Tap wire and add items to state. This is where we will update them
  (initial-state [{:as props :keys [wire]}]
    {:wire (app-tap wire component)
     :items items})
  (render [_ {:as state :keys [wire items]} ]
    (Sortable
      {:group "projects"
       :items items
       :drag-axis :x
       :item-component Lane
       :container-dom-id "lanes"
       :wire wire})))

(show/render-to-dom
  (App)
  (.getElementById js/document "app"))
