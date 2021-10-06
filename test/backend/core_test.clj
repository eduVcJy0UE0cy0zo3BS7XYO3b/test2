(ns backend.core-test
  (:require [backend.core :refer :all]
            [conman.core :as conman]
            [backend.db :as db]
            [cheshire.core :refer :all]
            [muuntaja.core :as m]
            [environ.core :refer [env]]
            [clojure.test :refer :all]
            [ring.mock.request :as mock]))

(defn fix-with-db [t]

  (def ^:dynamic *test-db-spec*
    (conman/connect!
     {:adapter            (env :db-test-adapter)
      :username           (env :db-test-username)
      :password           (env :db-test-password)
      :database-name      (env :db-test-database-name)
      :server-name        (env :db-test-server-name)
      :port-number        (env :db-test-port-number)}))

  (binding [db/*db* *test-db-spec*]
    (db/drop-pacients-table)
    (db/create-pacients-table)
    (t)
    (db/drop-pacients-table))

  (conman/disconnect! *test-db-spec*))

(use-fixtures :once fix-with-db)

(defn parse-body [response]
  (m/decode "application/json" response))

(def test-pacient {:second-name "Поляков"
                   :first-name  "Дмитрий"
                   :third-name  "Сергеевич"
                   :sex         "М"
                   :dob         "2000-04-20"
                   :address     "Yekaterinburg"
                   :oms         max-oms})

(def test-pacient2 {:second-name "Полухина"
                    :first-name  "Жанна"
                    :third-name  "Викторовна"
                    :sex         "Ж"
                    :dob         "1980-01-01"
                    :address     "Moscow"
                    :oms         min-oms})

;; (defn memm [resp] (case (>= (:status resp) 400)
;;                true (update resp :body :humanized)
;;                false resp))

(defn request
  ([method uri]
   (request method uri nil))
  ([method uri body]
   (-> (app (-> (mock/request method uri)
                (mock/json-body body)))
       (select-keys [:body :status])
       (update :body parse-body))))

(deftest test-easy
  (testing "Воспринимает ли сервер запросы?"
    (is (= {:status 200 :body {:health "ok"}}
           (request :get "/health"))))
  (testing "Проверка спеки."
    (is (= {:status 200 :body test-pacient}
           (request :post "/health" test-pacient)))))

(deftest error-client
  (let [max-oms+1 (+ max-oms 1)
        min-oms-1 (- min-oms 1)
        broken-pacient1 (assoc test-pacient
                               :first-name ""
                               :second-name ""
                               :third-name ""
                               :address ""
                               :sex "Собака"
                               :dob "2033-04-20"
                               :oms max-oms+1)
        broken-pacient2 (assoc test-pacient
                               :first-name 1
                               :second-name 2
                               :third-name 3
                               :address 4
                               :sex 5
                               :dob 6
                               :oms min-oms-1)
        broken-pacient3 (assoc test-pacient :oms "1111111111111111")]
    (testing "Первый пак"
      (is (= {:status 400
              :body
	      {:value
	       {:address "",
	        :sex "Собака",
	        :first-name "",
	        :dob "2033-04-20",
	        :second-name "",
	        :third-name "",
	        :oms max-oms+1},
	       :in ["request" "body-params"],
	       :humanized
	       {:address ["should be at least 1 characters"],
	        :sex ["should be either М or Ж"],
	        :first-name ["should be at least 1 characters"],
	        :dob
	        ["Should be a Date if format yyyy-mm-dd and not newer than today."],
	        :second-name ["should be at least 1 characters"],
	        :third-name ["should be at least 1 characters"],
	        :oms ["should be at most 9999999999999999"]}}}
             (request :post "/pacient/insert" broken-pacient1))))

    (testing "Второй пак"
      (is (= {:status 400
              :body
              {:value
               {:address 4,
                :sex 5,
                :first-name 1,
                :dob 6,
                :second-name 2,
                :third-name 3,
                :oms min-oms-1},
               :in ["request" "body-params"],
               :humanized
               {:address ["should be a string"],
                :sex ["should be either М or Ж"],
                :first-name ["should be a string"],
                :dob
                ["Should be a Date if format yyyy-mm-dd and not newer than today."],
                :second-name ["should be a string"],
                :third-name ["should be a string"],
                :oms ["should be at least 1000000000000000"]}}}
             (request :post "/pacient/insert" broken-pacient2))))
    (testing "Третий"
      (is (= {:status 400
              :body
               {:value
                {:address "Yekaterinburg",
                 :sex "М",
                 :first-name "Дмитрий",
                 :dob "2000-04-20",
                 :second-name "Поляков",
                 :third-name "Сергеевич",
                 :oms "1111111111111111"},
                :in ["request" "body-params"],
                :humanized
                {:oms
                 ["should be an int" "should be a number" "should be a number"]}}}
             (request :post "/pacient/insert" broken-pacient3))))))

(deftest test-client
  (let [pacient-in-db (assoc test-pacient :id 1)
        pacient2-in-db (assoc test-pacient2 :id 2)
        pacient-in-db-updated (assoc pacient-in-db :address "Peterburg")
        pacient-update-with-aeoms (assoc pacient-in-db :oms (:oms test-pacient2))
        pacient-in-db-without-id (dissoc pacient-in-db-updated :id)]

    (testing "Полный жизненный цикл работы с приложением"
      (testing "Занесение нового пациента в базу."
        (is (= {:status 200 :body {:message "Пациент добавлен." :id 1}}
               (request :post "/pacient/insert" test-pacient))))

      (testing "Занесение нового пациента в базу. (нужно для тестов дальше)"
        (is (= {:status 200 :body {:message "Пациент добавлен." :id 2}}
               (request :post "/pacient/insert" test-pacient2))))

      (testing "Занесение нового пациента в базу с существующим в ней ОМС."
        (is (= {:status 400
                :body {:message "Пациент с таким ОМС уже существует."}}
               (request :post "/pacient/insert" test-pacient))))

      (testing "Смотрим как пациент был записан в базу данных"
        (is (= {:status 200 :body [pacient2-in-db pacient-in-db]}
               (request :get "/pacients"))))

      (testing "Обновляем данные о пациенте"
        (is (= {:status 200 :body {:message "Пациент обновлён."}}
               (request :put "/pacient/update" pacient-in-db-updated))))

      (testing "При обновлении данных решаем указать существующий ОМС."
        (is (= {:status 400
                :body {:message "Пациент с таким ОМС уже существует."}}
               (request :put "/pacient/update" pacient-update-with-aeoms))))

      (testing "Смотрим как он обновился."
        (is (= {:status 200 :body [pacient2-in-db pacient-in-db-updated]}
               (request :get "/pacients"))))

      (testing "Удаляем данные о пациенте забыв передать его id."
        (is (= {:status 400
                :body {:value
	               {:address "Peterburg",
	                :sex "М",
	                :first-name "Дмитрий",
	                :dob "2000-04-20",
	                :second-name "Поляков",
	                :third-name "Сергеевич",
	                :oms max-oms}
	               :in ["request" "body-params"],
	               :humanized {:id ["missing required key"]}}}
               (request :delete "/pacient/remove" pacient-in-db-without-id))))

      (testing "Удаляем данные о пациенте."
        (is (= {:status 200 :body {:message "Пациент удалён."}}
               (request :delete "/pacient/remove" pacient-in-db-updated))))

      (testing "Смотрим что получилось в итоге."
        (is (= {:status 200 :body [pacient2-in-db]}
               (request :get "/pacients")))))))
