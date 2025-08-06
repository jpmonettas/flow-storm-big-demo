(ns data-windows-demo
  (:require [flow-storm.api :as fsa]
            [flow-storm.debugger.ui.data-windows.visualizers :as viz]
            [flow-storm.runtime.values :as fs-values]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [ants])
  (:import [javafx.scene.control Label]
           [javafx.scene.image Image ImageView]
           [javafx.scene.layout VBox GridPane]
           [java.io FileInputStream]))


;;;;;;;;;;;;;;;;;;;;;;;
;; Basic visualizers ;;
;;;;;;;;;;;;;;;;;;;;;;;

(comment

  "Clojure rocks!"

  {:a (filter odd? (range))}

  {:a ^:great {:name ^{:some-meta [1 2]}
               {:other :hello
                :bla "world"}}
   :b {:age 10}}

  )

;;;;;;;;;;;;;;;;;;;;;;;;
;; Custom visualizers ;;
;;;;;;;;;;;;;;;;;;;;;;;;

(def chess-board
  #{{:kind :king  :player :white :pos [0 5]}
    {:kind :rook  :player :white :pos [5 1]}
    {:kind :pawn  :player :white :pos [5 3]}
    {:kind :king  :player :black :pos [7 2]}
    {:kind :pawn  :player :black :pos [6 6]}
    {:kind :queen :player :black :pos [3 1]}})

(comment



  )



(comment

  (fs-values/register-data-aspect-extractor
   {:id :chess-board
    :pred (fn [val _]
            (and (set? val)
                 (let [{:keys [kind player pos]} (first val)]
                   (and kind player pos))))
    :extractor (fn [board _]
                 {:chess/board board})})

  ;; Now we can create our chess board visualizer.
  (viz/register-visualizer
   {:id :chess-board
    ;; only be available if the chess-board data extractor run on this value
    :pred (fn [val] (contains? (::fs-values/kinds val) :chess-board))

    ;; use the chess/board info to render a board with java fx
    :on-create (fn [{:keys [chess/board]}]
                 (let [kind->sprite {:king "♚" :queen "♛" :rook "♜" :bishop "♝" :knight "♞" :pawn "♟"}
                       pos->piece (->> board
                                       (mapv #(vector (:pos %) %))
                                       (into {}))]
                   {:fx/node (let [gp (GridPane.)]
                               (doall
                                (for [row (range 8) col (range 8)]
                                  (let [cell-color (if (zero? (mod (+ col (mod row 2)) 2)) "#f0d9b5" "#b58863")
                                        {:keys [kind player]} (pos->piece [row col])
                                        cell-str (kind->sprite kind "")
                                        player-color (when player (name player))]
                                    (.add gp (doto (Label. cell-str)
                                               (.setStyle (format "-fx-background-color:%s; -fx-font-size:40; -fx-text-fill:%s; -fx-alignment: center;"
                                                                  cell-color player-color))
                                               (.setPrefWidth 50))
                                          (int col)
                                          (int row)))))
                               gp)}))})


  (viz/add-default-visualizer (fn [val-data] (contains? (:flow-storm.runtime.values/kinds val-data) :chess-board)) :chess-board)

  )

;;;;;;;;;;;;;;;;;;;;
;; Datafy and nav ;;
;;;;;;;;;;;;;;;;;;;;

(comment

  (def ds (jdbc/get-datasource {:dbtype "h2" :dbname "nav_example_db"}))

  ;; create some tables
  (jdbc/execute! ds ["
create table person (
  id int auto_increment primary key,
  name varchar(32)

)"])

  (jdbc/execute! ds ["
create table emails (
  id int auto_increment primary key,
  person_id int,
  email varchar(255),
FOREIGN KEY (person_id) REFERENCES person(id))"])



  ;; insert some stuff
  (jdbc/execute! ds ["insert into person(name) values('Rich Hickey')"])
  (jdbc/execute! ds ["insert into person(name) values('Alex Miller')"])

  (jdbc/execute! ds ["insert into emails(person_id,email) values(1,'rhickey@gmail.com')"])
  (jdbc/execute! ds ["insert into emails(person_id,email) values(1,'rhickey@nubank.com')"])
  (jdbc/execute! ds ["insert into emails(person_id,email) values(2,'alex@nubank.com')"])

  (jdbc/execute! ds ["select * from emails"] {:builder-fn rs/as-unqualified-lower-maps})


  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Numbers and bytes arrays visualizations ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  ;; All int? numbers can be visualized in decimal, hex, and binary.
  42

  ;; Let's tap any binary file byte array
  (.readAllBytes (FileInputStream. "./resources/clojure-logo.png"))

  ;; Now open its data window and try the :bin-byte-array and :hex-byte-array visualizations

  )



;;;;;;;;;;;;;;;;;
;; EQL queries ;;
;;;;;;;;;;;;;;;;;

(comment
  (flow-storm.runtime.values/register-eql-query-pprint-extractor)
  (flow-storm.runtime.values/unregister-data-aspect-extractor :eql-query-pprint)
  )

(def example-data
  [{:name "Bob"
    :age 41
    :houses {1 {:rooms 5
                :address "A"}
             2 {:rooms 3
                :address "B"}}}
   {:name "Alice"
    :age 32
    :vehicles [{:type :car
                :wheels 4
                :seats #{{:kind :small :position :left}
                         {:kind :small :position :right}
                         {:kind :big :position :center}}}
               {:type :bike
                :wheels 2}]
    :trips (cycle [{:destination "Uruguay" ;; infinite!
                    :months-spent 11
                    :expenses [{:product 1
                                :price 10}
                               {:product 2
                                :price 20}]}
                   {:name "US"
                    :months-spent 1
                    :expenses [{:product 3
                                :price 30}
                               {:product 4
                                :price 40}]}])}])


;; [*]
;; [:name]
;; [:name :age :vehicles]
;; [:name :age {:vehicles [:type]}]
;; [:name :age {:vehicles [?]}]
;; [:name {:vehicles [*]}]
;; [:name :age {:vehicles [:type {:seats [?]}]}]
;; [:name :age {:vehicles [:type {:seats [:kind]}]}]
;; [{:trips [:destination {:expenses [:price]}]}]

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Realtime visualizers ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Now let's explore another feature of data windows, which allows us to see
;; values while they are changing.

(comment

  ;; Let's try something a little bit more fun, by creating a thread that
  ;; updates an atom with a sine wave

  (def scale 0.2) ;; define a scale we will soon redefine while the loop is running

  (def *changing-ref (atom 0))

  ;; create a thread that loops and updates our atom
  (def th
    (Thread.
     (fn []
       (loop [x 0]
         (when-not (Thread/interrupted)
           (Thread/sleep 3)
           (reset! *changing-ref (* scale (Math/sin x)))
           (recur (+ x 0.1)))))))

  ;; start the thread
  (.start th)

  ;; Prob it
  (fsa/probe-ref *changing-ref
                 (fn [v] v) ;; send the value to channel-1
                 (fn [v] 0) ;; leaves channel-2 always on zero
                 {})

  ;; While everything is running try to redef the `scale` var with different values.

  ;; you can now interrupt that thread
  (.interrupt th)

  )


;;;;;;;;;;;;;;;
;; Ants demo ;;
;;;;;;;;;;;;;;;


(viz/register-visualizer
 {:id :ant
  :pred (fn [val] (= "ant" (::fs-values/type val)))
  :on-create (fn [_]
               (let [scale 0.7
                     img (Image. (.toExternalForm (io/resource "ant_dark.gif")))
                     iv (doto (ImageView. img)
                          (.setFitWidth (* scale 320))
                          (.setFitHeight (* scale 240)))
                     box (VBox.)
                     lbl (doto (Label. "")
                           (.setStyle (format "-fx-font-size:30;")))]
                 (-> box .getChildren (.addAll [iv lbl]))
                 {:fx/node box
                  :data-label lbl}))
  :on-update (fn [val {:keys [data-label] :as ctx} {:keys [new-val]}]
               (let [dir-arrow {0 "↓", 1 "↘", 2 "→", 3 "↗"
                                4 "↑", 5 "↖", 6 "←", 7 "↙"}
                     dir-s (dir-arrow (:dir new-val))
                     food-s (if (:food new-val) "🍒"  "")
                     data-s (format "Dir: %s, Food: %s" dir-s food-s)]
                 (.setText ^Label data-label data-s)))})

(viz/add-default-visualizer (fn [val-data] (= "ant" (:flow-storm.runtime.values/type val-data))) :ant)

(comment

  (ants/init)
  (ants/run)
  (ants/stop)

  (def example-ant (first ants/ants))
  (def stop-watch (ants/watch-ant example-ant))
  (stop-watch)


  )
