(ns examples.updated.core
  "Problem:

  We have a list of items that have a stored position property. We would
  like our sortable to respond and allow us to update this property. We
  would also like to dictate where we place a new item in the sortable list.
  This can be a complicated problem.

  The sorted-ids list is kept seperate so it can be manipulated from
  outside the Sortable component. This allows for inserting new items
  within a currently sorted list.

  We have 3 places that keep track of sort:

  - The DOM representation of the objects
    - Sortable keeps track of this for you. It will let you know when
      it changes
  - Your internal sort state (located in the items map)
    - You will update this via callbacks from Sortable, but the current
      DOM state is not necessecarly affected by this property after
      first load.
  - Local atom state
    - This is the object that we keep in sync with Sortable. When we want
      to change the sort state (or add an item) we update this atom and then
      pass it into Sortable"
  (:require
    [show.core :as show]
    [show.dom  :as dom]
    [wire.up.show :as wired]
    [wire.core :as w]
    [tools.sortable :refer [Sortable]]))

;; Debug and such
(def items
  (atom (for [i (range 10)]
          {:id i
           :title (str "item-" i)
           :position i
           :height (+ 10 (rand-int 80))})))

(def sorted-ids
  (atom []))

(defn pop-list [n coll]
  (let [taken (take n @coll)]
    (swap! coll (partial drop n))
    taken))

(defn add-item [component]
  (let [item (first (pop-list 1 items))]
    ;; Add item to sortable list
    (show/swap!
      component
      (fn [state]
        (-> state
            (update-in [:items] #(conj % item))
            (assoc :sorted-ids (swap! sorted-ids #(conj % (:id item)))))))))

(defn base-wire [component]
  (w/taps (w/wire)
    :mouse-click
    (fn [] (add-item component))
    ::tools.sortable/sorting-endeds
    (fn [data]
      (reset! sorted-ids (:sort data)))))

(show/defcomponent UpdatedItem [component]
  (render [props state]
    (wired/div (:hotspot-wire props) {:style {:height (:height props)}}
      (dom/p {:class "text"}
        (str (:title props))))))

(show/defcomponent App [component]
  (initial-state
    (let [recs (pop-list 5 items)]
      {:items recs
       :sorted-ids (reset! sorted-ids (mapv :id recs)) }))
  (render [props state]
    (let [wire (base-wire component)]
      (dom/div
        (wired/a wire "add one")
        (Sortable {:wire wire
                   :item-component UpdatedItem
                   :items (:items state)
                   :sorted-ids (:sorted-ids state)
                   :item-key :id})))))

(show/render-to-dom
  (App)
  (.getElementById js/document "app"))
