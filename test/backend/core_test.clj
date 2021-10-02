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

;; (use-fixtures :once fix-with-db)

(defn parse-body [response]
  (m/decode "application/json" (:body response)))

#_(deftest test-easy

  (testing "Воспринимает ли сервер запросы?"
    (let [response (app (mock/request :get "/health"))]
      (is (= 200 (:status response)))
      (is (= {:health "ok"} (parse-body response)))))

  (testing "Проверяем умеет ли сервер вообще восприн."
    (let [req-body {:param1 "param1" :param2 123}
          response (app (-> (mock/request :post "/health")
                            (mock/json-body req-body)))]
      (is (= 200 (:status response)))
      (is (= req-body (parse-body response)))))

  (testing "Проверяем умеет ли сервер вообще восприн."
    (let [req-body {:second-name "Поляков"
                    :first-name  "Дмитрий"
                    :third-name  "Сергеевич"
                    :sex         "М"
                    :dob         "2000-04-20"
                    :address     "Yekaterinburg"
                    :oms         815599972900031}
          response (app (-> (mock/request :post "/health")
                            (mock/json-body req-body)))]
      (is (= 400 (:status response)))
      (is (= {:type "reitit.coercion/request-coercion",
	      :coercion "malli",
	      :in ["request" "body-params"],
	      :humanized {:param2 ["should be an int"]}}
             (parse-body response))))))

(deftest test-client
  (let [test-pacient {:second-name "Поляков"
                      :first-name  "Дмитрий"
                      :third-name  "Сергеевич"
                      :sex         "М"
                      :dob         "2000-04-20"
                      :address     "Yekaterinburg"
                      :oms         8155999729000314}
        test-pacient2 {:second-name "Полухина"
                       :first-name  "Жанна"
                       :third-name  "Викторовна"
                       :sex         "Ж"
                       :dob         "1980-01-01"
                       :address     "Moscow"
                       :oms         1111111111111111}
        pacient-in-db (assoc test-pacient :id 1)
        pacient2-in-db (assoc test-pacient2 :id 2)
        broken-pacient (assoc test-pacient
                              :first-name ""
                              :second-name ""
                              :third-name ""
                              :address ""
                              :sex "Собака"
                              :dob "2033-04-20"
                              :dob "gav gav"
                              :oms 81559997290003141)
        broken-dob (assoc test-pacient :dob "gav gav" :oms 8155999729000)
        pacient-in-db-updated (assoc pacient-in-db :address "Peterburg")
        pacient-update-with-aeoms (assoc pacient-in-db :oms (:oms test-pacient2))
        pacient-in-db-without-id (dissoc pacient-in-db-updated :id)]

    (testing "Вводим неправильные данные"
      (testing "Первый пак"
        (let [response (app (-> (mock/request :post "/pacient/insert")
                                (mock/json-body broken-pacient)))]
          (is (= 400 (:status response)))
          (is (= {:type "reitit.coercion/request-coercion",
	          :coercion "malli"
	          :in ["request" "body-params"]
	          :humanized
	          {:sex ["should be either М or Ж"]
                   :address ["should be at least 1 characters"]
	           :first-name ["should be at least 1 characters"]
	           :dob
	           ["Should be a Date if format yyyy-mm-dd and not newer than today."]
	           :second-name ["should be at least 1 characters"]
	           :third-name ["should be at least 1 characters"]
	           :oms ["should be smaller than 10000000000000000"]}}
                 (parse-body response)))))

        (testing "Второй пак"
          (let [response (app (-> (mock/request :post "/pacient/insert")
                                  (mock/json-body broken-dob)))]
            (is (= 400 (:status response)))
            (is (= {:type "reitit.coercion/request-coercion",
	            :coercion "malli",
	            :in ["request" "body-params"],
	            :humanized
	            {:oms ["should be larger than 999999999999999"]
                     :dob
	             ["Should be a Date if format yyyy-mm-dd and not newer than today."]}}
                   (parse-body response))))))

    (testing "Полный жизненный цикл работы с приложением"
      (testing "Занесение нового пациента в базу."
        (let [response (app (-> (mock/request :post "/pacient/insert")
                                (mock/json-body test-pacient)))]
          (is (= 200 (:status response)))
          (is (= {:message "Пациент добавлен."
                  :id 1}
                 (parse-body response)))))

      (testing "Занесение нового пациента в базу. (нужно для тестов дальше)"
        (let [response (app (-> (mock/request :post "/pacient/insert")
                                (mock/json-body test-pacient2)))]
          (is (= 200 (:status response)))
          (is (= {:message "Пациент добавлен."
                  :id 2}
                 (parse-body response)))))

      (testing "Занесение нового пациента в базу с существующим в ней ОМС."
        (let [response (app (-> (mock/request :post "/pacient/insert")
                                (mock/json-body test-pacient)))]
          (is (= 404 (:status response)))
          (is (= {:message "Пациент с таким ОМС уже существует."}
                 (parse-body response)))))

      (testing "Смотрим как пациент был записан в базу данных"
        (let [response (app (mock/request :get "/pacients"))]
          (is (= 200 (:status response)))
          (is (= [pacient2-in-db pacient-in-db]
                 (parse-body response)))))

      (testing "Обновляем данные о пациенте"
        (let [response (app (-> (mock/request :put "/pacient/update")
                                (mock/json-body pacient-in-db-updated)))]
          (is (= 200 (:status response)))
          (is (= {:message "Пациент обновлён."}
                 (parse-body response)))))

      (testing "При обновлении данных решаем указать существующий ОМС."
        (let [response (app (-> (mock/request :put "/pacient/update")
                                (mock/json-body pacient-update-with-aeoms)))]
          (is (= 404 (:status response)))
          (is (= {:message "Пациент с таким ОМС уже существует."}
                 (parse-body response)))))

      (testing "Смотрим как он обновился."
        (let [response (app (mock/request :get "/pacients"))]
          (is (= 200 (:status response)))
          (is (=  [pacient2-in-db pacient-in-db-updated]
                  (parse-body response)))))

      (testing "Удаляем данные о пациенте забыв передать его id."
        (let [response (app (-> (mock/request :delete "/pacient/remove")
                                (mock/json-body pacient-in-db-without-id)))]
          (is (= 400 (:status response)))
          (is (= {:type "reitit.coercion/request-coercion"
	          :coercion "malli"
	          :in ["request" "body-params"]
	          :humanized {:id ["missing required key"]}}
                 (parse-body response)))))

      (testing "Удаляем данные о пациенте."
        (let [response (app (-> (mock/request :delete "/pacient/remove")
                                (mock/json-body pacient-in-db-updated)))]
          (is (= 200 (:status response)))
          (is (= {:message "Пациент удалён."}
                 (parse-body response)))))

      (testing "Смотрим что получилось в итоге."
        (let [response (app (mock/request :get "/pacients"))]
          (is (= 200 (:status response)))
          (is (=  [pacient2-in-db]
                  (parse-body response))))))))
