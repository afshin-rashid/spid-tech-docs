(ns spid-docs.import-endpoints
  (:require [clojure.data.json :as json]
            [clojure.set :as set]
            [schema.core :as schema]
            [spid-docs.api-client :refer [GET get-config get-server-token config-exists?]]
            [spid-docs.content :refer [load-content]]
            [spid-docs.homeless :refer [assoc-if]]
            [spid-docs.validate-raw :refer [Endpoint]]))

(defn- extract-relevant-keys [endpoints]
  "Return a list of all property names used for all endpoints, including the
   properties used in all httpMethod objects."
  (->> (concat (map name (mapcat keys endpoints))
               (map #(str "httpMethods/" (name %))
                    (mapcat keys (mapcat (comp vals :httpMethods) endpoints))))
       set))

(defn- schema-is-valid? [endpoints]
  "Check if the given endpoints matches our expectations."
  (not (schema/check [Endpoint] endpoints)))

(defn- find-schema-changes [old new]
  "Compare properties used to describe endpoints in old and new version."
  (let [old-keys (extract-relevant-keys old)
        new-keys (extract-relevant-keys new)
        added (sort (set/difference new-keys old-keys))
        removed (sort (set/difference old-keys new-keys))]
    (when (or (seq added) (seq removed))
      {:added added, :removed removed})))

(defn- get-signatures [endpoint]
  "Return a list of {:path :method} for each of httpMethods in endpoint."
  (map (fn [method] {:path (:path endpoint)
                     :method method})
       (keys (:httpMethods endpoint))))

(defn- flatten-http-methods [endpoint]
  "Return a list of endpoints each with a single entry in httpMethods from the original."
  (let [methods (:httpMethods endpoint)]
    (map (fn [method] (assoc endpoint :httpMethods (select-keys methods [method])))
         (keys methods))))

(defn- find-endpoint-changes [old new]
  "Return a map of added, removed and changed endpoints."
  (let [old-paths (set (mapcat get-signatures old))
        new-paths (set (mapcat get-signatures new))
        added (sort (set/difference new-paths old-paths))
        removed (sort (set/difference old-paths new-paths))
        changed (sort (-> (set (mapcat get-signatures
                                       (set/difference (set (mapcat flatten-http-methods old))
                                                       (set (mapcat flatten-http-methods new)))))
                          (set/difference added removed)))]
    (cond-> {}
            (seq added) (assoc :added added)
            (seq removed) (assoc :removed removed)
            (seq changed) (assoc :changed changed))))

(defn- add-schema-changes [m old new]
  "If there are any schema changes, add them to the map along with :schema-change set to true."
  (if-let [schema-changes (find-schema-changes old new)]
    (assoc m
      :schema-change? true
      :schema schema-changes)
    m))

(defn compare-endpoint-lists [old new]
  "Diff old and new endpoints, and return a summary of the changes."
  (if (= old new)
    {:no-changes? true}
    (-> {}
        (add-schema-changes old new)
        (assoc-if (not (schema-is-valid? new)) :breaking-change? true)
        (merge (find-endpoint-changes old new)))))

(def import-path "/endpoints")

(defn- report-changed-endpoint-keys [diff]
  (when (:schema-change? diff)
    (println "Note! The endpoint fields have changed since last import:")
    (doseq [field (-> diff :schema :added)] (println (format "  - Added field \"%s\"" field)))
    (doseq [field (-> diff :schema :removed)] (println (format "  - Removed field \"%s\"" field)))
    (println)))

(defn- report-endpoint-changes [{:keys [changed added removed]}]
  (when changed
    (println "Changed endpoints:")
    (doseq [e changed] (println "  -" (name (:method e)) (:path e)))
    (println))
  (when added
    (println "Added endpoints:")
    (doseq [e added] (println "  -" (name (:method e)) (:path e)))
    (println))
  (when removed
    (println "Removed endpoints:")
    (doseq [e removed] (println "  -" (name (:method e)) (:path e)))
    (println)))

(defn- load-new-endpoints []
  (println (str "Fetching " (:spid-base-url (get-config)) "/api/2" import-path))
  (let [response (GET (get-server-token) import-path)]
    (when-not (= 200 (:status response))
      (throw (Exception.
              (format "Unexpected status %d when fetching endpoints at %s"
                      (:status response) import-path))))
    {:new-endpoints (:data response)
     :raw-response (:body response)}))

(defn import-endpoints []
  (if-not (config-exists?)
    (do
      (println "Aborting import, no configuration file detected.")
      (println "")
      (println "  cp resources/config.sample.edn resources/config.edn")
      (println "  vim resources/config.edn")
      (println ""))
    (let [old-endpoints (:endpoints (load-content))
          {:keys [new-endpoints raw-response]} (load-new-endpoints)
          diff (compare-endpoint-lists old-endpoints new-endpoints)]
      (if (:no-changes? diff)
        (println "No changes detected.")
        (if (:breaking-change? diff)
          (do
            (report-changed-endpoint-keys diff)
            (println "Aborting import, since schema has changed. It no longer passes")
            (println "our expectations for the data structure.")
            (println)
            (println "If this is just a new field to be ignored by the tech-docs")
            (println "site, add it as an optional field to the schema in validate_raw.clj.")
            (println "Otherwise, there's Clojure programming ahead."))
          (do
            (report-endpoint-changes diff)
            (report-changed-endpoint-keys diff)
            (spit "generated/cached-endpoints.json" raw-response)
            (println "Wrote generated/cached-endpoints.json")))))))
