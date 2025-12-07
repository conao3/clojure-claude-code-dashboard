(ns conao3.claude-code-dashboard.frontend.core
  (:require
   ["@apollo/client" :as apollo]
   ["@apollo/client/react" :as apollo.react]
   [clojure.string :as str]
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
          projectId
          sessions {
            edges {
              node {
                id
                projectId
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
(defonce selected-session-id (r/atom nil))

(def session-messages-query
  (apollo/gql "query SessionMessages($id: ID!) {
    node(id: $id) {
      ... on Session {
        id
        sessionId
        messages {
          edges {
            node {
              id
              messageId
            }
          }
        }
      }
    }
  }"))

(defn FlexCol [{:keys [class]} & children]
  (into [:div {:class (str/join " " ["flex flex-col" class])}] children))

(defn FlexRow [{:keys [class]} & children]
  (into [:div {:class (str/join " " ["flex flex-row" class])}] children))

(defn MessageList []
  (let [session-id @selected-session-id
        result (apollo.react/useQuery session-messages-query #js {:variables #js {:id session-id}
                                                                   :skip (nil? session-id)})
        loading (.-loading result)
        error (.-error result)
        data (.-data result)]
    (cond
      (nil? session-id) [:p.text-neutral-subdued-content "Select a session"]
      loading [:p.text-gray-500 "Loading messages..."]
      error [:p.text-negative-content (str "Error: " (.-message error))]
      :else
      (let [messages (for [edge (-> data .-node .-messages .-edges)]
                       (let [^js node (.-node edge)]
                         {:id (.-id node)
                          :messageId (.-messageId node)}))]
        (if (empty? messages)
          [:p.text-neutral-subdued-content "No messages"]
          [:ul.space-y-2
           (for [message messages]
             [:li.p-2.bg-background-layer-2.rounded.text-sm.font-mono
              {:key (:id message)}
              (:messageId message)])])))))

(defn SessionList [sessions]
  (let [selected-id @selected-session-id]
    (if (empty? sessions)
      [:p.text-neutral-subdued-content "No sessions"]
      [:ul.space-y-2
       (for [session sessions]
         [:li.p-3.rounded.cursor-pointer
          {:key (:id session)
           :class (if (= (:id session) selected-id)
                    "bg-accent-background text-white"
                    "bg-background-layer-2 text-neutral-content hover:bg-gray-200")
           :on-click #(reset! selected-session-id (:id session))}
          [:div.text-sm (:sessionId session)]
          [:div.text-xs.opacity-70 (:createdAt session)]])])))

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
                          :projectId (.-projectId project)
                          :sessions
                          (for [session-edge (-> project .-sessions .-edges)]
                            (let [^js session (.-node session-edge)]
                              {:id (.-id session)
                               :projectId (.-projectId session)
                               :sessionId (.-sessionId session)
                               :createdAt (.-createdAt session)}))}))
            selected-project (some #(when (= (:id %) @selected-project-id) %) projects)
            selected-id @selected-project-id]
        [FlexRow {:class "gap-4 h-full"}
         [FlexCol {:class "w-1/4"}
          [:h2.text-xl.font-semibold.mb-4 "Projects"]
          [:ul.space-y-2.overflow-y-auto.flex-1
           (for [project projects]
             (let [has-sessions? (seq (:sessions project))]
               [:li.p-3.rounded
                {:key (:id project)
                 :class (cond
                          (not has-sessions?) "bg-disabled-background text-disabled-content"
                          (= (:id project) selected-id) "bg-accent-background text-white cursor-pointer"
                          :else "bg-background-layer-2 text-neutral-content hover:bg-gray-200 cursor-pointer")
                 :on-click (when has-sessions? #(do (reset! selected-project-id (:id project))
                                                    (reset! selected-session-id nil)))}
                (:name project)]))]]
         [FlexCol {:class "w-1/4"}
          [:h2.text-xl.font-semibold.mb-4 "Sessions"]
          [:div.overflow-y-auto.flex-1
           (if selected-project
             [SessionList (:sessions selected-project)]
             [:p.text-neutral-subdued-content "Select a project"])]]
         [FlexCol {:class "w-1/2"}
          [:h2.text-xl.font-semibold.mb-4 "Messages"]
          [:div.overflow-y-auto.flex-1
           [:f> MessageList]]]]))))

(defn App []
  [:> apollo.react/ApolloProvider {:client apollo-client}
   [FlexCol {:class "h-screen bg-background-base text-neutral-content p-8"}
    [:h1 {:class "text-3xl font-bold mb-6 text-accent-content"} "Claude Code Dashboard"]
    [:div.flex-1.min-h-0
     [:f> ProjectList]]]])

(defonce root (-> js/document (.getElementById "app") reagent.dom.client/create-root))

(defn ^:dev/after-load start []
  (reagent.dom.client/render root [App]))
