(ns big-demo.programmable
  (:require [flow-storm.runtime.indexes.api :as idx-api]))


(comment

  (def timeline (idx-api/get-timeline 0 79))

  (count timeline)

  (take 3 timeline)

  (get timeline 0)

  (->> timeline
     (take 3)
     (map idx-api/as-immutable))

  (def expr (get timeline 4))

  (idx-api/get-expr-val expr)

  (idx-api/get-sub-form timeline expr)

  ;; Query the forms of the first 10 expressions that return nil
  (->> timeline
       (keep (fn [entry]
               (when (and (idx-api/expr-trace? entry)
                          (nil? (idx-api/get-expr-val entry)))
                 (idx-api/get-sub-form timeline entry))))
       (take 10))
  )
