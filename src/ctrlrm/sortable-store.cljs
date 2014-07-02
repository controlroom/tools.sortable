(ns ctrlrm.sortable-store
  (:require [ctrlrm.stores :as stores]))

(def store (stores/create-store))
(defn sortable [] (stores/get-data store))
(defn emit-topic [topic]
  (stores/emit-change store topic))
(defn emit []
  (stores/emit-change store))

(defn get-data [] @(sortable))
(defn sget [key]
  (if (sequential? key)
    (get-in @(sortable) key)
    (get    @(sortable) key)))

(defn sup! [& args]
  (apply swap! (sortable) args))

(defn start-sort [selected-id floating-pos]
  (sup! assoc
       :selected-id selected-id
       :selected-idx (.indexOf (clj->js (sget :sorted-ids)) selected-id)
       :floating-pos floating-pos
       :sorting true)
  (emit))

(defn end-sort []
  (sup! assoc :sorting false)
  (emit))

(defn get-item [id]
  (sget [:items-by-id id]))

(defn is-sorting? []
  (sget :sorting))

(defn is-selected? [id]
  (and (is-sorting?)
       (= id (sget :selected-id))))

(defn selected-item []
  (when (is-sorting?)
    (get-item (sget :selected-id))))

(defn inject-items [items]
  (sup! assoc
       :items-by-id (into {}
                          (map (fn [item]
                                 [(get item (sget :item-key)) item])
                               items))))

(defn- extract-at-position
  "return new sequence with item removed at index"
  [index list]
  (let [[i v] (split-at index list)]
    (concat i (rest v))))

(defn- inject-at-position
  "return new sequence with item injected into index"
  [list item index]
  (let [[i v] (split-at (- index 1) list)]
    (concat i [item] v)))

(defn- swap-item
  "return new sequence with item swapped into new index"
  [sorted-list item index]
  (-> (.indexOf (clj->js sorted-list) item)
      (extract-at-position sorted-list)
      (inject-at-position item index)))

(defn update-dragging [floating-pos]
  (sup! assoc :floating-pos floating-pos)
  (emit-topic :dragging)
  (let [[idx _] (split-with (partial >= (:y floating-pos))
                            (sget :height-seq))
        idx (count idx)]
    (when (not= (dec idx) (sget :selected-idx))
      (sup! assoc     :selected-idx (dec idx))
      (sup! update-in [:sorted-ids]
           (fn [old]
             (swap-item old (sget :selected-id) idx)))
      (emit))))

(defn update-offset [id offset]
  (sup! assoc-in [:offsets-by-id id] offset)
  ;; Calculate height-seq after we have stored offset
  (sup! assoc
        :height-seq (for [id (sget :sorted-ids)]
                      (sget [:offsets-by-id id]))))

(defn init [{:keys [items item-key]}]
  (reset! (sortable)
          {:sorted-ids    (map item-key items)
           :item-key      item-key
           :height-seq    []
           :offsets-by-id {}
           :floating-pos  {:x 0 :y 0}
           :selected-idx  nil
           :selected-id   nil
           :sorting       false})
  (inject-items items)
  (emit))
