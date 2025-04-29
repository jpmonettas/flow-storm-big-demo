(ns big-demo.async-flow
  (:require [clojure.core.async.flow :as flow]
            [clojure.core.async :as async]))


;;                        0,1,2..  > |numbers-in-ch|
;;                                          |
;;                                          |
;;                         +----------------+---------------+ :firsts
;;                         |    nums-counter  {:found 42}   |--------------------------+ {:num 10}
;;                         +--------+-------------------+---+                          |
;;                                        :nums         |                              |
;;                                    --/       \-      |------------------------------+-------------|
;;                                 --/            \--                                  |             |
;;                               -/                  \-  {:nums-found 10 :num 3}       |             |
;;                            --/                      \--                             |             |
;;                         --/                            \-                           |             |
;;                       -/:nums-in                         \- :nums-in                |             | :anything-in
;;     +----------------/---------------+     +---------------\----------------+       | +-----------+---------+
;;     |  only-odds  {:odds-found 42}   |     |  only-evens {:evens-found 42}  |       | |       forwarder     |
;;     +--------+-----------------------+     +--------------------------------+       | +-----------+---------+
;;                            :odds              :evens                                |             | :anything-out
;;                              \                    /                                 |             |
;;                               \                 -/                                  |             |
;;    {:ods-found 42 :odd 2}      \              -/    {:evens-found 42 :even 2}       |             |
;;                                 \-           /                                      |             |
;;                                   \        -/                                       |             |
;;                                    \     -/                                         |             |
;;                                     \  -/                                           |             |
;;                                      \/                                             |             |
;;                                  :things                                            |             |
;;                              +------------+ :logger                                 |             |
;;                              | serializer |-------------------------------------------------------+
;;                              +------------+
;;                                 :strings
;;                                     |
;;                                     |
;;                                     |
;;                                 :strings
;;                              +------------+
;;                              |   printer  |
;;                              +------------+


(def numbers-in-ch (async/chan))

(defn nums-counter
  ([] {:outs {:nums "A map with :nums-found and :num"
              :firsts "A map with :num"}})
  ([_] {:nums-found 0
        ::flow/in-ports {:numbers-in-ch numbers-in-ch}})
  ([state _] state)
  ([state ch-id msg]
   (if (= :numbers-in-ch ch-id)
     (let [num msg]
       (let [{:keys [nums-found] :as state'} (update state :nums-found inc)]
         [state' (cond
                   (< nums-found 3) {:firsts [{:num num}]}
                   (<= 3 nums-found 6) {:nums [{:nums-found nums-found :num num}]
                                        :firsts [{:num num}]}
                   :ese {:nums [{:nums-found nums-found :num num}]})]))
     [state nil])))

(defn only-evens
  ([] {:ins  {:nums-in "A map with :num key"}
       :outs {:evens "A map with :evens-found and :even"}})
  ([_] {:evens-found 0})
  ([state _] state)
  ([state ch-id msg]
   (if (= :nums-in ch-id)
     (let [{:keys [num]} msg]
       (Thread/sleep 500)
       (if (even? num)
         (let [{:keys [evens-found] :as state'} (update state :evens-found inc)]
           [state' {:evens [(assoc msg :evens-found evens-found :even num)]}])
         [state nil]))
     [state nil])))

(defn only-odds
  ([] {:ins  {:nums-in "A number"}
       :outs {:odds "A map with :odds-found and :odd"}})
  ([_] {:odds-found 0})
  ([state _] state)
  ([state ch-id msg]
   (if (= :nums-in ch-id)
     (let [{:keys [num]} msg]
       (if (odd? num)
         (let [;; _ (Thread/sleep 1000)
               {:keys [odds-found] :as state'} (update state :odds-found inc)]
           [state' {:odds [(assoc msg :odds-found odds-found :odd num)]}])
         [state nil]))
     [state nil])))

(defn forwarder
  ([] {:ins  {:anything-in "Anything"}
       :outs {:anything-out "The same things that went in"}})
  ([_])
  ([state _] state)
  ([_ ch-id msg]
   (if (= :anything-in ch-id)
     [nil {:anything-out [msg]}]
     [nil nil])))

(defn serializer
  ([] {:ins {:things "Anything comming here will be serialized to a string"
             :logger "Anything comming here will be serialized to a string starting with Logger"}
       :outs {:strings "String serialization"}})
  ([_])
  ([state _] state)
  ([_ ch-id {:keys [odds-found evens-found nums-found num] :as thing}]
   (case ch-id
     :things [nil {:strings [(format "Odds cnt: %d, Evens cnt: %d, Nums cnt: %d, Num: %d" odds-found evens-found nums-found num)]}]
     :logger [nil {:strings [(str "Logger" thing)]}])))

(defn printer
  ([] {:ins {:strings "Any string comming here will be printed"}})
  ([_])
  ([state _] state)
  ([_ ch-id msg]
   (when (= :strings ch-id)
     (println msg))))

(def system-graph
  (flow/create-flow
   {:procs
    {:nums-counter {:proc (flow/process nums-counter)}
     :only-evens   {:proc (flow/process only-evens)
                    :chan-opts {:nums-in {:buf-or-n (async/sliding-buffer 1)
                                          :xform (map (fn [msg] (update msg :num #(* 100 %))))}}}
     :only-odds    {:proc (flow/process only-odds)}
     :forwarder    {:proc (flow/process forwarder)}
     :serializer   {:proc (flow/process serializer)}
     :printer      {:proc (flow/process printer)}}

    :conns
    [[[:nums-counter :nums]         [:only-odds  :nums-in]]
     [[:nums-counter :nums]         [:only-evens :nums-in]]
     [[:nums-counter :nums]         [:forwarder :anything-in]]
     [[:nums-counter :firsts]       [:serializer :logger]]
     [[:forwarder    :anything-out] [:serializer :logger]]
     [[:only-odds    :odds]         [:serializer :things]]
     [[:only-evens   :evens]        [:serializer :things]]
     [[:serializer   :strings]      [:printer :strings]]]}))

(comment

  (flow/start  system-graph)
  (flow/resume system-graph)
  (flow/pause  system-graph)

  (async/>!! numbers-in-ch 2)

  (dotimes [i 10]
    (async/>!! numbers-in-ch i))

  )


;; - I always record a total order timeline if I see multiple threads, and then look at the fns
;; - For a single thread look at the tree and fns list counts
;; - `only-evens` and `printer` called with the description arity, but no init
;; - Follow the code with your IDE asisted by runtime values
;; - Look at `create-flow` in emacs asisted by runtime values

;; - On a different flow let's start the system
;; - We see 3 threads
;; - Look at them in the total order timeline
;; - Add bookmarks for :
;;   - "Only evens process started and waiting paused at the control channel"
;;   - "Printer process started and waiting paused at the control channel"
;; - We can see `start` spawn all our processes in paused
;; - Resume the graph and see new recordings on each thread
;; - Move forward to the handle command and bookmark "Process command handling and state transition"
;; - Jump to the end of the `only-evens` process
;; - Notice it is waiting on two channels, one is our in-channel, guess the other is the control one
;; - We can check our assumptions, nav with the DW to the unknown chan, use the search tool and look at its creation
