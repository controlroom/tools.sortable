(ns tools.sortable
  (:require
    [cljs.pprint    :refer [pprint]]
    [show.core      :as show]
    [show.dom       :as dom]
    [wired-show.dom :as wired :refer [wire-up unwire]]
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
(defn- get-item-by-id [component id]
  (show/get-state component [:items-by-id id]))

(defn- normalize-items [component items item-key]
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

(defn- stop-sort
  "Send sort-end message up the wire and detach temporary wire from window. We
  can reference the original calling wire since it is passed into the callback
  in the wiretap"
  [wire evt]
  (w/act wire ::sort-ended)
  (unwire (::wire.core/wire evt) js/window))

(defn- dragging
  "Send sort-dragging message up the wire with position data"
  [wire {:as evt :keys [event]} cursor-movement item-bounds]
  (let [cursor-pos (goog.math.Vec2. (.-pageX event) (.-pageY event))
        movement (goog.math.Vec2. (.-movementX event) (.-movementY event))]
    (.translate cursor-movement movement)
    (.translate item-bounds movement)
    (w/act wire ::sort-dragging {::bounds item-bounds
                                 ::cursor-movement cursor-movement
                                 ::cursor-pos cursor-pos})))

(defn- should-start-sorting?
  "Predicate to exclude specific events. Used primarily to keep contextmenu clicks
   from starting a dragging session."
  [{:as evt :keys [event]}]
  (not (or ;; Context menu click
           (= (.-button event) 2)
           (.-ctrlKey event)
           ;; Explicit ingore in attribute
           (= "true" (.getAttribute (:target evt) "ignoredrag"))
           ;; Input boxes
           (contains?
             #{"INPUT" "TEXTAREA"}
             (.-tagName (:target evt))))))

(defn- start-sort
  "Initiates beginning of sort with sort-start wire message and creates and
  attaches a temporary wire on the window dom. Since we can't rely on the
  events passed from the current component dom object, we need to listen for
  mouse events on the window.

  This window wire has specific wiretaps to listen to global mouse events. We
  close over properties that will be useful (dom-pos, cursor-pos) when
  capturing later mouse actions"
  [component wire event]
  (if (should-start-sorting? event)
    (let [item-bounds     (gstyle/getBounds (show/get-node component))
          cursor-movement (goog.math.Vec2. 0 0)]
      (w/act wire ::sort-started {::bounds item-bounds})

      (-> (w/wire)
          (w/tap :mouse-move #(dragging wire % cursor-movement item-bounds))
          (w/tap :mouse-up   #(stop-sort wire %))
          (wire-up js/window)))))

(defn- tap-sort-item
  "Single wiretap on the li, only listening for the mousedown dom event to
  initiate the start of the sorting"
  [component wire]
  (w/taps (w/wire)
    :mouse-down #(start-sort component wire %)))

(show/defcomponent SortedItem
  "List item, acting as a wrapper that will capture mouse down click for
  sorting initialization"
  [component]
  (initial-state [{:keys [wire]}]
    {:ref (show/create-ref)
     :hotspot-wire (tap-sort-item component wire)})
  (will-unmount
    (w/act (show/get-props component :wire) ::item-unmounted))
  (did-mount
    (w/act (show/get-props component :wire) ::item-mounted {::component component}))
  (render [{:as props :keys [items wire item-props item-component selected?]}
           {:as state :keys [ref hotspot-wire]}]
    (dom/li {:class {"selected" selected?} :ref ref}
      (item-component (merge item-props
                             {:wire wire
                              :items items
                              :hotspot-wire hotspot-wire})))))

(show/defcomponent FloatingItem
  "Floating display only copy of selected SortedItem. We pass in a junk wire to
  ensure that none of the events can pass"
  [component]
  (render [{:as props :keys [items bounds item-component item-props wire]} _]
    (dom/li {:class "floating"
             :style {:opacity 0.95
                     :position "absolute"
                     :cursor "move"
                     :backgroundColor "#eee"
                     :width (:width bounds)
                     :height (:height bounds)
                     :top  (:top bounds)
                     :left (:left bounds)}}
      (item-component (merge item-props {:hotspot-wire (w/wire)
                                         :items items
                                         :wire (w/wire)})))))

(defn- wiretap*
  "Wiretap responsibilities:

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
  (let [collected   (atom {})
        items-by-id (atom {})
        bounds-from-component #(-> % (show/get-state :ref) (gobj/get "current") gstyle/getBounds)]
    (w/taps wire
      ::container-mounted
        (fn [{:as evt ::keys [group group-id component]}]
          (swap! collected assoc-in [:container group group-id] component))
      ::item-mounted
        (fn [{:as evt ::keys [group group-id component item-id]}]
          (swap! collected assoc-in [:items group group-id item-id] component))
      ::item-unmounted
        (fn [{:as evt ::keys [group group-id component item-id]}]
          (swap! collected update-in [:items group group-id] dissoc item-id))
      ::sort-started
        (fn [{:as evt ::keys [item-id group group-id bounds]}]
          (let [component (get-in @collected [:container group group-id])]
            (reset! items-by-id (show/get-state component :items-by-id))))
      ::sort-dragging
        (fn [{:as evt ::keys [drag-axis cursor-pos item-id bounds group
                              group-id cursor-movement]}]
          (when (> (.magnitude cursor-movement) 3)
            (let [component        (get-in @collected [:container group group-id])
                 all-containers    (get-in @collected [:container group])
                 [cgid ccomponent] (or (first (filter (fn [[gid component]]
                                                        (.intersects bounds (bounds-from-component component)))
                                                      all-containers))
                                       ;; Default to original container
                                       [group-id component])
                 all-items (get-in @collected [:items group cgid])]

              ;; Directional relative position
              ;; a 1
              ;; b 0 (current)
              ;; c -1

              ;; |   a    |   b   | c |        d         |
              ;; Item sorting
              ;;
              (let [drag-key (str (name drag-axis))
                    ;; From Center
                    dist-fn (fn [item _]
                              (- (gobj/get cursor-pos drag-key)
                                 (gobj/get (.getCenter (bounds-from-component item)) drag-key)))

                    ;; Upon intersection
                    dist-fn (fn [item]
                              (let [bounds (bounds-from-component item)
                                    cursor (gobj/get cursor-pos drag-key)]
                                (if (.contains bounds cursor-pos)
                                  (- cursor (gobj/get (.getCenter bounds) drag-key))
                                  (let [base    (if (= "x" drag-key)
                                                  (.-left bounds)
                                                  (.-top bounds))
                                        final   (- cursor base)]
                                    (if (< final 0)
                                      final
                                      (- cursor (+ base (if (= "x" drag-key)
                                                          (.-width bounds)
                                                          (.-height bounds)))))))))

                    sort-set (sort-by first (map (fn [[id item]]
                                                   [(dist-fn item) [id item]])
                                                 all-items))

                    [prev-pos [prev-id prev-item]] (last  (filter #(< (first %) 0) sort-set))
                    [post-pos [post-id post-item]] (first (filter #(> (first %) 0) sort-set))
                    _ (pprint {:current item-id
                               :set sort-set
                               :prev-id prev-id
                               :post-id post-id}) ]
                (when (and (not= prev-id item-id)
                           (not= post-id item-id))
                  (let [post-pos (get-in @items-by-id [post-id :pos])
                        prev-pos (get-in @items-by-id [prev-id :pos])
                        swap-pos (cond
                                   (nil? prev-pos) (+ 5000 post-pos)
                                   (nil? post-pos) (/ prev-pos 2)
                                   :else           (/ (+ post-pos prev-pos) 2))]
                    (swap! items-by-id assoc-in [item-id :pos] swap-pos)))
                )

              ;; Set parent group id for dragging item
              ;;
              (swap! items-by-id assoc-in [item-id :parent-group-id] cgid)

              ;; Inject updated items & unsort other containers
              ;;
              (doseq [[ngid container] all-containers]
                (if (= ngid cgid)
                  (show/assoc! container
                     :selected-id item-id
                     :items-by-id @items-by-id
                     :floating-pos {:height (.-height bounds) :width (.-width bounds)
                                    :top    (.-top bounds)    :left  (.-left bounds)}
                     :sorting? true)
                  (show/assoc! container
                     :items-by-id @items-by-id
                     :sorting? false))))))
      ::sort-ended
        (fn [{:as evt ::keys [item-id group group-id]}]
          (doseq [[_ container] (get-in @collected [:container group])]
            (show/assoc! container
              :selected-id nil
              :sorting? false))
          (w/act wire ::sort-complete {:item-id item-id
                                       :item-params (get @items-by-id item-id)})))))

(defn next-pos
  "Determine what the next :pos value should be to ensure you can place an item
   at the end of the list"
  ([items group]
   (next-pos items group nil))
  ([items group parent-group-id]
   (->> items
        (filter (fn [item]
                  (and (= (:group item) group)
                       (= (:parent-group-id item) parent-group-id))))
        (apply max-key :pos)
        :pos
        (+ 5000))))

(defn wiretap
  "Basic wiretap allowing for a nested tapping check to ensure children
   don't tap too much"
  [wire]
  (if (get-in (w/data wire) [:context ::sortable-tapped])
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
     :drag-axis      :y
     :group-id       (str (gensym "group-id-"))
     :group          (str (gensym "group"))})

  ;; initialState
  ;;
  (initial-state [{:keys [group group-id items wire item-key
                          drag-axis axis-constrain]}]
    {;; Basic requirements
     :wire (wiretap (w/lay wire nil {::group group
                                     ::group-id group-id
                                     ::drag-axis drag-axis
                                     ::axis-constrain axis-constrain}))

     :items-by-id {}

     ;; Stored refs, for bounds lookups
     :ref (show/create-ref)

     ;; Selecting states
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
  (will-receive-props [{:keys [items item-key]}]
    (when (not= (show/get-props component :items) items)
      (normalize-items component items item-key)))

  ;; Render
  ;;
  (render [{:as props :keys [items group group-id container-dom-id
                             container-dom-classes item-key item-component]}
           {:as state :keys [wire floating-pos sorting? selected-id ref
                             items-by-id]}]
    (dom/ul (cond-> {:key "list" :class "sort-list" :ref ref}
              container-dom-id (assoc :id container-dom-id)
              container-dom-classes (update :class #(str % " " container-dom-classes)))
      (->> (vals items-by-id)
           (filter #(if (:parent-group-id %)
                      (and (= (:group %) group)
                           (= (:parent-group-id %) group-id))
                      (= (:group %) group)))
           (sort-by :pos)
           (map (fn [{:as data :keys [id]}]
                  (SortedItem
                    {:key id
                     :sorting? sorting?
                     :wire (w/lay wire nil {::item-id id})
                     :selected? (= selected-id id)
                     :items (vals items-by-id)
                     :item-props data
                     :item-component item-component})))
           (#(cond-> %
               sorting? (conj (FloatingItem
                                {:item-props (get-item-by-id component selected-id)
                                 :items (vals items-by-id)
                                 :wire wire
                                 :item-component item-component
                                 :bounds floating-pos}))))))))
