(ns examples.project.core
  (:require
    [cljs.pprint    :refer [pprint]]
    [show.core :as show]
    [show.dom :as dom]
    [wired-show.dom :as wired]
    [wire.core :as w]
    [tools.sortable :refer [next-pos wiretap Sortable]]))

(show/defcomponent Task [component]
  (initial-state
    {:editing false})
  (render [{:as props :keys [hotspot-wire wire description]}
           {:as state :keys [editing]}]
    (wired/div hotspot-wire {:class "task"}
      description)))

(show/defcomponent Lane [component]
  (render [{:as props :keys [new? adding? adding-text items hotspot-wire wire
                             title id]}
           _]
    (dom/div {:class "lane"}
      (wired/h2 hotspot-wire title)
      (Sortable
        {:wire wire
         :group "tasks"
         :group-id id
         :item-component Task
         :items items})
      (let [lane-wire (w/lay wire :lane {:id id})]
        (if adding?
          (dom/div
            (wired/input lane-wire {:value adding-text :autoFocus "true"})
            (wired/button lane-wire "Submit"))
          (wired/p lane-wire "add new one"))))))

(def items
  "Initial project data state"
  [;; Lanes
   {:id "Todo"
    :group "projects"
    :title "Todo"
    :pos 1}
   {:id "Working"
    :group "projects"
    :title "Working"
    :pos 2}
   ;; Tasks
   {:id "b58b74a2-727d-427a-abe2-b5325f843489"
    :group "tasks"
    :parent-group-id "Todo"
    :description "Eat Chicken"
    :pos 1}
   {:id "97eb998a-8084-11e8-90ac-720001c83150"
    :group "tasks"
    :parent-group-id "Working"
    :description "Dispose of Chicken"
    :pos 2}
   ])

(defn get-item [component id]
  (->> (show/get-state component :items)
       (filter #(= id (:id %)))
       first))

(defn update-item [component id & args]
  (let [items (show/get-state component :items)
        item-idx (->> items
                   (map-indexed vector)
                   (filter #(= id (:id (second %))))
                   ffirst)]
    (apply show/update-in! component [:items item-idx] args)))

(defn add-new-item [component]
  (fn [{:as evt :keys [id value]}]
    (show/update! component :items
      conj {:id (random-uuid)
            :group "tasks"
            :description (:adding-text (get-item component id))
            :parent-group-id id
            :pos (next-pos (show/get-state component :items) "tasks" id)})
    (update-item component id assoc
      :adding? false
      :adding-text nil)))

(defn app-tap
  "App component wiretap. Responsible for updating the task data"
  [wire component]
  (w/taps wire
    [:new-lane :mouse-click]
      (fn [_]
        (show/assoc! component :adding-lane? true))
    [:new-lane :keypress-enter]
      (fn []
        (let [npos (next-pos (show/get-state component :items) "projects")
              _ (println npos)]
          (show/update! component :items
            conj {:id (random-uuid)
                  :group "projects"
                  :title (show/get-state component :new-lane-text)
                  :pos npos}))
        (show/assoc! component
           :adding-lane? false
           :new-lane-text ""))
    [:new-lane :form-change]
      (fn [{:keys [value]}]
        (show/assoc! component :new-lane-text value))
    [:lane :p :mouse-click]
      (fn [{:as evt :keys [id]}]
        (update-item component id assoc
          :adding? true
          :adding-text ""))
    [:lane :button :mouse-click] (add-new-item component)
    [:lane :keypress-enter] (add-new-item component)
    [:lane :form-change]
      (fn [{:as evt :keys [id value]}]
        (update-item component id assoc
          :adding-text value))
    :tools.sortable/sort-complete
      (fn [{:as evt :keys [item-params]}]
        (update-item component (:id item-params) (fn [_] item-params)))))

(show/defcomponent App [component]
  ;; Tap wire and add items to state. This is where we will update them
  (initial-state [{:as props :keys [wire]}]
    {:wire (wiretap (app-tap (w/wire) component))
     :items items
     :new-lane-text ""
     :adding-lane? false})
  (render [_ {:as state :keys [new-lane-text adding-lane? wire items]} ]
    (dom/div
      (Sortable
        {:group "projects"
         :items items
         :drag-axis :x
         :item-component Lane
         :container-dom-id "lanes"
         :wire wire})
      (let [wire (w/lay wire :new-lane)]
        (if adding-lane?
          (wired/input wire {:value new-lane-text :autoFocus "true"})
          (wired/p wire "Add"))))))

(show/render-to-dom
  (App)
  (.getElementById js/document "app"))
