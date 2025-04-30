(ns big-demo.web-app
  (:require [org.httpkit.server :as http-server]
            [compojure.core :refer [GET POST routes]]
            [ring.middleware.params :refer [wrap-params]]
            [hiccup.core :as h]
            [ring.adapter.jetty :as jetty]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def database (jdbc/get-datasource {:dbtype "h2" :dbname "todos"}))

(def styles
  "body {
      font-family: Arial, sans-serif;
      background: #f9f9f9;
      padding: 2em;
      max-width: 600px;
      margin: auto;
      color: #333;
    }

    h1 {
      color: #2c3e50;
      text-align: center;
      margin-bottom: 1em;
    }

    ul {
      list-style: none;
      padding: 0;
    }

    li {
      background: #ffffff;
      padding: 1em;
      margin-bottom: 0.5em;
      border-radius: 8px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      box-shadow: 0 2px 5px rgba(0, 0, 0, 0.05);
    }

    li span {
      flex-grow: 1;
    }

    .complete {
      margin-left: 10px;
      color: green;
      font-weight: bold;
    }

    form {
      margin: 0;
    }

    input[type=\"submit\"] {
      background-color: #3498db;
      color: white;
      border: none;
      padding: 0.5em 1em;
      border-radius: 4px;
      cursor: pointer;
      transition: background 0.2s ease;
    }

    input[type=\"submit\"]:hover {
      background-color: #2980b9;
    }

    form[action=\"/add-todo\"] {
      margin-top: 2em;
      display: flex;
      gap: 0.5em;
      align-items: center;
    }

    input[type=\"text\"] {
      flex-grow: 1;
      padding: 0.5em;
      border: 1px solid #ccc;
      border-radius: 4px;
    }

    label {
      font-weight: bold;
    }")

(defn init-db []
  (jdbc/execute! database ["
create table todos (
  id int auto_increment primary key,
  text varchar(32),
  done int)"])

  (jdbc/execute! database ["insert into todos(text,done) values('Call doctor',0)"])
  (jdbc/execute! database ["insert into todos(text,done) values('Buy milk',0)"])
  (jdbc/execute! database ["insert into todos(text,done) values('Review pull request',0)"]))

(defn add-todo [todo-text]
  (jdbc/execute!
   database
   ["insert into todos(text,done) values(?,0)" todo-text]))

(defn mark-todo-done [todo-id]
  (jdbc/execute!
   database
   ["update todos set done=1 where id=?" todo-id]))

(defn all-todos []
  (->> (jdbc/execute!
        database
        ["select * from todos"]
        {:builder-fn rs/as-unqualified-lower-maps})
       (mapv (fn build-todo [todo] (update todo :done {0 false 1 true})))))

(defn render-main-page []
  {:status 200
   :body
   (h/html
       [:html
        [:head
         [:style styles]]
        [:body
         [:h1 "TODOs app"]
         [:ul
          (->> (all-todos)
               (map (fn build-todo-item [{:keys [id text done]}]
                      (if done

                         ;; :li for done
                         [:li [:span text] [:span.complete "Complete"]]

                         ;; :li for not done
                         [:li
                          [:span text]
                          [:form {:action "/mark-done" :method "POST"}
                           [:input {:name "todo-id" :type "hidden" :value (str id)}]
                           [:input {:type "submit" :value "Mark as done"}]]])))
               doall)]

         [:form {:action "/add-todo" :method "POST"}
          [:label {:for "todo-text"} "New todo:"]
          [:input {:id "todo-text" :name "todo-text" :type "text"}]
          [:input {:type "submit" :value "Add todo"}]]]])})

(def handler
  (routes
   (GET "/" req (render-main-page))

   (POST "/add-todo" req
     (let [todo-text (get-in req [:form-params "todo-text"])]
       (add-todo todo-text)
       (render-main-page)))

   (POST "/mark-done" req
     (let [todo-id (parse-long (get-in req [:form-params "todo-id"]))]
       (mark-todo-done todo-id)
       (render-main-page)))))

(defn wrap-middlewares [handler]
  (-> handler
      wrap-params))

(defn -main [& args]
  (println "Populating db...")
  (init-db)
  (println "Starting http server")
  (http-server/run-server
   (wrap-middlewares handler)
   {:port 7744})
  (println "All done"))


;;;;;;;;;;;;;;;;;;
;; For the repl ;;
;;;;;;;;;;;;;;;;;;

(comment

  ;; the first time to create db, tables and pre populate with some data
  (init-db)

  ;; start the http server
  (def server (http-server/run-server

               (-> (fn handle [req] (#'handler req))
                   wrap-middlewares)

               {:port 7744}))

  (def server (jetty/run-jetty

               (-> (fn handle [req] (#'handler req))
                   wrap-middlewares)

               {:port 7744
                :join? false}))

  ;; to stop the http server
  (server)


  (handler user/r)

  )
