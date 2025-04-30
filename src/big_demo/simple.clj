(ns big-demo.simple)

;;;;;;;;;;;;;;;;;;;;;;;;;
;; Recursive functions ;;
;;;;;;;;;;;;;;;;;;;;;;;;;

(defn quick-sort [[pivot & coll]]
  (when pivot
    (let [smaller-than-pivot   (filterv (fn ltp [v] (< v pivot)) coll)
          bigger-than-pivot (filterv (fn gtp [v] (>= v pivot)) coll)]
      (concat (quick-sort smaller-than-pivot)
              [pivot]
              (quick-sort bigger-than-pivot)))))


(comment

  (quick-sort '[3 4 2 7 8 0 9 8 2 3])
  (quick-sort '[3 4 2 7 ])
  user/c
  )

;;;;;;;;;;;;;;;;
;; Exceptions ;;
;;;;;;;;;;;;;;;;

(defn foo [n]
  (->> (range n)
       (filter odd?)
       (partition-all 2)
       (map second)
       (drop 10)
       (reduce +)))


(comment

  (foo 72) ;; works
  (foo 70) ;; throws


  )


;;;;;;;;;;;;;;;
;; Libraries ;;
;;;;;;;;;;;;;;;


(comment

  (require '[clojure.data.codec.base64 :as b64])


  (-> "Clojure rocks!!!"
      .getBytes
      b64/encode
      String.)

  )

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Total order timeline ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (def c [3 4 2 7 8 0 9 8 2 3])
  (pmap quick-sort [(shuffle c)
                    (shuffle c)
                    (shuffle c)
                    (shuffle c)])
  )

;;;;;;;;;;;;;;;;;;
;; Uninstrument ;;
;;;;;;;;;;;;;;;;;;

(defn sum [a b]
  (+ a b))

;;;;;;;;;;;;;;;;;;;;
;; Snapshot value ;;
;;;;;;;;;;;;;;;;;;;;

(comment


  (let [a (java.util.ArrayList.)]
    (count a)
    (.add a "hello")
    (count a)
    (.add a "world")
    (.add a "!")
    a)



  (ns some-non-instrumented-ns)
  (extend-protocol flow-storm.runtime.values/SnapshotP
    java.util.ArrayList
    (snapshot-value [a]
      {:ref/type 'java.util.ArrayList
       :snapshot/items (into [] a)}))



  )
