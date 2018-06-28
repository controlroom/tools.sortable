tools.sortable
Dragula / Trello as uber examples

- Nested sortables
- Multiple Containers driven by data
- Sortable options to allow for Dragula features

- Grid Mode
- List Mode

```clojure
(Sortable
  {;; Debug & identifing
   :name "type of sortable"

   ;; Grouping (allow for multiple
   :group-id "identifier to allow items to traverse between sortables"
   :axis-constrain "axis for dragging constraints (:x, :y, :none)"
   :drag-axis "axis to determine where to swap"

   ;; Modes (interchangable between groups)
   :mode "list or grid (config varies based)"

   :items "vector of hash-maps"
   :item-key "keyword that identifies identifier"
   :item-compnent "A single component that data flows to"
   :sorted-ids "Vector of identifiers that represent the current sort via index"

   :container-classes "String list of classes"
   :container-id "String of potential id for container"

   :wire "wire to tap on sorting events"
   }
  )
```
