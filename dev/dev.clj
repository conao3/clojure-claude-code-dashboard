(ns dev
  (:require
   [clojure.string :as str])
  (:import [io.undertow.server HttpServerExchange]))

(defn api-route? [^HttpServerExchange exchange config]  
  (let [path (.getRequestPath exchange)]  
    (str/starts-with? path "/api")))
