(ns examples.hotspot.core
  (:require [show.core :as show :include-macros true]
            [show.dom  :as dom  :include-macros true]
            [wire.up.show :as wired :include-macros true]
            [wire.core :as w]
            [ctrlrm.sortable :refer [Sortable]]))

;; Debug and such
(def items (for [i (range 10)] {:id i :title (str "item-" i)}))

(show/defclass HotspotItem [component]
  (initial-state
    {:counter 0})
  (will-mount
    (js/setInterval
      #(if (.isMounted component) (show/update-in! component [:counter] inc))
      1000))
  (render [props state]
    (wired/div (:hotspot-wire props)
      (dom/div {:className "hotspot"})
      (dom/p {:className "text"} (str (:title props) (:counter state))))))


(defn tapped []
  (w/taps (w/wire)
    {:class :sortable} #(js/console.log %)))

(show/render-component
  (Sortable {:item-component HotspotItem
             :wire (tapped)
             :items items
             :item-key :id})
  (.getElementById js/document "app"))
