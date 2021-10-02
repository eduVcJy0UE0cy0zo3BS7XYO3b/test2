(defproject backend "0.0.1-SNAPSHOT"
  :description "DESC."
  :dependencies [;; Язык сам по себе
                 [org.clojure/clojure "1.10.3"]

                 ;; Роуты
                 [metosin/reitit "0.5.15"]

                 ;; SQL шаблоны
                 [com.layerware/hugsql "0.5.1"]

                 ;; Kebab > Snake > Caml
                 [camel-snake-kebab "0.4.2"]

                 ;; access to db
                 [org.clojure/java.jdbc "0.7.12"]
                 [org.postgresql/postgresql "9.4.1207"]

                 ;; JDBC connection bade simple
                 [conman "0.9.1"]

                 [tick "0.5.0-RC2"]

                 ;; component managment
                 [mount "0.1.16"]

                 ;; http server adapter
                 [ring/ring-jetty-adapter "1.4.0"]

                 ;; open door for frontend
                 [ring-cors "0.1.13"]

                 ;; Получение переменных окружения для
                 ;; доступа к базе данных.
                 [environ "1.2.0"]

                 ;; Тестирование
                 [ring/ring-mock "0.4.0"]]

  :repl-options {:init-ns backend.core}
  :main backend.core
  :aot [backend.core])

;; https://12factor.net/
;; https://www.restapitutorial.com/
;; http://www.learndatalogtoday.org/
