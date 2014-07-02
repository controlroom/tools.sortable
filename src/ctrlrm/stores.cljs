(ns ctrlrm.stores
  (:require [show.core :as show :include-macros true]))

(defprotocol StoreAccessors
  (-data     [this])
  (-local    [this]))

(defprotocol StoreChanges
  (-register-changes   [this topic cb])
  (-unregister-changes [this topic cb-id])
  (-emit-change        [this topic]))

(deftype Store [data local]
  StoreAccessors
  (-data     [this] data)
  (-local    [this] local)

  StoreChanges
  (-unregister-changes [this topic cb-id]
    (swap! local (fn [o] (update-in o [:callbacks topic] dissoc cb-id))))

  (-register-changes [this topic cb]
    (let [cb-id (gensym)]
      (swap! local assoc-in [:callbacks topic cb-id] cb)
      (partial -unregister-changes this topic cb-id)))

  (-emit-change [this topic]
    (doseq [cb (vals (get-in @local [:callbacks topic]))]
      (cb))))

(defn create-store []
  (Store. (atom {}) (atom {})))

(defn get-data [store]
  (-data store))

(defn register-changes
  ([store f]
   (register-changes store ::basic f))
  ([store topic f]
   (-register-changes store topic f)))

(defn emit-change
  ([store] (emit-change store ::basic))
  ([store topic]
   (-emit-change store topic)))

(show/defclass PlugCallbackMixin [component]
  (will-unmount
    (doseq [cb (show/get-state component ::cbs)] (cb))))
