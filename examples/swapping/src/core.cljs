(ns examples.swapping.core
  (:require
    [show.core :as show]
    [show.dom :as dom]
    [wire.up.show :as wired]
    [wire.core :as w]
    [tools.sortable :refer [wiretap Sortable]]))


(def my-wire (wiretap (w/wire)))

(def items
  [{:title "box1"
    :group "boxes"
    :parent-group-id "boxes-1"
    :id "abc1"
    :height 100
    :pos 1}
   {:title "box2"
    :group "boxes"
    :parent-group-id "boxes-1"
    :height 20
    :id "abc2"
    :pos 2}
   {:title "box3"
    :group "boxes"
    :parent-group-id "boxes-2"
    :height 30
    :id "abc3"
    :pos 3}
   {:title "box4"
    :group "boxes"
    :parent-group-id "boxes-2"
    :height 40
    :id "abc4"
    :pos 4}
   ])

(show/defcomponent Item [component]
  (render [{:keys [wire hotspot-wire height] :as props} state]
    (wired/div hotspot-wire {:style {:height height}}
      (wired/p wire (:title props)))))

(show/defcomponent App [component]
  (render [_ _]
    (dom/div {:id "boxes-wrap"}
      (Sortable
        {:wire my-wire
         :group "boxes"
         :group-id "boxes-1"
         :items items
         :item-component Item
         :container-dom-id "boxes-1"})
      (Sortable
        {:wire my-wire
         :group "boxes"
         :group-id "boxes-2"
         :items items
         :item-component Item
         :container-dom-id "boxes-2"}))))

(show/render-to-dom
  (App)
  (.getElementById js/document "app"))

