(ns backend.core
  (:require [backend.db :as db]
            [taoensso.timbre :as timbre :refer [log info error]]
            [ring.logger :as logger]
            [reitit.ring.middleware.dev :as dev]
            [backend.time_check :refer [dob?]]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.exception :as reitit-exception]
            [reitit.ring.middleware.parameters :as parameters]
            [malli.util :as mu]
            [reitit.coercion.malli]
            [environ.core :refer [env]]
            [muuntaja.core :as m]
            [malli.core :as malli]
            [reitit.ring :as ring]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [ring.middleware.cors :refer [wrap-cors]]
            [mount.core :as mount]
            [ring.adapter.jetty :as jetty])
  (:gen-class))

(def ne-string [:string {:min 1}])

(defn exp [x n]
  (reduce * (repeat n x)))

(def min-oms (exp 10 15))               ;1000.0000.0000.0000
(def max-oms (- (exp 10 16) 1))         ;9999.9999.9999.9999

;; https://www.tfoms.nnov.ru/documents/foms/2020/Prikaz_49_05.03.2020.pdf
(def new-pacient-spec
  [:map
   [:second-name ne-string]
   [:first-name  ne-string]
   [:third-name  ne-string]
   [:sex         [:enum "М" "Ж"]]
   [:dob         dob?]
   [:address     ne-string]
   [:oms         [:and int?             ; страница 14
                  [:>= min-oms]
                  [:<= max-oms]]]])

(def pacient-spec (mu/assoc new-pacient-spec :id int?))

(defn message-name [name]
  (case name
    :oms-already-exist "Пациент с таким ОМС уже существует."
    "Неизвестная ошибка"))

(defn return-error [error-message]
  {:status 400
   :body {:message (message-name error-message)}})

(defn affected? [statement] (= statement 1))

(defn insert-pacient [pacient]
  (if-let [id (:id (db/wrap-insert-pacient pacient))]
    {:status 200
     :body {:message "Пациент добавлен." :id id}}
    {:status 400
     :body {:message "Пациент не добавлен."}}))

(defn update-pacient [pacient]
  (if (affected? (db/wrap-update-pacient pacient))
    {:status 200
     :body {:message "Пациент обновлён."}}
    {:status 400
     :body {:message "Пациент не обновлён."}}))

(defn remove-pacient [pacient]
  (if (affected? (db/remove-pacient-by-id pacient))
    {:status 200
     :body {:message "Пациент удалён."}}
    {:status 400
     :body {:message "Пациент не удалён."}}))

(defn oms-already-exist? [pacient]
  (not (nil? (db/get-user-by-oms pacient))))

(def app
  (ring/ring-handler
   (ring/router
    [["/health"
      {:get
       {:summary "Возвращает 'ок'."
        :handler (fn [request]
                   {:status 200
                    :body {:health "ok"}})}
       :post
       {:summary "Возвращает переданные поля."
        :parameters {:body new-pacient-spec}
        :handler (fn [request]
                   {:status 200
                    :body (:body-params request)})}}]

     ["/pacients"
      {:get
       {:summary "Получение списка всех пациентов."
        :handler (fn [request]
                   {:status 200
                    :body (into [] (db/wrap-all-pacients))})}}]

     ["/pacient/insert"
      {:post
       {:summary "Внесение нового пациента."
        :parameters {:body new-pacient-spec}
        :handler (fn [request]
                   (let [pacient (-> request :body-params)]
                     (if (oms-already-exist? pacient)
                       (return-error :oms-already-exist)
                       (insert-pacient pacient))))}}]

     ["/pacient/update"
      {:put
       {:summary "Обновление данных о пациенте."
        :parameters {:body pacient-spec}
        :handler
        (fn [request]
          (let [pacient (-> request :body-params)
                curr-pacient-from-db (db/get-user-by-id pacient)
                same-oms? (= (:oms pacient) (:oms curr-pacient-from-db))]
            (cond same-oms? (update-pacient pacient)
                  (not (oms-already-exist? pacient)) (update-pacient pacient)
                  :else (return-error :oms-already-exist))))}}]

     ["/pacient/remove"
      {:delete
       {:summary "Удаление данных о пациенте."
        :parameters {:body pacient-spec}
        :handler  (fn [request]
                    (let [pacient (-> request :body-params)]
                      (remove-pacient pacient)))}}]]

    {;; :reitit.middleware/transform dev/print-request-diffs
     :data {:coercion (reitit.coercion.malli/create
                       {:error-keys #{:in :value :humanized}})
            :muuntaja   m/instance
            :middleware[#(logger/wrap-with-logger %
                                                  {:log-fn
                                                   (fn [{:keys [level throwable message]}]
                                                     (log level throwable message))})
                        #(wrap-cors %
                                    :access-control-allow-origin
                                    [#".*"]
                                    :access-control-allow-methods
                                    [:get :post :put :delete])
                        muuntaja/format-negotiate-middleware
                        muuntaja/format-response-middleware
                        muuntaja/format-request-middleware
                        (reitit-exception/create-exception-middleware
                         (merge
                          reitit-exception/default-handlers
                          {::reitit-exception/wrap
                           (fn [handler ^Exception e request]
                             (error e (.getMessage e))
                             (handler e request))}))
                        coercion/coerce-request-middleware
                        coercion/coerce-response-middleware]}})))

(mount/defstate ^{:on-reload :noop}
  http-server
  :start (jetty/run-jetty #'app {:port 8000 :host "0.0.0.0" :join? false})
  :stop (.stop http-server))

(defn -main []
  (info (str "Running web server on 0.0.0.0:8000"))
  (mount/start))
