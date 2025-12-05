(ns conao3.claude-code-dashboard.frontend.core
  (:require
   ["@apollo/client" :as apollo]
   ["@apollo/client/react" :as apollo.react]
   [reagent.dom.client :as reagent.dom.client]))

(enable-console-print!)

(defonce apollo-client
  (apollo/ApolloClient. #js {:link (apollo/HttpLink. #js {:uri "/api/graphql"})
                             :cache (apollo/InMemoryCache.)}))

(defn HelloMessage []
  (let [result (apollo.react/useQuery (apollo/gql "query Hello { hello }"))
        loading (.-loading result)
        error (.-error result)
        data (.-data result)]
    (cond
      loading [:p "Loading..."]
      error [:p (str "Error: " (.-message error))]
      :else [:p (.-hello data)])))

(defn App []
  [:> apollo.react/ApolloProvider {:client apollo-client}
   [:div
    [:h1 "Claude Code Dashboard"]
    [:f> HelloMessage]]])

(defonce root (-> js/document (.getElementById "app") reagent.dom.client/create-root))

(defn ^:dev/after-load start []
  (reagent.dom.client/render root [App]))
 
