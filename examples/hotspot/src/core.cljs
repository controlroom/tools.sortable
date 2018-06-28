(ns examples.hotspot.core
  (:require [show.core :as show :include-macros true]
            [show.dom  :as dom  :include-macros true]
            [wire.up.show :as wired :include-macros true]
            [wire.core :as w]
            [ctrlrm.sortable :refer [Sortable]]))

;; Debug and such
(def items (for [i (range 10)] {:id i :title (str "item-" i)}))

(show/defcomponent HotspotItem [component]
  (initial-state
    {:counter 0})
  (did-mount
    (js/setInterval
      #(show/update-in! component [:counter] inc)
      1000))
  (render [props state]
    (wired/div (:hotspot-wire props)
      (dom/div {:class "hotspot"})
      (dom/p {:class "text"} (str (:title props) (:counter state))))))

(defn tapped []
  (w/taps (w/wire)
    {:class :sortable} #(js/console.log %)))

(show/render-to-dom
  (Sortable {:item-component HotspotItem
             :wire (tapped)
             :items items
             :item-key :id})
  (.getElementById js/document "app"))
