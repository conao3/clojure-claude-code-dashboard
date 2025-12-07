(ns conao3.claude-code-dashboard.frontend.core
  (:require
   ["@apollo/client" :as apollo]
   ["@apollo/client/react" :as apollo.react]
   ["js-yaml" :as yaml]
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
              ... on AssistantMessage {
                __typename
                id
                messageId
                rawMessage
              }
              ... on UserMessage {
                __typename
                id
                messageId
                rawMessage
              }
              ... on UnknownMessage {
                __typename
                id
                messageId
                rawMessage
              }
              ... on BrokenMessage {
                __typename
                id
                messageId
                rawMessage
              }
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

(defn CopyButton [{:keys [text label class]}]
  (let [copied? (r/atom false)]
    (fn [{:keys [text label class]}]
      [:button.px-2.py-1.rounded.bg-background-layer-1.opacity-0.group-hover:opacity-70.hover:opacity-100
       {:class class
        :on-click (fn [e]
                    (.stopPropagation e)
                    (-> js/navigator .-clipboard (.writeText text))
                    (reset! copied? true)
                    (js/setTimeout #(reset! copied? false) 1000))}
       (if @copied? "Copied!" (or label "Copy"))])))

(defn AssistantMessage [{:keys [message]}]
  (let [yaml-text (-> (:rawMessage message) js/JSON.parse yaml/dump)]
    [:li {:key (:id message)}
     [:details.rounded.bg-background-layer-2.border-l-4.border-transparent
      [:summary.p-2.cursor-pointer [:code (str "Assistant: " (:messageId message))]]
      [:details.m-2.p-2.rounded.bg-background-layer-1
       [:summary.cursor-pointer "Raw"]
       [:div.relative.group
        [:div.absolute.top-1.right-1.flex.gap-1
         [CopyButton {:text yaml-text :label "Copy"}]
         [CopyButton {:text (:rawMessage message) :label "Copy Raw"}]]
        [:pre.p-2.whitespace-pre-wrap.break-all yaml-text]]]]]))

(defn UserMessage [{:keys [message]}]
  (let [yaml-text (-> (:rawMessage message) js/JSON.parse yaml/dump)]
    [:li {:key (:id message)}
     [:details.rounded.bg-background-layer-2.border-l-4.border-accent-background
      [:summary.p-2.cursor-pointer [:code (str "User: " (:messageId message))]]
      [:details.m-2.p-2.rounded.bg-background-layer-1
       [:summary.cursor-pointer "Raw"]
       [:div.relative.group
        [:div.absolute.top-1.right-1.flex.gap-1
         [CopyButton {:text yaml-text :label "Copy"}]
         [CopyButton {:text (:rawMessage message) :label "Copy Raw"}]]
        [:pre.p-2.whitespace-pre-wrap.break-all yaml-text]]]]]))

(defn UnknownMessage [{:keys [message]}]
  (let [yaml-text (-> (:rawMessage message) js/JSON.parse yaml/dump)]
    [:li {:key (:id message)}
     [:details.rounded.bg-notice-background.text-white.border-l-4.border-transparent
      [:summary.p-2.cursor-pointer [:code (str "Unknown: " (:messageId message))]]
      [:details.m-2.p-2.rounded.bg-background-layer-1.text-neutral-content
       [:summary.cursor-pointer "Raw"]
       [:div.relative.group
        [:div.absolute.top-1.right-1.flex.gap-1
         [CopyButton {:text yaml-text :label "Copy"}]
         [CopyButton {:text (:rawMessage message) :label "Copy Raw"}]]
        [:pre.p-2.whitespace-pre-wrap.break-all yaml-text]]]]]))

(defn BrokenMessage [{:keys [message]}]
  [:li {:key (:id message)}
   [:details.rounded.bg-negative-background.text-white.border-l-4.border-transparent
    [:summary.p-2.cursor-pointer [:code (str "Broken: " (:messageId message))]]
    [:details.m-2.p-2.rounded.bg-background-layer-1.text-neutral-content
     [:summary.cursor-pointer "Raw"]
     [:div.relative.group
      [:div.absolute.top-1.right-1.flex.gap-1
       [CopyButton {:text (:rawMessage message) :label "Copy Raw"}]]
      [:pre.p-2.whitespace-pre-wrap.break-all
       (:rawMessage message)]]]]])

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
                         {:__typename (.-__typename node)
                          :id (.-id node)
                          :messageId (.-messageId node)
                          :rawMessage (.-rawMessage node)}))]
        (if (empty? messages)
          [:p.text-neutral-subdued-content "No messages"]
          [:ul.space-y-2
           (for [message messages]
             (case (:__typename message)
               "AssistantMessage" [AssistantMessage {:key (:id message) :message message}]
               "UserMessage" [UserMessage {:key (:id message) :message message}]
               "UnknownMessage" [UnknownMessage {:key (:id message) :message message}]
               "BrokenMessage" [BrokenMessage {:key (:id message) :message message}]
               nil))])))))

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
   [FlexCol {:class "h-screen bg-background-base text-neutral-content text-base md:text-sm p-8"}
    [:h1 {:class "text-3xl font-bold mb-6 text-accent-content"} "Claude Code Dashboard"]
    [:div.flex-1.min-h-0
     [:f> ProjectList]]]])

(defonce root (-> js/document (.getElementById "app") reagent.dom.client/create-root))

(defn ^:dev/after-load start []
  (reagent.dom.client/render root [App]))
