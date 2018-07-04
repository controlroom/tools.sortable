tools.sortable
Dragula / Trello as uber examples

- Nested sortables
- Multiple Containers driven by data
- Sortable options to allow for Dragula features

- Grid Mode
- List Mode

```clojure
(Sortable
  {;; Grouping (allow for traversal of items between containers)
   :group "Name to signify all traversable sortables (defaults to random)"
   :group-id "Unique group identifier. Must be unique between groupings (group-key)"

   ;; Axis
   :axis-constrain "axis for dragging constraints (:x or :y or :none)"
   :drag-axis "axis to determine where to swap (:x or :y)"

   ;; Modes (interchangable between groups)
   :mode "list or grid (config varies based)"

   :items "vector of hash-map data"
   :item-key "keyword that describes how to lookup identifier from seq of supplied items"
   :item-compnent "A single component that data flows into via props"
   ;; Working on depricating this
   :sorted-ids "Vector of identifiers that represent the current sort via index"

   :container-dom-classes "String list of classes"
   :container-dom-id "String of potential id for container"

   :wire "wire to tap on sorting events"})
```
