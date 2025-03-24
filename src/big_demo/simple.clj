(ns big-demo.simple)

(defn quick-sort [[pivot & coll]]
  (when pivot
    (let [smaller-than-pivot   (filterv (fn ltp [v] (< v pivot)) coll)
          bigger-than-pivot (filterv (fn gtp [v] (>= v pivot)) coll)]
      (concat (quick-sort smaller-than-pivot)
              [pivot]
              (quick-sort bigger-than-pivot)))))


(comment

  (quick-sort '[3 4 2 7 8 0 9 8 2 3])

  )
