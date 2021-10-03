(ns backend.db
  (:require [camel-snake-kebab.extras :refer [transform-keys]]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [clj-time.coerce :as c]
            [clj-time.core :as clj-core]
            [clj-time.format :as f]
            [environ.core :refer [env]]
            [mount.core :as mount]
            [conman.core :as conman]
            [clojure.java.jdbc :as jdbc]
            [hugsql.core :as hugsql])
  (:import
   (java.time LocalDate)))

;; Чиним HugSQL так, что бы он мог принимать и отдавать мапы с ключами
;; в стиле шашлыка, а не заставлять разработчика использовать
;; ограничения навязанные стандартом SQL.
(defn result-one-snake->kebab
  [this result options]
  (->> (hugsql.adapter/result-one this result options)
       (transform-keys ->kebab-case-keyword)))

(defn result-many-snake->kebab
  [this result options]
  (->> (hugsql.adapter/result-many this result options)
       (map #(transform-keys ->kebab-case-keyword %))))

(defmethod hugsql.core/hugsql-result-fn :1 [sym]
  'backend.db/result-one-snake->kebab)

(defmethod hugsql.core/hugsql-result-fn :one [sym]
  'backend.db/result-one-snake->kebab)

(defmethod hugsql.core/hugsql-result-fn :* [sym]
  'backend.db/result-many-snake->kebab)

(defmethod hugsql.core/hugsql-result-fn :many [sym]
  'backend.db/result-many-snake->kebab)
;; Конец починки тут.


;; То что поступает с фронта
(def test-pacient {:second-name "Поляков"
                   :first-name  "Дмитрий"
                   :third-name  "Сергеевич"
                   :sex         "М"
                   :dob         (c/to-sql-date "2000-04-20")
                   :address     "Yekaterinburg"
                   :oms         8155999729000314})


;; Экспорт всех функций в текущий namespace

(def pool-spec
  {:adapter            (env :db-prod-adapter)
   :username           (env :db-prod-username)
   :password           (env :db-prod-password)
   :database-name      (env :db-prod-database-name)
   :server-name        (env :db-prod-server-name)
   :port-number        (env :db-prod-port-number)})

(mount/defstate ^:dynamic
  ^{:on-reload :noop}
  *db*
  :start (conman/connect! pool-spec)
  :stop (conman/disconnect! *db*))

(conman/bind-connection *db* "backend/pacients.sql")

(defn db-to-str [date]
  (f/unparse (f/formatters :date) (c/from-sql-date date)))
             ;; (clj-core/plus (c/from-sql-date date)
             ;;                ;; TODO change locale on db server
             ;;                (clj-core/days 1))))

(defn wrap-update-pacient [pacient]
  (let [pacient* (update pacient :dob #(c/to-sql-date %))]
    (update-pacient pacient*)))

(defn wrap-all-pacients []
  (into []
        (for [p (all-pacients)]
          (update p :dob (fn [el] (db-to-str el))))))

(defn wrap-insert-pacient [pacient]
  (let [pacient* (update pacient :dob #(c/to-sql-date %))]
    (insert-pacient pacient*)))

(comment
  ;; Включаем базу данных
  (mount/start #'*db*)
  (mount/stop #'*db*)


  (= (:oms pacient) (:oms curr-pacient-from-db))
  (=  (:oms (get-user-by-oms (assoc test-pacient :oms 3)))
      (:oms (get-user-by-id (assoc test-pacient :id 1))))
  (create-pacients-table)
  (drop-pacients-table)
  (all-pacients)
  (insert-pacient test-pacient)
  (update-pacient test-pacient*)
  (remove-pacient-by-id {:id 1}))
