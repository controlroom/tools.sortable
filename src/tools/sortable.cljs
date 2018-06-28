(ns tools.sortable
  (:require
    [show.core    :as show]
    [show.dom     :as dom]
    [wire.up.dom  :refer [wire-up unwire]]
    [wire.up.show :as wired]
    [wire.core    :as w]
    [goog.events  :as events]
    [goog.style   :as gstyle])

  (:import
    [goog.events EventType]))

(enable-console-print!)

;; Utility functions
;;

(defn get-item-by-id [component id]
  (show/get-state component [:items-by-id id]))

(defn normalize-items [component items item-key]
  (show/assoc! component :items-by-id
               (into {} (map (fn [item]
                               [(get item item-key) item])
                             items))))

;; Sortable
;;
(show/defcomponent BaseRenderedItem [component]
  (render [{:keys [wire hotspot-wire] :as props} state]
    (wired/div hotspot-wire
      (wired/p wire (:title props)))))

(defn determine-pos
  "Calculate position of floating li, returning a position map. Ensuring to
  constrain x position"
  [dom-pos cursor-start-pos cursor-current-pos]
  {:x (+ (:x dom-pos) (- (:x cursor-current-pos) (:x cursor-start-pos)))
   :y (+ (:y dom-pos) (- (:y cursor-current-pos) (:y cursor-start-pos)))})

(defn dragging
  "Send sort-dragging message up the wire with calculated position data"
  [wire evt dom-pos cursor-start-pos]
  (let [cursor-current-pos {:x (.-pageX (:event evt))
                            :y (.-pageY (:event evt))}]
    (w/act wire :sort-dragging
           (determine-pos dom-pos cursor-start-pos cursor-current-pos))))

(defn stop-sort
  "Send sort-end message up the wire and detach temporary wire from window. We
  can reference the original calling wire since it is passed into the callback
  in the wiretap"
  [wire evt]
  (w/act wire :sort-ended)
  (unwire (::wire.core/wire evt) js/window))

(defn start-sort
  "Initiates beginning of sort with sort-start wire message and creates and
  attaches a temporary wire on the window dom. Since we can't rely on the
  events passed from the current component dom object, we need to listen for
  mouse events on the window.

  This window wire has specific wiretaps to listen to global mouse events. We
  close over properties that will be useful (dom-pos, cursor-pos) when
  capturing later mouse actions"
  [component wire evt]
  (let [g-pos      (gstyle/getPosition (show/get-node component))
        m-event    (:event evt)
        dom-pos    {:x (.-x g-pos), :y (.-y g-pos)}
        cursor-pos {:x (.-pageX m-event), :y (.-pageY m-event)}]
    (w/act wire :sort-started (determine-pos dom-pos cursor-pos cursor-pos))

    (-> (w/wire)
        (w/tap :mouse-move #(dragging wire % dom-pos cursor-pos))
        (w/tap :mouse-up   #(stop-sort wire %))
        (wire-up js/window))))

(defn tap-sort-item
  "Single wiretap on the li, only listening for the mousedown dom event to
  initiate the start of the sorting"
  [component wire]
  (w/tap (w/wire) :mouse-down #(start-sort component wire %)))

(show/defcomponent SortedItem
  "List item, acting as a wrapper that will capture mouse down click for
  sorting initialization. Sending up the items y top offset after it is
  mounted"
  [component]
  (did-mount
    (let [node (show/get-node component)]
      (js/setTimeout
        (fn [_]
          (let [wire (show/get-props component :wire)
                y (.-y (gstyle/getPosition node))
                height (.-height (gstyle/getSize node))]
            ;; Old
            (w/act wire :init-height {:height height})
            ;; New
            (w/act wire :init-dimensions {:size (gstyle/getSize node)
                                          :position (gstyle/getPosition node)})
            (println "item mounted y: " y " height: " height)))
        50)))
  (render [{:as props :keys [wire item-props item-component selected?]}
           {:as state :keys [sorting]}]
    (dom/li {:class {"selected" selected?}}
      (item-component (merge item-props
                             {:wire wire
                              :hotspot-wire (tap-sort-item component wire)})))))

(show/defcomponent FloatingItem
  "Floating display only copy of selected SortedItem. We pass in a junk wire to
  ensure that none of the events can pass"
  [component]
  (render [{:as props :keys [pos item-component item-props wire]} _]
    (dom/li {:style {:opacity 0.95
                     :position "absolute"
                     :cursor "move"
                     :backgroundColor "#eee"
                     :top (:y pos)
                     :left (:x pos)}}
      (item-component (merge item-props {:hotspot-wire (w/wire)
                                         :wire (w/wire)})))))

(defn build-sorted-items
  "Build the sequence of SortedItem components. We are also laying the items id
  so we can keep track of which sorted item is being acted upon"
  [wire component item-component]
  (let [{:keys [wire selected-id sorted-ids]} (show/get-state component)]
    (for [id sorted-ids]
      (SortedItem {:key id
                   :wire (w/lay wire nil {:id id})
                   :selected? (= selected-id id)
                   :item-props (get-item-by-id component id)
                   :item-component item-component}))))

(defn floating-item
  "Build FloatingItem component"
  [wire component selected-id props state]
  (FloatingItem {:item-props (get-item-by-id component selected-id)
                 :wire wire
                 :item-component (:item-component props)
                 :pos (:floating-pos state)}))

(defn- sort-started
  [component selected-id floating-pos]
  (show/assoc! component
    :selected-id selected-id
    :selected-idx (.indexOf (clj->js (show/get-state component :sorted-ids)) selected-id)
    :floating-pos floating-pos
    :sorting? true))

(defn- extract-at-position
  "return new sequence with item removed at index"
  [index list]
  (let [[i v] (split-at index list)]
    (concat i (rest v))))

(defn- inject-at-position
  "return new sequence with item injected into index"
  [list item index]
  (let [[i v] (split-at (- index 1) list)]
    (into [] (concat i [item] v))))

(defn- swap-item
  "return new sequence with item swapped into new index"
  [sorted-list item index]
  (-> (.indexOf (clj->js sorted-list) item)
      (extract-at-position sorted-list)
      (inject-at-position item index)))

(defn- sort-dragging
  [component floating-pos]
  (show/assoc! component :floating-pos floating-pos)
  ;; (println "pos: " (:y floating-pos) (show/get-state component :height-seq))
  (let [{:keys [height-seq selected-idx selected-id]} (show/get-state component)
        [idx _] (split-with (partial >= (:y floating-pos)) height-seq)
        idx (count idx)]
    (when (not= (dec idx) selected-idx)
      (show/assoc! component :selected-idx (dec idx))
      (show/update! component :sorted-ids #(swap-item % selected-id idx))

      (w/act (show/get-state component :wire) ::sorting-updated
        {:item-id selected-id
         :updated-idx (dec idx)
         :sort (show/get-state component :sorted-ids)}))))

(defn- sort-ended [component]
  (let [{:keys [wire selected-id selected-idx sorted-ids]} (show/get-state component)]
    (w/act wire ::sorting-ended
      {:item-id selected-id
       :updated-idx selected-idx
       :sort sorted-ids})

    (show/assoc! component
      :sorting? false
      :selected-id nil
      :selected-idx nil)))

(defn update-height [component id height]
  (show/assoc-in! component [:height-by-id id] height)
  (show/assoc! component :height-seq
               (loop [ids (show/get-state component :sorted-ids)
                      h 0
                      heights []]
                 (if-not (seq ids)
                   heights
                   (let [id (first ids)
                         height (show/get-state component [:height-by-id id])]
                     (recur
                       (rest ids)
                       (+ h height)
                       (conj heights h)))))))


(defn tap-sortables
  "Setup wiretaps for all the sortable actions that are passed back from the
  SortedItem components"
  [wire component]
  (w/taps wire
    :sort-started    #(sort-started component (:id %) {:x (:x %) :y (:y %)})
    :sort-dragging   #(sort-dragging component (select-keys % [:x :y]))
    :sort-ended      #(sort-ended component)
    :init-dimensions #(println %)
    :init-height     #(update-height component (:id %) (:height %))))


(show/defcomponent Sortable
  "Base sortable React/show class.

  required:
    items          - Sequence of maps containing data to pass into item-component
    item-component - show component class to render inside sorted list item

  optional:
    item-key - optional key for retrieving id from list of items (defaults to :id)
    wire     - optional wire for recieving sorting events"
  [component]

  (default-props
    {:item-component BaseRenderedItem
     :wire           (w/wire)
     :item-key       :id })

  (initial-state [{:keys [wire items item-key sorted-ids]}]
    {:wire (tap-sortables wire component)
     :sorted-ids (or sorted-ids (mapv item-key items))

     ;; Dimentions
     :dimentions-by-id {}

     ;; Heights
     :height-seq []
     :height-by-id {}
     ;; Widths
     :width-seq []
     :width-by-id {}

     ;; Selected states
     :floating-pos {:x 0 :y 0}
     :selected-idx nil
     :selected-id nil
     :sorting? false})

  (did-mount
    (let [{:keys [items item-key]} (show/get-props component)]
      (normalize-items component items item-key)))

  (will-receive-props [{:keys [items sorted-ids item-key]}]
    (when (not= (show/get-props component :sorted-ids) sorted-ids)
      (show/assoc! component :sorted-ids sorted-ids))
    (when (not= (show/get-props component :items) items)
      (normalize-items component items item-key)))

  (render [{:as props :keys [container-id container-classes item-key item-component]}
           {:as state :keys [wire sorting? sorted-ids selected-id offsets-by-id]}]
    (let [sorted-list (build-sorted-items wire component item-component)
          sorted-list (if sorting?
                        (conj sorted-list
                          (floating-item wire component selected-id props state))
                        sorted-list)]
      ;; List out all the sortable items, even the selected ones
      (dom/ul
        (cond-> {:key "list" :class "sort-list"}
          container-id (assoc :id container-id)
          container-classes (update :class #(str % " " container-classes)))
        sorted-list))))
