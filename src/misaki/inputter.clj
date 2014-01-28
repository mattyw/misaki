(ns misaki.inputter
  (:require
    [misaki.config :refer [*config*]]
    [misaki.loader :refer [load-functions]])
  (:refer-clojure :exclude [empty?])
  )

(def ^:dynamic *inputter-ns-prefix*
  "misaki.inputter")

(def ^{:private true} queue (ref []))


(defn- load-inputter
  [inputter-name]
  (load-functions *inputter-ns-prefix* inputter-name))

(defn get-inputters
  []
  (let [inputter-names (:inputters *config*)]
    (filter (comp not nil?)
            (map (comp :run load-inputter) inputter-names))))

(defn add!
  [edn]
  (dosync (alter queue conj edn)))

(defn get!
  []
  (let [edn (first @queue)]
    (dosync (alter queue (comp vec rest)))
    edn))

(defn empty?
  []
  (clojure.core/empty? @queue))

(defn start-inputters!
  []
  (let [inputters (get-inputters)]
    (doseq [f inputters]
      (.start (Thread. (partial f *config*))))))
