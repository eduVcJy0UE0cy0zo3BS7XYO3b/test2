(ns backend.time_check
  (:require
   [tick.core :as t]
   [clojure.string :as str]
   [malli.core :as m])
  (:import
   (java.text SimpleDateFormat)))

(def dob?
  (m/-simple-schema
   {:type :dob
    :type-properties
    {:error/fn (fn [e & else] "Should be a Date if format yyyy-mm-dd and not newer than today.")}
    :pred (fn [val & else]
            (try
              (let [sdf (new SimpleDateFormat "yyyy-mm-dd")]
                (t/> (t/today) (t/date val)))
              (catch Exception e false)))}))
