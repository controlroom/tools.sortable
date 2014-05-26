(ns ctrlrm.sortable
  (:require [show.core      :as show  :include-macros true]
            [show.dom       :as dom   :include-macros true]
            [wire.up.dom    :refer [wire-up unwire]]
            [wire.up.show   :as wired :include-macros true]
            [wire.core      :as w]
            [goog.events    :as events]
            [goog.style     :as gstyle])
  (:import  [goog.events EventType]))

(show/defclass BaseRenderedItem [component]
  (render [{:keys [wire] :as props} state]
    (wired/p wire (:title props))))

(defn extract-at-position
  "return new sequence with item removed at index"
  [index list]
  (let [[i v] (split-at index list)]
    (concat i (rest v))))

(defn inject-at-position
  "return new sequence with item injected into index"
  [list item index]
  (let [[i v] (split-at (- index 1) list)]
    (concat i [item] v)))

(defn swap-item
  "return new sequence with item swapped into new index"
  [sorted-list item index]
  (-> (.indexOf (clj->js sorted-list) item)
      (extract-at-position sorted-list)
      (inject-at-position item index)))

(defn determine-pos
  "Calculate position of floating li, returning a position map. Ensuring to
  constrain x position"
  [dom-pos cursor-start-pos cursor-current-pos]
  {:x (:x dom-pos)
   :y (+ (:y dom-pos) (- (:y cursor-current-pos) (:y cursor-start-pos)))})

(defn dragging
  "Send sort-drag message up the wire with calculated position data"
  [wire evt dom-pos cursor-start-pos]
  (let [cursor-current-pos {:x (.-pageX (:event evt)), :y (.-pageY (:event evt))}]
    (w/act wire :sort-drag (determine-pos dom-pos cursor-start-pos cursor-current-pos))))

(defn stop-sort
  "Send sort-end message up the wire and detach temporary wire from window. We
  can reference the original calling wire since it is passed into the callback
  in the wiretap"
  [wire evt]
  (w/act wire :sort-end)
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
    (w/act wire :sort-start (determine-pos dom-pos cursor-pos cursor-pos))

    (-> (w/wire)
        (w/tap :mouse-move #(dragging wire % dom-pos cursor-pos))
        (w/tap :mouse-up   #(stop-sort wire %))
        (wire-up js/window))))

(def base-item-style
  {:cursor "pointer"
   :user-select "none"
   :-webkit-user-select "none"})

(defn tap-sort-item
  "Single wiretap on the li, only listening for the mousedown dom event to
  initiate the start of the sorting"
  [component wire]
  (w/tap wire :mouse-down #(start-sort component wire %)))

(show/defclass SortedItem
  "List item, acting as a wrapper that will capture mouse down click for
  sorting initialization. Sending up the items y top offset after it is
  mounted"
  [component]
  (did-mount []
    (let [node (show/get-node component)]
      (w/act (show/get-props component :wire) :init-offset
           {:offset (- (.-y (gstyle/getPosition node))
                       (/ (.-height (gstyle/getSize node)) 2))})))
  (render [{:as props :keys [wire item-props item-component selected?
                             item-top]}
           {:as state :keys [sorting]}]
    (wired/li (tap-sort-item component wire)
              {:className (show/class-map {"selected" selected?})
               :style base-item-style}
      (item-component (merge item-props {:wire wire})))))

(show/defclass FloatingItem
  "Floating display only copy of selected SortedItem. We pass in a junk wire to
  ensure that none of the events can pass"
  [component]
  (render [{:keys [item-component item-props wire pos]} state]
    (dom/li {:style (merge base-item-style
                           {:opacity 0.95
                            :position "absolute"
                            :cursor "move"
                            :backgroundColor "#eee"
                            :top (:y pos)
                            :left (:x pos)})}
      (item-component (merge item-props {:wire (w/wire)})))))

(defn tap-sortables
  "Setup wiretaps for all the sortable actions that are passed back from the
  SortedItem components. We use the assoc! fn to update the state of the main
  Sortable component"
  [wire component]
  (w/taps wire
    :sort-start  (fn [o] (doto component
                           (show/assoc! :selected-id (:id o))
                           (show/assoc! :floating-pos {:x (:x o) :y (:y o)})
                           (show/assoc! :sorting true)))
    :sort-drag   #(show/assoc! component :floating-pos %)
    :sort-end    #(show/assoc! component :sorting false)
    :init-offset #(show/assoc! component [:offsets-by-id (:id %)] (+ (:offset %)))))

(defn build-sorted-items
  "Build the sequence of SortedItem components. We are also laying the items id
  so we can keep track of which sorted item is being acted upon. Notice we
  passed nil for the namespace since we have no use for it"
  [wire sorted-ids items-by-id item-component selected-id sorting offsets-by-id]
  (for [id sorted-ids]
    (let [item (get items-by-id id)]
      (SortedItem {:key id
                   :wire (w/lay wire nil {:id id})
                   :selected? (and sorting (= id selected-id))
                   :item-top (get offsets-by-id id)
                   :item-props item
                   :item-component item-component}))))

(defn build-floating-item
  "Build FloatingItem component"
  [wire props state]
  (FloatingItem {:item-props (get (:items-by-id state) (:selected-id state))
                 :wire wire
                 :item-component (:item-component props)
                 :pos (:floating-pos state)}))

(show/defclass Sortable
  "Base sortable React/show class.

  required:
    items          - Sequence of maps containing data to pass into item-component
    item-component - show component class to render inside sorted list item

  optional:
    item-key - optional key for retrieving id from list of items (defaults to :id)
    wire     - optional wire for recieving sorting events"
  [component]
  (default-props []
    {:item-component BaseRenderedItem
     :item-key       :id})
  (initial-state []
    (let [{:keys [wire item-key items]} (show/get-props component)
          sorted-ids (map item-key items)]
      {:wire           (or wire (w/wire))
       :sorted-ids     sorted-ids
       :height-seq     []
       :offsets-by-id  (into {} (map (fn [id] [id nil]) sorted-ids))
       :items-by-id    (into {} (map (fn [item] [(get item item-key) item]) items))
       :floating-pos   {:x 0 :y 0}
       :selected-idx   nil
       :selected-id    nil
       :sorting        false}))
  (did-update [prev-props prev-state]
    ;; Update height sequence when new offsets are passed
    (let [offsets (show/get-state component :offsets-by-id)]
      (if (not= (:offsets-by-id prev-state) offsets)
        (show/assoc! component :height-seq
                     (for [id (show/get-state component :sorted-ids)]
                       (get offsets id)))))
    ;; Reshuffle sorted-ids vector when dragged past neighbor thresholds
    (let [floating-pos (show/get-state component :floating-pos)]
      (if (not= (:floating-pos prev-state) floating-pos)
        (let [[idx _] (split-with (partial >= (:y floating-pos))
                                  (show/get-state component :height-seq))
              idx (count idx)
              id (show/get-state component :selected-id)]
          (if (not= idx (show/get-state component :selected-idx))
            (show/update! component :sorted-ids
                          (fn [old] (swap-item old id idx))))))))
  (render [{:as props :keys [item-component]}
           {:as state :keys [wire sorting sorted-ids items-by-id
                             selected-id offsets-by-id]}]
    (let [sortable-wire (tap-sortables wire component)
          sorted-list   (build-sorted-items sortable-wire sorted-ids items-by-id
                                            item-component selected-id sorting
                                            offsets-by-id)
          sorted-list   (if sorting
                          (conj sorted-list
                            (build-floating-item wire props state))
                          sorted-list)]
      ;; List out all the sortable items, even the selected ones
      (dom/ul {:key "list" :className "sort-list"}
        sorted-list))))
