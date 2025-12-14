(ns conao3.ccboard.portfolio.core
  (:require
   conao3.ccboard.portfolio.layout
   conao3.ccboard.portfolio.messages
   conao3.ccboard.portfolio.navigation
   [portfolio.ui :as ui]))

(defn ^:export start []
  (ui/start!
   {:config {:css-paths ["/dist/css/main.css"]
             :background/background-color "#1a1a2e"}}))
