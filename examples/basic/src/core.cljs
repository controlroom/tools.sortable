(ns examples.basic.core
  (:require [show.core :as show]
            [ctrlrm.sortable :refer [Sortable]]))

;; Debug and such
(def items (for [i (range 10)] {:id i :title (str "item-" i)}))

(show/render-to-dom
  (Sortable {:items items :item-key :id})
  (.getElementById js/document "app"))
