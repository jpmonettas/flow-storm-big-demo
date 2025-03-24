(defproject big-demo "0.1.0-SNAPSHOT"
  :description "FlowStorm big demo"
  :dependencies [[http-kit/http-kit "2.8.0"]
                 [ring/ring "1.13.0"]
                 [compojure/compojure "1.7.1"]
                 [hiccup/hiccup "2.0.0-RC3"]
                 [com.github.seancorfield/next.jdbc "1.3.994"]
                 [com.h2database/h2 "2.2.224"]]
  :resource-paths ["src" "resources"]
  :jvm-opts ["-Dvisualvm.display.name=BigDemo"])
