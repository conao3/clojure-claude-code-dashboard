(ns conao3.claude-code-dashboard.frontend.core
  (:require
   ["@apollo/client" :as apollo]
   ["@apollo/client/react" :as apollo.react]
   [reagent.core :as r]
   [reagent.dom.client :as reagent.dom.client]))

(enable-console-print!)

(defonce apollo-client
  (apollo/ApolloClient. #js {:link (apollo/HttpLink. #js {:uri "/api/graphql"})
                             :cache (apollo/InMemoryCache.)}))

(def projects-query
  (apollo/gql "query Projects {
    projects {
      edges {
        node {
          id
          name
          rawName
          sessions {
            edges {
              node {
                id
                sessionId
                createdAt
              }
            }
          }
        }
      }
    }
  }"))

(defonce selected-project-id (r/atom nil))

(defn SessionList [sessions]
  (if (empty? sessions)
    [:p.text-neutral-subdued-content "No sessions"]
    [:ul.space-y-2
     (for [session sessions]
       [:li.p-3.bg-background-layer-2.rounded.hover:bg-gray-200.cursor-pointer
        {:key (:id session)}
        [:div.text-sm.text-neutral-subdued-content (:sessionId session)]
        [:div.text-xs.text-disabled-content (:createdAt session)]])]))

(defn ProjectList []
  (let [result (apollo.react/useQuery projects-query)
        loading (.-loading result)
        error (.-error result)
        data (.-data result)]
    (cond
      loading [:p.text-gray-500 "Loading..."]
      error [:p.text-negative-content (str "Error: " (.-message error))]
      :else
      (let [projects (for [project-edge (-> data .-projects .-edges)]
                       (let [^js project (.-node project-edge)]
                         {:id (.-id project)
                          :name (.-name project)
                          :rawName (.-rawName project)
                          :sessions
                          (for [session-edge (-> project .-sessions .-edges)]
                            (let [^js session (.-node session-edge)]
                              {:id (.-id session)
                               :sessionId (.-sessionId session)
                               :createdAt (.-createdAt session)}))}))
            selected-project (some #(when (= (:id %) @selected-project-id) %) projects)
            selected-id @selected-project-id]
        [:div.flex.gap-8
         [:div {:class "w-1/3"}
          [:h2.text-xl.font-semibold.mb-4 "Projects"]
          [:ul.space-y-2
           (for [project projects]
             [:li.p-3.rounded.cursor-pointer
              {:key (:id project)
               :class (if (= (:id project) selected-id)
                        "bg-accent-background text-white"
                        "bg-background-layer-2 text-neutral-content hover:bg-gray-200")
               :on-click #(reset! selected-project-id (:id project))}
              (:name project)])]]
         [:div {:class "w-2/3"}
          [:h2.text-xl.font-semibold.mb-4 "Sessions"]
          (if selected-project
            [SessionList (:sessions selected-project)]
            [:p.text-neutral-subdued-content "Select a project"])]]))))

(defn App []
  [:> apollo.react/ApolloProvider {:client apollo-client}
   [:div.min-h-screen.bg-background-base.text-neutral-content.p-8
    [:h1 {:class "text-3xl font-bold mb-6 text-accent-content"} "Claude Code Dashboard"]
    [:f> ProjectList]]])

(defonce root (-> js/document (.getElementById "app") reagent.dom.client/create-root))

(defn ^:dev/after-load start []
  (reagent.dom.client/render root [App]))
