(ns conao3.claude-code-dashboard-test
  (:require
   [clojure.test :as t]
   [conao3.claude-code-dashboard :as c]))

(t/deftest add-test
  (t/is (= 3 (c/add 1 2))))
