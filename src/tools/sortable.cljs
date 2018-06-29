(ns tools.sortable
  "Things to experiment with:
     - Group wire. Build a wire that can be passed into Sortables that
   will keep track of the refs. This might be even better!"
  (:require
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
  "Send sort-dragging message up the wire with calculated position data"
  [wire {:as evt :keys [event]} cursor-pos item-bounds]
  (let [movement (goog.math.Vec2. (.-movementX event) (.-movementY event))]
    (.translate cursor-pos movement)
    (.translate item-bounds movement)
    (w/act wire ::sort-dragging {::bounds item-bounds})))

(defn start-sort
  "Initiates beginning of sort with sort-start wire message and creates and
  attaches a temporary wire on the window dom. Since we can't rely on the
  events passed from the current component dom object, we need to listen for
  mouse events on the window.

  This window wire has specific wiretaps to listen to global mouse events. We
  close over properties that will be useful (dom-pos, cursor-pos) when
  capturing later mouse actions"
  [component wire {:as evt :keys [event]}]
  (let [item-bounds (gstyle/getBounds (show/get-node component))
        cursor-pos  (goog.math.Vec2. 0 0)]

    (w/act wire ::sort-started {::bounds item-bounds})

    (-> (w/wire)
        (w/tap :mouse-move #(dragging wire % cursor-pos item-bounds))
        (w/tap :mouse-up   #(stop-sort wire %))
        (wire-up js/window))))

(defn tap-sort-item
  "Single wiretap on the li, only listening for the mousedown dom event to
  initiate the start of the sorting"
  [component wire]
  (w/tap (w/wire) :mouse-down #(start-sort component wire %)))

(show/defcomponent SortedItem
  "List item, acting as a wrapper that will capture mouse down click for
  sorting initialization. We also have some insane ref management required for
  looking up dom sizes during dragging transitions"
  [component]
  (initial-state
    {:item-ref (show/create-ref)})
  (did-mount
    (w/act (show/get-props component :wire) ::item-mounted {::component component}))
  (render [{:as props :keys [wire item-props item-component selected?]}
           {:as state :keys [sorting item-ref]}]
    (dom/li {:class {"selected" selected?} :ref item-ref}
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
      (fn [{::keys [bounds id]}]
        (show/assoc! component
          :selected-id id
          :selected-idx (.indexOf (clj->js (show/get-state component :sorted-ids)) id)
          :floating-pos {:top (.-top bounds) :left (.-left bounds)}
          :sorting? true))
    ::sort-dragging
      (fn [{::keys [bounds]}]
        (show/assoc! component :floating-pos {:top (.-top bounds) :left (.-left bounds)}))
    ::sort-ended
      (fn [evt]
        (let [{:keys [selected-id selected-idx sorted-ids]} (show/get-state component)]
          (w/act wire ::sorting-ended
            {:item-id selected-id
             :updated-idx selected-idx
             :sort sorted-ids})

          (show/assoc! component
            :sorting? false
            :selected-id nil
            :selected-idx nil)))))

(defn group-wire-tap
  [wire]
  (let [group-data (atom {})
        current-sizes (atom {})]
    (w/taps wire
    ::container-mounted
      (fn [{::keys [component]}]
        (swap! group-data assoc :container component))
    ::item-mounted
      (fn [{:as evt ::keys [component id]}]
        (swap! group-data assoc-in [:items id] component))
    ::sort-started
      (fn [evt]
        (swap! current-sizes assoc :container
               (-> @group-data
                 :container
                 (show/get-state :ref)
                 (gobj/get "current")
                 gstyle/getBounds)))
    ::sort-dragging
      (fn [{:as evt ::keys [bounds]}]
        (println (.intersects bounds (:container @current-sizes)))))))

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
     :item-key       :id})

  (initial-state [{:keys [wire items item-key sorted-ids]}]
    {:wire (tap-sortables wire component)
     :sorted-ids (or sorted-ids (mapv item-key items))

     ;; Stored refs, for bounds lookups
     :ref (show/create-ref)

     ;; Dimentions
     :dimentions-by-id {}

     ;; Heights
     :height-seq []
     :height-by-id {}
     ;; Widths
     :width-seq []
     :width-by-id {}

     ;; Selected states
     :floating-pos {:top 0 :left 0}
     :selected-idx nil
     :selected-id nil
     :sorting? false})

  (did-mount
    (w/act (show/get-state component :wire) ::container-mounted {::component component})
    (let [{:keys [items item-key]} (show/get-props component)]
      (normalize-items component items item-key)))

  (will-receive-props [{:keys [items sorted-ids item-key]}]
    (when (not= (show/get-props component :sorted-ids) sorted-ids)
      (show/assoc! component :sorted-ids sorted-ids))
    (when (not= (show/get-props component :items) items)
      (normalize-items component items item-key)))

  (render [{:as props :keys [container-id container-classes item-key item-component]}
           {:as state :keys [wire sorting? sorted-ids selected-id offsets-by-id ref]}]
    (let [sorted-list (for [id sorted-ids]
                        (SortedItem {:key id
                                     :wire (w/lay wire nil {::id id})
                                     :selected? (= selected-id id)
                                     :item-props (get-item-by-id component id)
                                     :item-component item-component}))
          sorted-list (if sorting?
                        (conj sorted-list
                              (FloatingItem {:item-props (get-item-by-id component selected-id)
                                             :wire wire
                                             :item-component (:item-component props)
                                             :bounds (:floating-pos state)}))
                        sorted-list)]
      ;; List out all the sortable items, even the selected ones
      (dom/ul
        (cond-> {:key "list" :class "sort-list" :ref ref}
          container-id (assoc :id container-id)
          container-classes (update :class #(str % " " container-classes)))
        sorted-list))))
