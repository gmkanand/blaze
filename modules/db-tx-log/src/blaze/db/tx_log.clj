(ns blaze.db.tx-log
  "Protocols for transaction log backend implementations."
  (:import
    [java.io Closeable]))


(defprotocol TxLog
  "The central transaction log shared between all nodes.

  There might be implementations of TxLog only suitable for single node setups."

  (-submit [tx-log tx-cmds])

  (-new-queue [tx-log offset]))


(defprotocol Queue
  (-poll [tx-log timeout]))


(defn submit
  "Submits transaction commands.

  Returns a success deferred with the point in time (:blaze.db/t) of the
  potentially valid transaction or an error deferred with an anomaly in case of
  errors."
  [tx-log tx-cmds]
  (-submit tx-log tx-cmds))


(defn new-queue
  "Returns a new queue starting at `offset`.

  The queue has to be closed after usage."
  ^Closeable
  [tx-log offset]
  (-new-queue tx-log offset))


(defn poll
  "Returns a coll of transaction commands or an anomaly in case the queue was
  closed in cause of an error."
  [queue timeout]
  (-poll queue timeout))
