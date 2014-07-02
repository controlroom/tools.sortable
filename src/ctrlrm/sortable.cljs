(ns ctrlrm.sortable
  (:require [show.core      :as show  :include-macros true]
            [show.dom       :as dom   :include-macros true]
            [wire.up.dom    :refer [wire-up unwire]]
            [wire.up.show   :as wired :include-macros true]
            [wire.core      :as w]
            [goog.events    :as events]
            [goog.style     :as gstyle]

            [ctrlrm.stores :as stores]
            [ctrlrm.sortable-store :as store.sortable])

  (:import  [goog.events EventType]))

(enable-console-print!)

(show/defclass BaseRenderedItem [component]
  (render [{:keys [wire hotspot-wire] :as props} state]
    (wired/div hotspot-wire
      (wired/p wire (:title props)))))

(defn determine-pos
  "Calculate position of floating li, returning a position map. Ensuring to
  constrain x position"
  [dom-pos cursor-start-pos cursor-current-pos]
  {:x (:x dom-pos)
   :y (+ (:y dom-pos) (- (:y cursor-current-pos) (:y cursor-start-pos)))})

(defn dragging
  "Send sort-drag message up the wire with calculated position data"
  [wire evt dom-pos cursor-start-pos]
  (let [cursor-current-pos {:x (.-pageX (:event evt))
                            :y (.-pageY (:event evt))}]
    (w/act wire :sort-drag
           (determine-pos dom-pos cursor-start-pos cursor-current-pos))))

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

(defn tap-sort-item
  "Single wiretap on the li, only listening for the mousedown dom event to
  initiate the start of the sorting"
  [component wire]
  (w/tap (w/wire) :mouse-down #(start-sort component wire %)))

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
    (dom/li {:className (show/class-map {"selected" selected?})}
      (item-component (merge item-props
                             {:hotspot-wire (tap-sort-item component wire)})))))

(show/defclass FloatingItem
  "Floating display only copy of selected SortedItem. We pass in a junk wire to
  ensure that none of the events can pass"
  [component]
  (mixins stores/PlugCallbackMixin)
  (will-mount
    (show/assoc! component ::stores/cbs
     [(stores/register-changes
        store.sortable/store :dragging
        (fn [_]
          (show/assoc! component :pos
                       (store.sortable/sget :floating-pos))))]))

  (render [{:as props :keys [item-component item-props wire]}
           {:as state :keys [pos]}]
    (dom/li {:style {:opacity 0.95
                     :position "absolute"
                     :cursor "move"
                     :backgroundColor "#eee"
                     :top (:y pos)
                     :left (:x pos)}}
      (item-component (merge item-props {:hotspot-wire (w/wire)
                                         :wire (w/wire)})))))

(defn tap-sortables
  "Setup wiretaps for all the sortable actions that are passed back from the
  SortedItem components. We use the assoc! fn to update the state of the main
  Sortable component"
  [wire component]
  (w/taps wire
    :sort-start  #(store.sortable/start-sort (:id %)
                                             {:x (:x %) :y (:y %)})
    :sort-drag   #(store.sortable/update-dragging (select-keys % [:x :y]))
    :sort-end    #(store.sortable/end-sort)
    :init-offset #(store.sortable/update-offset (:id %) (:offset %))))

(defn build-sorted-items
  "Build the sequence of SortedItem components. We are also laying the items id
  so we can keep track of which sorted item is being acted upon. Notice we
  passed nil for the namespace since we have no use for it"
  [wire item-component]
  (for [id (store.sortable/sget :sorted-ids)]
    (SortedItem {:key id
                 :wire (w/lay wire nil {:id id})
                 :selected?  (store.sortable/is-selected? id)
                 :item-top   (store.sortable/sget [:offsets-by-id id])
                 :item-props (store.sortable/get-item id)
                 :item-component item-component})))

(defn build-floating-item
  "Build FloatingItem component"
  [wire props state]
  (FloatingItem {:item-props (store.sortable/selected-item)
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
  (mixins stores/PlugCallbackMixin)
  (default-props []
    {:item-component BaseRenderedItem
     :wire           (w/wire)
     :item-key       :id})
  (will-mount
    (show/assoc! component ::stores/cbs
      [(stores/register-changes
         store.sortable/store
         (fn [_] (show/force-update! component)))])
    (store.sortable/init
      (select-keys (show/get-props component) [:items :item-key])))

  (render [{:as props :keys [wire item-key item-component items]}
           {:as state :keys [sorting sorted-ids selected-id offsets-by-id]}]
    (let [items-by-id   (into {} (map (fn [item] [(get item item-key) item]) items))
          sortable-wire (tap-sortables wire component)
          sorted-list   (build-sorted-items sortable-wire item-component)
          sorted-list   (if (store.sortable/is-sorting?)
                          (conj sorted-list
                            (build-floating-item wire props state))
                          sorted-list)]
      ;; List out all the sortable items, even the selected ones
      (dom/ul {:key "list" :className "sort-list"}
        sorted-list))))
