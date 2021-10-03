(ns backend.core
  (:require [backend.db :as db]
            [clojure.tools.logging :as log]
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

(def new-pacient-spec
  [:map
   [:second-name ne-string]
   [:first-name  ne-string]
   [:third-name  ne-string]
   [:sex         [:enum "М" "Ж"]]
   [:dob         dob?]
   [:address     ne-string]
   [:oms         [:and int?
                  [:> 999999999999999]
                  [:< 10000000000000000]]]])

(def pacient-spec (mu/assoc new-pacient-spec :id int?))

(defn return-error [error-message]
  {:status 404
   :body {:message error-message}})

(defn insert-pacient [pacient]
  (let [id (:id (first (db/wrap-insert-pacient pacient)))]
    (if id
      {:status 200
       :body {:message "Пациент добавлен."
              :id id}}
      {:status 404
       :body {:message "Пациент не добавлен."}})))

(defn update-pacient [pacient]
  (if (= (db/wrap-update-pacient pacient) 1)
    {:status 200
     :body {:message "Пациент обновлён."}}
    {:status 404
     :body {:message "Пациент не обновлён."}}))

(defn remove-pacient [pacient]
  (if (= (db/remove-pacient-by-id pacient) 1)
    {:status 200
     :body {:message "Пациент удалён."}}
    {:status 404
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
        :parameters {:body pacient-spec}
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
                       (return-error "Пациент с таким ОМС уже существует.")
                       (insert-pacient pacient))
                     ))}}]
     ["/pacient/update"
      {:put
       {:summary "Обновление данных о пациенте."
        :parameters {:body pacient-spec}
        :handler
        (fn [request]
          (let [pacient (-> request :body-params)
                curr-pacient-from-db (db/get-user-by-id pacient)
                same-oms? (= (:oms pacient) (:oms curr-pacient-from-db))]
            (if same-oms?
              (update-pacient pacient)
              (if (oms-already-exist? pacient)
                (return-error "Пациент с таким ОМС уже существует.")
                (update-pacient pacient)))))}}]
     ["/pacient/remove"
      {:delete
       {:summary "Удаление данных о пациенте."
        :parameters {:body pacient-spec}
        :handler  (fn [request]
                    (let [pacient (-> request :body-params)]
                      (remove-pacient pacient)))}}]]
    {:data {
            :coercion (reitit.coercion.malli/create
                       {:error-keys #{:type :coercion :in #_:schema #_:value #_:errors :humanized #_:transformed}
                        :validate true
                        :enabled true
                        :strip-extra-keys true
                        :default-values true
                        :options nil})
            :muuntaja   m/instance
            :middleware [#(wrap-cors %
                                     :access-control-allow-origin [#".*"]
                                     :access-control-allow-methods [:get :post :put :delete])
                         parameters/parameters-middleware
                         muuntaja/format-negotiate-middleware
                         muuntaja/format-response-middleware
                         (reitit-exception/create-exception-middleware
                          (merge
                           reitit-exception/default-handlers
                           {::reitit-exception/wrap
                            (fn [handler ^Exception e request]
                              (log/error e (.getMessage e))
                              (handler e request))}))
                         muuntaja/format-request-middleware
                         coercion/coerce-request-middleware
                         coercion/coerce-response-middleware]}})))

(mount/defstate
  ^{:on-reload :noop}
  http-server
  :start
  (jetty/run-jetty #'app
                   {:port 8000
                    :host "0.0.0.0"
                    :join? false})
  :stop (.stop http-server))

(defn -main []
  (println (str "Running web server on 0.0.0.0:8000"))
  (mount/start))
