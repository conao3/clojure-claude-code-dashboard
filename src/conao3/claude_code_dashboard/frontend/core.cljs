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
                             :cache (apollo/InMemoryCache.)
                             :defaultOptions #js {:query #js {:errorPolicy "all"}
                                                  :watchQuery #js {:errorPolicy "all"}}}))

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
              __typename
              messageId
              rawMessage
              ... on Node { id }
              ... on AssistantMessage {
                assistantMessage: message {
                  content {
                    type
                    text
                    thinking
                    id
                    name
                    input
                    tool_use_id
                    content
                  }
                }
              }
              ... on UserMessage {
                userMessage: message {
                  content {
                    type
                    text
                    tool_use_id
                    content
                  }
                }
              }
              ... on FileHistorySnapshotMessage {
                snapshot {
                  trackedFileBackups
                }
                isSnapshotUpdate
              }
              ... on QueueOperationMessage {
                operation
                timestamp
                content
                queueSessionId
              }
              ... on SystemMessage {
                subtype
                systemContent: content
                isMeta
                timestamp
                level
                compactMetadata {
                  trigger
                  preTokens
                }
              }
              ... on SummaryMessage {
                summary
                leafUuid
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

(defn CopyButton []
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

(defn ToolResultBlock [{:keys [block]}]
  [:div.m-2.p-2.rounded.bg-background-layer-1
   [:div.font-semibold "Tool Result"]
   [:div.text-xs.opacity-70 (str "Tool Use ID: " (:tool_use_id block))]
   (when (:content block)
     [:pre.mt-2.p-2.whitespace-pre-wrap.break-all (:content block)])])

(defn ContentBlock [{:keys [block tool-results]}]
  (case (:type block)
    "text"
    [:div.p-2.whitespace-pre-wrap.break-all (:text block)]

    "thinking"
    [:div.m-2.p-2.rounded.bg-background-layer-1.text-neutral-subdued-content
     [:div.font-semibold "Thinking"]
     [:pre.p-2.whitespace-pre-wrap.break-all (:thinking block)]]

    "tool_use"
    (let [result (get tool-results (:id block))]
      [:<>
       [:div.m-2.p-2.rounded.bg-background-layer-1
        [:div.font-semibold (str "Tool: " (:name block))]
        [:div.text-xs.opacity-70 (str "ID: " (:id block))]
        (when (:input block)
          [:pre.mt-2.p-2.whitespace-pre-wrap.break-all
           (-> (:input block) js/JSON.parse yaml/dump)])]
       (when result
         [:div.ml-4
          [ToolResultBlock {:block result}]])])

    "tool_result"
    [ToolResultBlock {:block block}]

    [:div.m-2.p-2.rounded.bg-notice-background.text-white
     [:div.font-semibold (str "Unknown: " (:type block))]]))

(defn AssistantMessage [{:keys [message tool-results]}]
  (let [yaml-text (-> (:rawMessage message) js/JSON.parse yaml/dump)
        content-blocks (get-in message [:message :content])]
    [:li
     [:details.rounded.bg-background-layer-2.border-l-4.border-transparent
      [:summary.p-2.cursor-pointer [:code (str "Assistant: " (:messageId message))]]
      (for [[idx block] (map-indexed vector content-blocks)]
        ^{:key idx} [ContentBlock {:block block :tool-results tool-results}])
      [:details.m-2.p-2.rounded.bg-background-layer-1
       [:summary.cursor-pointer "Raw"]
       [:div.relative.group
        [:div.absolute.top-1.right-1.flex.gap-1
         [CopyButton {:text yaml-text :label "Copy"}]
         [CopyButton {:text (:rawMessage message) :label "Copy Raw"}]]
        [:pre.p-2.whitespace-pre-wrap.break-all yaml-text]]]]]))

(defn UserContentBlock [{:keys [block]}]
  (case (:type block)
    "text"
    [:div.p-2.whitespace-pre-wrap.break-all (:text block)]

    "tool_result"
    [ToolResultBlock {:block block}]

    [:div.m-2.p-2.rounded.bg-notice-background.text-white
     [:div.font-semibold (str "Unknown: " (:type block))]]))

(defn UserMessage [{:keys [message displayed-tool-use-ids]}]
  (let [yaml-text (-> (:rawMessage message) js/JSON.parse yaml/dump)
        content-blocks (get-in message [:message :content])
        tool-result-ids (->> content-blocks
                             (filter #(= (:type %) "tool_result"))
                             (map :tool_use_id)
                             set)
        all-displayed? (and (seq tool-result-ids)
                            (every? #(contains? displayed-tool-use-ids %) tool-result-ids))]
    [:li
     [:details.rounded.bg-background-layer-2.border-l-4.border-accent-background
      {:class (when all-displayed? "opacity-50")}
      [:summary.p-2.cursor-pointer [:code (str "User: " (:messageId message))]]
      (for [[idx block] (map-indexed vector content-blocks)]
        ^{:key idx} [UserContentBlock {:block block}])
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

(defn FileHistorySnapshotMessage [{:keys [message]}]
  (let [yaml-text (-> (:rawMessage message) js/JSON.parse yaml/dump)
        snapshot (:snapshot message)
        tracked-file-backups (-> (:trackedFileBackups snapshot) js/JSON.parse js/Object.keys js->clj)]
    [:li {:key (:id message)}
     [:details.rounded.bg-background-layer-2.border-l-4.border-transparent.opacity-50
      [:summary.p-2.cursor-pointer
       [:code (str "FileHistorySnapshot: " (:messageId message)
                   (when (:isSnapshotUpdate message) " (update)"))]]
      [:div.m-2.p-2.rounded.bg-background-layer-1
       [:div.font-semibold "Tracked Files"]
       [:ul.ml-4.list-disc
        (for [path tracked-file-backups]
          ^{:key path} [:li.text-sm path])]]
      [:details.m-2.p-2.rounded.bg-background-layer-1
       [:summary.cursor-pointer "Raw"]
       [:div.relative.group
        [:div.absolute.top-1.right-1.flex.gap-1
         [CopyButton {:text yaml-text :label "Copy"}]
         [CopyButton {:text (:rawMessage message) :label "Copy Raw"}]]
        [:pre.p-2.whitespace-pre-wrap.break-all yaml-text]]]]]))

(defn QueueOperationMessage [{:keys [message]}]
  (let [yaml-text (-> (:rawMessage message) js/JSON.parse yaml/dump)]
    [:li {:key (:id message)}
     [:details.rounded.bg-background-layer-2.border-l-4.border-transparent.opacity-50
      [:summary.p-2.cursor-pointer
       [:code (str "QueueOperation: " (:operation message))]]
      [:div.m-2.p-2.rounded.bg-background-layer-1
       [:div.text-sm [:span.font-semibold "Content: "] (:content message)]
       [:div.text-xs.opacity-70 (str "Session: " (:queueSessionId message))]
       [:div.text-xs.opacity-70 (str "Timestamp: " (:timestamp message))]]
      [:details.m-2.p-2.rounded.bg-background-layer-1
       [:summary.cursor-pointer "Raw"]
       [:div.relative.group
        [:div.absolute.top-1.right-1.flex.gap-1
         [CopyButton {:text yaml-text :label "Copy"}]
         [CopyButton {:text (:rawMessage message) :label "Copy Raw"}]]
        [:pre.p-2.whitespace-pre-wrap.break-all yaml-text]]]]]))

(defn SystemMessage [{:keys [message]}]
  (let [yaml-text (-> (:rawMessage message) js/JSON.parse yaml/dump)
        compact-metadata (:compactMetadata message)]
    [:li {:key (:id message)}
     [:details.rounded.bg-background-layer-2.border-l-4.border-informative-background.opacity-50
      [:summary.p-2.cursor-pointer
       [:code (str "System: " (:subtype message))]]
      [:div.m-2.p-2.rounded.bg-background-layer-1
       [:div.text-sm [:span.font-semibold "Content: "] (:systemContent message)]
       [:div.text-xs.opacity-70 (str "Level: " (:level message))]
       [:div.text-xs.opacity-70 (str "Timestamp: " (:timestamp message))]
       (when compact-metadata
         [:<>
          [:div.text-xs.opacity-70 (str "Trigger: " (:trigger compact-metadata))]
          [:div.text-xs.opacity-70 (str "Pre-tokens: " (:preTokens compact-metadata))]])]
      [:details.m-2.p-2.rounded.bg-background-layer-1
       [:summary.cursor-pointer "Raw"]
       [:div.relative.group
        [:div.absolute.top-1.right-1.flex.gap-1
         [CopyButton {:text yaml-text :label "Copy"}]
         [CopyButton {:text (:rawMessage message) :label "Copy Raw"}]]
        [:pre.p-2.whitespace-pre-wrap.break-all yaml-text]]]]]))

(defn SummaryMessage [{:keys [message]}]
  (let [yaml-text (-> (:rawMessage message) js/JSON.parse yaml/dump)]
    [:li {:key (:id message)}
     [:details.rounded.bg-background-layer-2.border-l-4.border-positive-background.opacity-50
      [:summary.p-2.cursor-pointer
       [:code "Summary"]]
      [:div.m-2.p-2.rounded.bg-background-layer-1
       [:div.text-sm [:span.font-semibold "Summary: "] (:summary message)]
       [:div.text-xs.opacity-70 (str "Leaf UUID: " (:leafUuid message))]]
      [:details.m-2.p-2.rounded.bg-background-layer-1
       [:summary.cursor-pointer "Raw"]
       [:div.relative.group
        [:div.absolute.top-1.right-1.flex.gap-1
         [CopyButton {:text yaml-text :label "Copy"}]
         [CopyButton {:text (:rawMessage message) :label "Copy Raw"}]]
        [:pre.p-2.whitespace-pre-wrap.break-all yaml-text]]]]]))

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
                       (let [^js node (.-node edge)
                             assistant-msg (.-assistantMessage node)
                             user-msg (.-userMessage node)
                             snapshot (.-snapshot node)]
                         {:__typename (.-__typename node)
                          :id (.-id node)
                          :messageId (.-messageId node)
                          :rawMessage (.-rawMessage node)
                          :isSnapshotUpdate (.-isSnapshotUpdate node)
                          :snapshot (when snapshot
                                      {:messageId (.-messageId snapshot)
                                       :trackedFileBackups (.-trackedFileBackups snapshot)})
                          :operation (.-operation node)
                          :timestamp (.-timestamp node)
                          :content (.-content node)
                          :queueSessionId (.-queueSessionId node)
                          :subtype (.-subtype node)
                          :systemContent (.-systemContent node)
                          :isMeta (.-isMeta node)
                          :level (.-level node)
                          :compactMetadata (when-let [cm (.-compactMetadata node)]
                                             {:trigger (.-trigger cm)
                                              :preTokens (.-preTokens cm)})
                          :summary (.-summary node)
                          :leafUuid (.-leafUuid node)
                          :message (cond
                                     assistant-msg
                                     {:content (mapv (fn [^js block]
                                                       {:type (.-type block)
                                                        :text (.-text block)
                                                        :thinking (.-thinking block)
                                                        :id (.-id block)
                                                        :name (.-name block)
                                                        :input (.-input block)
                                                        :tool_use_id (.-tool_use_id block)
                                                        :content (.-content block)})
                                                     (.-content assistant-msg))}
                                     user-msg
                                     {:content (mapv (fn [^js block]
                                                       {:type (.-type block)
                                                        :text (.-text block)
                                                        :tool_use_id (.-tool_use_id block)
                                                        :content (.-content block)})
                                                     (.-content user-msg))})}))
            tool-results (->> messages
                              (mapcat #(get-in % [:message :content]))
                              (filter #(= (:type %) "tool_result"))
                              (reduce (fn [m block] (assoc m (:tool_use_id block) block)) {}))
            displayed-tool-use-ids (->> messages
                                        (filter #(= (:__typename %) "AssistantMessage"))
                                        (mapcat #(get-in % [:message :content]))
                                        (filter #(= (:type %) "tool_use"))
                                        (map :id)
                                        (filter #(contains? tool-results %))
                                        set)]
        (if (empty? messages)
          [:p.text-neutral-subdued-content "No messages"]
          (into [:ul.space-y-2]
                (map-indexed
                 (fn [idx message]
                   ^{:key idx}
                   [(case (:__typename message)
                      "AssistantMessage" AssistantMessage
                      "UserMessage" UserMessage
                      "UnknownMessage" UnknownMessage
                      "BrokenMessage" BrokenMessage
                      "FileHistorySnapshotMessage" FileHistorySnapshotMessage
                      "QueueOperationMessage" QueueOperationMessage
                      "SystemMessage" SystemMessage
                      "SummaryMessage" SummaryMessage
                      :div)
                    (case (:__typename message)
                      "AssistantMessage" {:message message :tool-results tool-results}
                      "UserMessage" {:message message :displayed-tool-use-ids displayed-tool-use-ids}
                      {:message message})])
                 messages)))))))

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
