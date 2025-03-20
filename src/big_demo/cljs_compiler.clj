(ns big-demo.cljs-compiler
  (:require [cljs.main :as cljs.m]))

;; lein repl :connect localhost:`cat .nrepl-port`
;; then run (big-demo.cljs-compiler/cljs-repl)
(defn cljs-repl []
  (cljs.m/-main "--repl"))
