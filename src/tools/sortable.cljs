(ns tools.sortable
  "Things to experiment with:
     - Group wire. Build a wire that can be passed into Sortables that
   will keep track of the refs. This might be even better!"
  (:require
    [cljs.pprint    :refer [pprint]]
    [show.core      :as show]
    [show.dom       :as dom]
    [wire.up.dom    :refer [wire-up unwire]]
    [wire.up.show   :as wired]
    [wire.core      :as w]
    [goog.object    :as gobj]
    [goog.math      :as gmath]
     goog.math.Vec2
    [goog.style     :as gstyle])

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

(defn stop-sort
  "Send sort-end message up the wire and detach temporary wire from window. We
  can reference the original calling wire since it is passed into the callback
  in the wiretap"
  [wire evt]
  (w/act wire ::sort-ended)
  (unwire (::wire.core/wire evt) js/window))

(defn vec-from-mouse-event [evt]
  (goog.math.Vec2. (.-movementX evt) (.-movementY evt)))

(defn dragging
  "Send sort-dragging message up the wire with position data"
  [wire {:as evt :keys [event]} cursor-movement item-bounds]
  (let [cursor-pos (goog.math.Vec2. (.-pageX event) (.-pageY event))
        movement (goog.math.Vec2. (.-movementX event) (.-movementY event))]
    (.translate cursor-movement movement)
    (.translate item-bounds movement)
    (w/act wire ::sort-dragging {::bounds item-bounds
                                 ::cursor-pos cursor-pos})))

(defn start-sort
  "Initiates beginning of sort with sort-start wire message and creates and
  attaches a temporary wire on the window dom. Since we can't rely on the
  events passed from the current component dom object, we need to listen for
  mouse events on the window.

  This window wire has specific wiretaps to listen to global mouse events. We
  close over properties that will be useful (dom-pos, cursor-pos) when
  capturing later mouse actions"
  [component wire {:as evt :keys [event]}]
  (let [item-bounds     (gstyle/getBounds (show/get-node component))
        cursor-movement (goog.math.Vec2. 0 0)]

    (w/act wire ::sort-started {::bounds item-bounds})

    (-> (w/wire)
        (w/tap :mouse-move #(dragging wire % cursor-movement item-bounds))
        (w/tap :mouse-up   #(stop-sort wire %))
        (wire-up js/window))))

(defn tap-sort-item
  "Single wiretap on the li, only listening for the mousedown dom event to
  initiate the start of the sorting"
  [component wire]
  (w/tap (w/wire) :mouse-down #(start-sort component wire %)))

(show/defcomponent SortedItem
  "List item, acting as a wrapper that will capture mouse down click for
  sorting initialization"
  [component]
  (initial-state
    {:ref (show/create-ref)})
  (will-unmount
    (w/act (show/get-props component :wire) ::item-unmounted))
  (did-mount
    (w/act (show/get-props component :wire) ::item-mounted {::component component}))
  (render [{:as props :keys [wire item-props item-component selected?]}
           {:as state :keys [ref]}]
    (dom/li {:class {"selected" selected?} :ref ref}
      (item-component (merge item-props
                             {:wire wire
                              :hotspot-wire (tap-sort-item component wire)})))))

(show/defcomponent FloatingItem
  "Floating display only copy of selected SortedItem. We pass in a junk wire to
  ensure that none of the events can pass"
  [component]
  (render [{:as props :keys [bounds item-component item-props wire]} _]
    (dom/li {:style {:opacity 0.95
                     :position "absolute"
                     :cursor "move"
                     :backgroundColor "#eee"
                     :top  (:top bounds)
                     :left (:left bounds)}}
      (item-component (merge item-props {:hotspot-wire (w/wire)
                                         :wire (w/wire)})))))

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

(defn tap-sortables
  "Setup wiretaps for all the sortable actions that are passed back from the
  SortedItem components"
  [wire component]
  (w/taps wire
    ::sort-started
      (fn [{::keys [bounds item-id]}]
        (show/assoc! component
          :selected-id item-id
          :floating-pos {:top (.-top bounds) :left (.-left bounds)}
          :sorting? true))
    ::sort-dragging
      (fn [{::keys [bounds]}]
        (show/assoc! component :floating-pos {:top (.-top bounds) :left (.-left bounds)}))
    ::sort-ended
      (fn [evt]
        (let [{:keys [selected-id sorted-ids]} (show/get-state component)]
          (w/act wire ::sorting-ended
            {:item-id selected-id
             :sort sorted-ids})

          (show/assoc! component
            :sorting? false
            :selected-id nil)))))

(defn wiretap*
  "This wiretap is responsible for doing a ton of things. It must:

     - Coordinate all possible sortable containers and each item within it
     - Update the positions of all items during dragging
     - Inject new data into a seperate group-item when draggables have traversed borders
     - Send sort changed messages up the wire for all consumers

   We tackle things in this order:

     - Store all the components for each container and item with this wire

     On sort start:
       - Gather bounds of all items and containers affected

     - Internal sorting
     - Group management
     - Message projection"
  [wire]
  (let [collected (atom {})
        bounds-from-component #(-> % (show/get-state :ref) (gobj/get "current") gstyle/getBounds)]
    (w/taps wire
      ::container-mounted
        (fn [{::keys [group-key group-id component]}]
          (swap! collected assoc-in [group-key group-id :container] component))
      ::item-mounted
        (fn [{:as evt ::keys [group-key group-id component item-id]}]
          (swap! collected assoc-in [group-key group-id :items item-id] component))
      ::sort-started
        (fn [evt]
          (swap! current-sizes assoc :items
            (reduce
              (fn [memo [id item]]
                (assoc memo id (bounds-from-component item)))
              {}
              (:items @group-data)))
          (swap! current-sizes assoc :container (bounds-from-component (:container @group-data))))
      ::sort-dragging
        (fn [{:as evt ::keys [cursor-pos bounds]}]
          ;; (println cursor-pos)
          (let [inside-container? (.intersects bounds (:container @current-sizes)) ]
            (doseq [[id dim] (:items @current-sizes)]
              ;; (println (.distance dim cursor-pos))
              )
            ;; (println inside-container?)
            )
          ))))

(defn wiretap
  "Basic wiretap allowing for a nested tapping check to ensure children
   don't tap too much"
  [wire]
  (if (::sortable-tapped (w/data wire))
    wire
    (wiretap* (w/lay wire nil {::sortable-tapped true}))))

(show/defcomponent Sortable
  "Base sortable React/show class.

  required:
    items          - Sequence of maps containing data to pass into item-component
    item-component - show component class to render inside sorted list item

  optional:
    item-key - optional key for retrieving id from list of items (defaults to :id)
    wire     - optional wire for recieving sorting events"
  [component]

  ;; defaultProps
  ;;
  (default-props
    {:item-component BaseRenderedItem
     :wire           (w/wire)
     :item-key       :id
     :group-key      (gensym "group-key")
     :group-id       (gensym "group-id")})

  ;; initialState
  ;;
  (initial-state [{:keys [group-key group-id items wire
                          item-key sorted-ids]}]
    {;; Basic requirements
     :wire (wiretap wire)
     :sorted-ids (or sorted-ids (mapv item-key items))

     ;; Normalized items grouped by identifier. We affect this during group
     ;; item traversals
     :items-by-id {}
     :positions-by-id {}

     ;; Stored refs, for bounds lookups
     :ref (show/create-ref)

     ;; Selected states
     :floating-pos {:top 0 :left 0}
     :selected-id nil
     :sorting? false})

  ;; didMount
  ;;
  (did-mount
    (w/act (show/get-state component :wire) ::container-mounted {::component component})
    (let [{:keys [items item-key]} (show/get-props component)]
      (normalize-items component items item-key)))

  ;; willRecieveProps
  ;;
  (will-receive-props [{:keys [items sorted-ids item-key]}]
    (when (not= (show/get-props component :sorted-ids) sorted-ids)
      (show/assoc! component :sorted-ids sorted-ids))
    (when (not= (show/get-props component :items) items)
      (normalize-items component items item-key)
      (show/assoc! component :sorted-ids (mapv item-key items)) ))

  ;; Render
  ;;
  (render [{:as props :keys [items group-key group-id container-dom-id
                             container-dom-classes item-key item-component]}
           {:as state :keys [wire sorting? sorted-ids selected-id offsets-by-id ref]}]
    (let [sorted-list (for [id sorted-ids]
                        (SortedItem
                          {:key id
                           :sorting? sorting?
                           :wire (w/lay wire nil {::item-id id
                                                  ::group-key group-key
                                                  ::group-id group-id})
                           :selected? (= selected-id id)
                           :item-props (get-item-by-id component id)
                           :item-component item-component}))]
      ;; (println sorted-list)
      ;; List out all the sortable items, even the selected ones
      (dom/ul (cond-> {:key "list" :class "sort-list" :ref ref}
                container-dom-id (assoc :id container-dom-id)
                container-dom-classes (update :class #(str % " " container-dom-classes)))
        (if sorting?
          (conj sorted-list
                (FloatingItem
                  {:item-props (get-item-by-id component selected-id)
                   :wire wire
                   :item-component (:item-component props)
                   :bounds (:floating-pos state)}))
          sorted-list)))))
