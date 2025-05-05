(ns big-demo.programmable
  (:require [flow-storm.runtime.indexes.api :as idx-api]))


(comment

  (def timeline (idx-api/get-timeline 0 94))

  (count timeline)

  (take 3 timeline)

  (get timeline 0)

  (->> timeline
     (take 3)
     (map idx-api/as-immutable))

  (def expr (get timeline 8))

  (idx-api/get-expr-val expr)

  (idx-api/get-sub-form timeline 8)

  ;; Query the forms of the first 10 expressions that return nil
  (->> timeline
       (keep-indexed (fn [idx entry]
                       (when (and (idx-api/expr-trace? entry)
                                  (nil? (idx-api/get-expr-val entry)))
                         (idx-api/get-sub-form timeline idx))))
       (take 10))
  )
