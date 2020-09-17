(ns blaze.db.impl.index.resource-as-of-spec
  (:require
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.index.resource-as-of :as resource-as-of]
    [blaze.db.impl.index.resource-handle-spec]
    [blaze.db.impl.iterators-spec]
    [blaze.db.kv-spec]
    [blaze.db.spec]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]))


(s/fdef resource-as-of/type-list
  :args (s/cat :raoi :blaze.db/kv-iterator
               :tid :blaze.db/tid
               :start-id (s/nilable bytes?)
               :t :blaze.db/t)
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/fdef resource-as-of/system-list
  :args (s/cat :raoi :blaze.db/kv-iterator
               :start-tid (s/nilable :blaze.db/tid)
               :start-id (s/nilable bytes?)
               :t :blaze.db/t)
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/fdef resource-as-of/instance-history
  :args (s/cat :raoi :blaze.db/kv-iterator
               :tid :blaze.db/tid
               :id :blaze.resource/id
               :start-t :blaze.db/t
               :end-t :blaze.db/t)
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/fdef resource-as-of/hash-state-t
  :args (s/cat :raoi :blaze.db/kv-iterator
               :tid :blaze.db/tid
               :id :blaze.db/id-bytes
               :t :blaze.db/t)
  :ret (s/nilable (s/tuple :blaze.resource/hash :blaze.db/state :blaze.db/t)))


(s/fdef resource-as-of/resource-handle
  :args (s/cat :raoi :blaze.db/kv-iterator
               :tid :blaze.db/tid
               :id :blaze.db/id-bytes
               :t :blaze.db/t)
  :ret (s/nilable :blaze.db/resource-handle))


(s/fdef resource-as-of/num-of-instance-changes
  :args (s/cat :raoi :blaze.db/kv-iterator
               :tid :blaze.db/tid
               :id :blaze.db/id-bytes
               :start-t :blaze.db/t
               :end-t :blaze.db/t)
  :ret nat-int?)
