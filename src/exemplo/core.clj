(ns exemplo.core
  (:require [io.pedestal.http :as http]
            [hiccup.page :refer [html5]]
            [io.pedestal.http.body-params :refer [body-params]]
            [clojure.java.jdbc :as j]
            [io.pedestal.http.route :as http.route]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

(def db-uri
  {:dbtype   "postgresql"
   :dbname   "myapp"
   :host     "localhost"
   :user     "postgres"
   :password ""})

(defn head
  [_]
  [:head
   [:meta {:charset "UTF-8"}]
   [:title]])

(defn index
  [_]
  {:headers {"Content-Type" "text/html"}
   :body    (html5
              (head _)
              [:body
               [:form
                {:method "POST"
                 :action "/user"}
                "name"
                [:input {:name "nome"}]
                [:input {:type  "submit"
                         :value "Submit"}]]])
   :status  200})

(defn get-user
  [{{:keys [id]} :path-params
    :as          req}]
  (let [{:keys [id nome]} (first (j/query db-uri ["SELECT id,nome FROM usuarios WHERE id = ?"
                                                  (edn/read-string id)]))]
    {:headers {"Content-Type" "text/html"}
     :body    (html5
                (head req)
                [:body
                 [:div "id:" id]
                 [:div "name:" nome]])
     :status  200}))

(defn create-user
  [{{:keys [nome]} :form-params}]
  (let [id (:id (first (j/insert! db-uri :usuarios {:nome nome})))]
    {:headers {"Location" (str "/user/" id)}
     :status  301}))

(def routes
  `#{["/" :get index]
     ["/user" :post [~(body-params) create-user]]
     ["/user/:id" :get [~(body-params) get-user]]})


(def service
  {:env          :prod
   ::http/port   8080
   ::http/join?  false
   ::http/routes routes
   ::http/type   :jetty})

(defonce http-server (atom nil))

(defn -dev-run
  [& _]
  (j/execute! (assoc db-uri :dbname "")
              (str "DROP DATABASE IF EXISTS " (:dbname db-uri) "; ")
              {:transaction? false})
  (j/execute! (assoc db-uri :dbname "")
              (str "CREATE DATABASE " (:dbname db-uri) "; ")
              {:transaction? false})
  (j/execute! db-uri (slurp (io/resource "schema.sql")))
  (swap! http-server (fn [st]
                       (when st
                         (http/stop st))
                       (-> service
                           (assoc :env :dev)
                           (update ::http/routes (fn [routes]
                                                   #(http.route/expand-routes routes)))
                           http/default-interceptors
                           http/dev-interceptors
                           http/create-server
                           http/start))))

(defn -main
  [& _]
  (swap! http-server (fn [st]
                       (when st
                         (http/stop st))
                       (-> service
                           http/default-interceptors
                           http/create-server
                           http/start))))