(ns blaze.interaction.search-type
  "FHIR search interaction.

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.anomaly :refer [when-ok]]
    [blaze.async-comp :as ac]
    [blaze.db.api :as d]
    [blaze.fhir.spec.type :as type]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.handler.util :as util]
    [blaze.interaction.search.nav :as nav]
    [blaze.interaction.search.params :as params]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [ring.util.response :as ring]
    [taoensso.timbre :as log]))


(defn- entry
  [router {:fhir/keys [type] :keys [id] :as resource}]
  {:fullUrl (type/->Uri (fhir-util/instance-url router (name type) id))
   :resource resource
   :search {:fhir/type :fhir.Bundle.entry/search :mode #fhir/code"match"}})


(defn- entries
  "Returns bundle entries."
  [router db type {:keys [clauses page-id page-size]}]
  (when-ok [handles (if (empty? clauses)
                      (d/list-resource-handles db type page-id)
                      (d/type-query db type clauses page-id))]
    (-> (->> (into [] (take (inc page-size)) handles)
             (d/pull-many db))
        (ac/then-apply #(mapv (partial entry router) %)))))


(defn- self-link-offset [entries]
  {"__page-id" (-> entries first :resource :id)})


(defn- self-link [match params t entries]
  {:fhir/type :fhir.Bundle/link
   :relation "self"
   :url (type/->Uri (nav/url match params t (self-link-offset entries)))})


(defn- next-link-offset [entries]
  {"__page-id" (-> entries peek :resource :id)})


(defn- next-link [match params t entries]
  {:fhir/type :fhir.Bundle/link
   :relation "next"
   :url (type/->Uri (nav/url match params t (next-link-offset entries)))})


(defn- total
  "Calculates the total number of resources returned.

  If we have no clauses (returning all resources), we can use `d/type-total`.
  Secondly, if the number of entries found is not more than one page in size,
  we can use that number. Otherwise there is no cheap way to calculate the
  number of matching resources, so we don't report it."
  [db type {:keys [clauses page-size page-id]} entries]
  (cond
    (empty? clauses)
    (d/type-total db type)

    (and (nil? page-id) (<= (count entries) page-size))
    (count entries)))


(defn- search** [router match db type params]
  (let [t (or (d/as-of-t db) (d/basis-t db))]
    (when-ok [entries (entries router db type params)]
      (ac/then-apply
        entries
        (fn [entries]
          (let [page-size (:page-size params)
                total (total db type params entries)]
            (cond->
              {:fhir/type :fhir/Bundle
               :type #fhir/code"searchset"
               :entry (take page-size entries)}

              total
              (assoc :total (type/->UnsignedInt total))

              (seq entries)
              (update :link (fnil conj []) (self-link match params t entries))

              (< page-size (count entries))
              (update :link (fnil conj []) (next-link match params t entries)))))))))


(defn- summary-total [db type {:keys [clauses]}]
  (if (empty? clauses)
    (d/type-total db type)
    (transduce (map (constantly 1)) + 0 (d/type-query db type clauses))))


(defn- search-summary [db type params]
  (ac/completed-future
    {:fhir/type :fhir/Bundle
     :type #fhir/code"searchset"
     :total (type/->UnsignedInt (summary-total db type params))}))


(defn- search* [router match db type params]
  (if (:summary? params)
    (search-summary db type params)
    (search** router match db type params)))


(defn- search [router match db type params]
  (when-ok [params (params/decode params)]
    (search* router match db type params)))


(defn- handle [router match db type params]
  (let [body (search router match db type params)]
    (if (::anom/category body)
      (ac/completed-future (util/error-response body))
      (ac/then-apply body ring/response))))


(defn- handler-intern [node]
  (fn [{{{:fhir.resource/keys [type]} :data :as match} ::reitit/match
        :keys [params]
        ::reitit/keys [router]}]
    (-> (util/db node (fhir-util/t params))
        (ac/then-compose #(handle router match % type params)))))


(defn handler [node]
  (-> (handler-intern node)
      (wrap-observe-request-duration "search-type")))


(defmethod ig/init-key :blaze.interaction/search-type
  [_ {:keys [node]}]
  (log/info "Init FHIR search-type interaction handler")
  (handler node))
