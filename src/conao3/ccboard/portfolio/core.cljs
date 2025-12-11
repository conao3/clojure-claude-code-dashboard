(ns conao3.ccboard.portfolio.core
  (:require
   [portfolio.ui :as ui]
   conao3.ccboard.portfolio.navigation
   conao3.ccboard.portfolio.messages
   conao3.ccboard.portfolio.layout))

(defn ^:export start []
  (ui/start!
   {:config {:css-paths ["/dist/css/main.css"]
             :background/background-color "#1a1a2e"}}))
