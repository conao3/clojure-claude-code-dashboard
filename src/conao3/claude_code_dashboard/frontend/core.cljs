(ns conao3.claude-code-dashboard.frontend.core
  (:require
   ["@apollo/client" :as apollo]
   ["@apollo/client/react" :as apollo.react]
   ["js-yaml" :as yaml]
   ["lucide-react" :as lucide]
   ["react-aria-components" :as rac]
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

(defonce sidebar-collapsed (r/atom false))
(defonce selected-project-id (r/atom nil))
(defonce selected-session-id (r/atom nil))
(defonce session-search (r/atom ""))

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

(defn project-basename [name]
  (last (str/split name #"/")))

(defn NavItem [{:keys [icon label active collapsed on-click badge]}]
  [:> rac/Button
   {:className (str "flex items-center gap-3 w-full rounded-md transition-all outline-none "
                    (if collapsed "p-3 justify-center" "px-3 py-2.5 justify-start")
                    (if active " bg-accent-background/20 text-accent-content" " text-neutral-subdued-content hover:text-neutral-content"))
    :onPress on-click}
   [:> icon {:size 18}]
   (when-not collapsed
     [:<>
      [:span.flex-1.text-left.text-sm label]
      (when badge
        [:span.bg-accent-background.text-white.text-xs.px-1.5.py-0.5.rounded-full.min-w-5.text-center badge])])])

(defn ProjectItem [{:keys [project active collapsed on-click]}]
  (let [session-count (count (:sessions project))]
    [:> rac/Button
     {:className (str "flex items-center gap-2 w-full rounded transition-all outline-none "
                      (if collapsed "p-2 justify-center" "px-3 py-2 justify-start")
                      (if active " bg-accent-background/15 text-neutral-content" " text-neutral-subdued-content hover:text-neutral-content"))
      :onPress on-click
      :isDisabled (zero? session-count)}
     [:> lucide/GitBranch {:size 14 :className "text-neutral-subdued-content flex-shrink-0"}]
     (when-not collapsed
       [:span.text-sm.truncate.flex-1.text-left (project-basename (:name project))])
     (when (and (not collapsed) (pos? session-count))
       [:span.text-xs.text-neutral-subdued-content session-count])]))

(defn Sidebar [{:keys [projects selected-project on-select-project]}]
  (let [collapsed @sidebar-collapsed]
    [:div {:class (str "flex flex-col bg-background-layer-1 border-r border-gray-200 transition-all duration-200 "
                       (if collapsed "w-16" "w-60"))}
     [:div.p-4.border-b.border-gray-200.flex.items-center
      {:class (if collapsed "justify-center" "justify-between")}
      [:div.flex.items-center.gap-2.5
       [:div.w-8.h-8.rounded-lg.flex.items-center.justify-center.flex-shrink-0
        {:class "bg-gradient-to-br from-purple-600 to-blue-600"}
        [:> lucide/Zap {:size 18 :className "text-white"}]]
       (when-not collapsed
         [:span.font-semibold.text-neutral-content "Claude Code"])]
      (when-not collapsed
        [:> rac/Button
         {:className "p-1 text-neutral-subdued-content hover:text-neutral-content outline-none"
          :onPress #(reset! sidebar-collapsed true)}
         [:> lucide/PanelLeftClose {:size 18}]])]

     (when collapsed
       [:> rac/Button
        {:className "p-3 mx-auto my-2 text-neutral-subdued-content hover:text-neutral-content outline-none"
         :onPress #(reset! sidebar-collapsed false)}
        [:> lucide/PanelLeft {:size 18}]])

     [:div.p-2.flex.flex-col.gap-1
      [NavItem {:icon lucide/Home :label "Dashboard" :active false :collapsed collapsed :on-click #()}]
      [NavItem {:icon lucide/Folder :label "Projects" :active true :collapsed collapsed :on-click #() :badge (str (count projects))}]
      [NavItem {:icon lucide/History :label "Recent" :active false :collapsed collapsed :on-click #()}]]

     (when-not collapsed
       [:div.px-2.mt-2
        [:div.text-xs.font-medium.text-neutral-subdued-content.uppercase.tracking-wide.px-3.py-2
         "All Projects"]])

     [:div.flex-1.overflow-y-auto.p-2.flex.flex-col.gap-0.5
      (for [project projects]
        ^{:key (:id project)}
        [ProjectItem {:project project
                      :active (= (:id project) (:id selected-project))
                      :collapsed collapsed
                      :on-click #(on-select-project project)}])]

     [:div.p-2.border-t.border-gray-200
      [NavItem {:icon lucide/Settings :label "Settings" :active false :collapsed collapsed :on-click #()}]]]))

(defn format-date [date-str]
  (when date-str
    (let [date (js/Date. date-str)]
      (str (.toLocaleDateString date "ja-JP") " " (.toLocaleTimeString date "ja-JP" #js {:hour "2-digit" :minute "2-digit"})))))

(defn SessionItem [{:keys [session active on-click]}]
  [:> rac/Button
   {:className (str "w-full text-left p-3 border-b border-gray-200 outline-none transition-all "
                    (if active
                      "bg-accent-background/10 border-l-3 border-l-accent-background"
                      "border-l-3 border-l-transparent hover:bg-background-layer-1"))
    :onPress on-click}
   [:div.flex.items-center.gap-2.mb-1
    [:span.w-2.h-2.rounded-full.bg-positive-visual.flex-shrink-0]
    [:span.text-sm.font-medium.text-neutral-content.truncate.flex-1
     (:sessionId session)]]
   [:div.text-xs.text-neutral-subdued-content
    (format-date (:createdAt session))]])

(defn SessionsPanel [{:keys [project sessions on-select-session selected-session]}]
  [:div.w-72.bg-background-base.border-r.border-gray-200.flex.flex-col
   [:div.p-4.border-b.border-gray-200.flex.items-center.justify-between
    [:h2.text-base.font-semibold.text-neutral-content.truncate
     (if project (project-basename (:name project)) "Sessions")]
    [:> rac/Button
     {:className "p-1 text-neutral-subdued-content hover:text-neutral-content outline-none"}
     [:> lucide/MoreHorizontal {:size 18}]]]

   [:div.p-3
    [:div.relative
     [:> lucide/Search {:size 14 :className "absolute left-2.5 top-1/2 -translate-y-1/2 text-neutral-subdued-content"}]
     [:input
      {:type "text"
       :placeholder "Search sessions..."
       :value @session-search
       :on-change #(reset! session-search (-> % .-target .-value))
       :class "w-full bg-background-layer-1 border border-gray-300 rounded-md py-2 pl-8 pr-3 text-sm text-neutral-content outline-none focus:border-accent-background"}]]]

   [:div.flex-1.overflow-y-auto
    (if (empty? sessions)
      [:p.p-4.text-neutral-subdued-content.text-sm "Select a project"]
      (let [search-term (str/lower-case @session-search)
            filtered-sessions (if (str/blank? search-term)
                                sessions
                                (filter #(str/includes? (str/lower-case (:sessionId %)) search-term) sessions))]
        (for [session filtered-sessions]
          ^{:key (:id session)}
          [SessionItem {:session session
                        :active (= (:id session) (:id selected-session))
                        :on-click #(on-select-session session)}])))]])

(defn CopyButton []
  (let [copied? (r/atom false)]
    (fn [{:keys [text label class]}]
      [:> rac/Button
       {:className (str "px-2 py-1 rounded bg-background-layer-1 opacity-0 group-hover:opacity-70 hover:opacity-100 pressed:opacity-100 flex items-center gap-1 text-xs " class)
        :onPress (fn []
                   (-> js/navigator .-clipboard (.writeText text))
                   (reset! copied? true)
                   (js/setTimeout #(reset! copied? false) 1000))}
       (if @copied?
         [:<> [:> lucide/Check {:size 12}] "Copied!"]
         [:<> [:> lucide/Copy {:size 12}] (or label "Copy")])])))

(defn ToolResultBlock [{:keys [block]}]
  [:div.mt-2.p-3.rounded-lg.bg-background-layer-1.border.border-gray-200
   [:div.text-xs.font-medium.text-neutral-subdued-content.mb-1 "Tool Result"]
   [:div.text-xs.text-neutral-subdued-content.mb-2 (str "ID: " (:tool_use_id block))]
   (when (:content block)
     [:pre.text-xs.whitespace-pre-wrap.break-all.text-neutral-content (:content block)])])

(defn ContentBlock [{:keys [block tool-results]}]
  (case (:type block)
    "text"
    [:p.text-sm.leading-relaxed.text-neutral-content (:text block)]

    "thinking"
    [:div {:class "mt-3 p-3 rounded-lg bg-yellow-900/10 border border-yellow-700/20"}
     [:div.flex.items-center.gap-1.5.text-xs.font-medium.text-yellow-600.mb-2
      [:> lucide/Brain {:size 12}]
      "Thinking"]
     [:pre.text-xs.whitespace-pre-wrap.break-all.text-neutral-subdued-content (:thinking block)]]

    "tool_use"
    (let [result (get tool-results (:id block))]
      [:div.mt-3
       [:div {:class "p-3 rounded-lg bg-cyan-900/10 border border-cyan-700/20"}
        [:div.flex.items-center.gap-1.5.text-xs.font-medium.text-cyan-500.mb-2
         [:> lucide/Terminal {:size 12}]
         (:name block)]
        [:div.text-xs.text-neutral-subdued-content.mb-2 (str "ID: " (:id block))]
        (when (:input block)
          [:pre.text-xs.whitespace-pre-wrap.break-all.font-mono.text-neutral-content.bg-background-layer-1.p-2.rounded
           (try
             (-> (:input block) js/JSON.parse yaml/dump)
             (catch :default _ (:input block)))])]
       (when result
         [ToolResultBlock {:block result}])])

    "tool_result"
    [ToolResultBlock {:block block}]

    [:div.mt-3.p-3.rounded-lg.bg-notice-background.text-white
     [:div.text-xs.font-medium (str "Unknown: " (:type block))]]))

(defn MessageBubble [{:keys [role icon icon-class children time tool-count thinking?]}]
  [:div {:class (str "mb-4 " (when (= role :user) "pl-12"))}
   [:div {:class (str "rounded-xl p-4 border "
                      (if (= role :user)
                        "bg-accent-background-subdued border-accent-background"
                        "bg-background-layer-1 border-gray-200"))}
    [:div.flex.items-center.gap-2.mb-2
     (when icon
       [:> icon {:size 14 :className icon-class}])
     [:span {:class (str "text-xs font-medium " (if (= role :user) "text-accent-content" "text-purple-400"))}
      (if (= role :user) "You" "Claude")]
     [:span.text-xs.text-neutral-subdued-content time]
     (when (or tool-count thinking?)
       [:div.ml-auto.flex.gap-3
        (when thinking?
          [:span.text-xs.text-yellow-600.flex.items-center.gap-1
           [:> lucide/Brain {:size 12}] "Thinking"])
        (when tool-count
          [:span.text-xs.text-cyan-500.flex.items-center.gap-1
           [:> lucide/Terminal {:size 12}] (str tool-count " tools")])])]
    children]])

(defn safe-yaml-dump [raw-message]
  (try
    (-> raw-message js/JSON.parse yaml/dump)
    (catch :default _
      raw-message)))

(defn AssistantMessage [{:keys [message tool-results]}]
  (let [yaml-text (safe-yaml-dump (:rawMessage message))
        content-blocks (get-in message [:message :content])
        tool-count (->> content-blocks (filter #(= (:type %) "tool_use")) count)
        has-thinking? (some #(= (:type %) "thinking") content-blocks)]
    [MessageBubble {:role :assistant
                    :icon lucide/Cpu
                    :icon-class "text-purple-400"
                    :tool-count (when (pos? tool-count) tool-count)
                    :thinking? has-thinking?}
     [:<>
      (for [[idx block] (map-indexed vector content-blocks)]
        ^{:key idx} [ContentBlock {:block block :tool-results tool-results}])
      [:details.mt-3.pt-3.border-t.border-gray-200
       [:summary.text-xs.text-neutral-subdued-content.cursor-pointer "Raw"]
       [:div.relative.group.mt-2
        [:div.absolute.top-1.right-1.flex.gap-1
         [CopyButton {:text yaml-text :label "Copy"}]
         [CopyButton {:text (:rawMessage message) :label "Raw"}]]
        [:pre.text-xs.whitespace-pre-wrap.break-all.bg-background-base.p-2.rounded.max-h-48.overflow-auto yaml-text]]]]]))

(defn UserMessage [{:keys [message displayed-tool-use-ids]}]
  (js/console.log "UserMessage component message:" (clj->js message))
  (let [yaml-text (safe-yaml-dump (:rawMessage message))
        content-blocks (get-in message [:message :content])
        _ (js/console.log "UserMessage content-blocks:" (clj->js content-blocks))
        tool-result-ids (->> content-blocks
                             (filter #(= (:type %) "tool_result"))
                             (map :tool_use_id)
                             set)
        all-displayed? (and (seq tool-result-ids)
                            (every? #(contains? displayed-tool-use-ids %) tool-result-ids))]
    [:div {:class (when all-displayed? "opacity-50")}
     [MessageBubble {:role :user
                     :icon lucide/User
                     :icon-class "text-accent-content"}
      [:<>
       (for [[idx block] (map-indexed vector content-blocks)]
         ^{:key idx}
         (case (:type block)
           "text" [:p.text-sm.leading-relaxed.text-neutral-content (:text block)]
           "tool_result" [ToolResultBlock {:block block}]
           [:div.text-xs.text-notice-content (str "Unknown: " (:type block))]))
       [:details.mt-3.pt-3.border-t.border-gray-200
        [:summary.text-xs.text-neutral-subdued-content.cursor-pointer "Raw"]
        [:div.relative.group.mt-2
         [:div.absolute.top-1.right-1.flex.gap-1
          [CopyButton {:text yaml-text :label "Copy"}]
          [CopyButton {:text (:rawMessage message) :label "Raw"}]]
         [:pre.text-xs.whitespace-pre-wrap.break-all.bg-background-base.p-2.rounded.max-h-48.overflow-auto yaml-text]]]]]]))

(defn SystemMessageItem [{:keys [message]}]
  (let [yaml-text (safe-yaml-dump (:rawMessage message))]
    [:div.mb-3.opacity-60
     [:div {:class "rounded-lg p-3 bg-informative-background-subdued border border-informative-background"}
      [:div.flex.items-center.gap-2.text-xs.text-informative-content
       [:> lucide/Settings {:size 12}]
       [:span.font-medium (str "System: " (:subtype message))]
       [:span.text-neutral-subdued-content (:timestamp message)]]
      [:details.mt-2
       [:summary.text-xs.text-neutral-subdued-content.cursor-pointer "Raw"]
       [:div.mt-2.text-xs.text-neutral-subdued-content
        [:div [:span.font-medium "Content: "] (:systemContent message)]
        [:div [:span.font-medium "Level: "] (:level message)]
        (when-let [cm (:compactMetadata message)]
          [:<>
           [:div [:span.font-medium "Trigger: "] (:trigger cm)]
           [:div [:span.font-medium "Pre-tokens: "] (:preTokens cm)]])]
       [:div.relative.group.mt-2
        [:div.absolute.top-1.right-1
         [CopyButton {:text yaml-text :label "Copy"}]]
        [:pre.text-xs.whitespace-pre-wrap.break-all.bg-background-base.p-2.rounded.max-h-32.overflow-auto yaml-text]]]]]))

(defn SummaryMessageItem [{:keys [message]}]
  (let [yaml-text (safe-yaml-dump (:rawMessage message))]
    [:div.mb-3.opacity-60
     [:div {:class "rounded-lg p-3 bg-positive-background-subdued border border-positive-background"}
      [:div.flex.items-center.gap-2.text-xs.text-positive-content
       [:> lucide/FileText {:size 12}]
       [:span.font-medium "Summary"]]
      [:p.mt-1.text-sm.text-neutral-content (:summary message)]
      [:details.mt-2
       [:summary.text-xs.text-neutral-subdued-content.cursor-pointer "Raw"]
       [:div.relative.group.mt-2
        [:div.absolute.top-1.right-1
         [CopyButton {:text yaml-text :label "Copy"}]]
        [:pre.text-xs.whitespace-pre-wrap.break-all.bg-background-base.p-2.rounded.max-h-32.overflow-auto yaml-text]]]]]))

(defn FileHistorySnapshotMessage [{:keys [message]}]
  (let [yaml-text (safe-yaml-dump (:rawMessage message))
        snapshot (:snapshot message)
        tracked-file-backups (-> (:trackedFileBackups snapshot) js/JSON.parse js/Object.keys js->clj)]
    [:div.mb-3.opacity-50
     [:div.rounded-lg.p-3.bg-background-layer-1.border.border-gray-200
      [:div.flex.items-center.gap-2.text-xs.text-neutral-subdued-content
       [:> lucide/History {:size 12}]
       [:span "FileHistorySnapshot"]
       (when (:isSnapshotUpdate message)
         [:span.text-xs.bg-gray-600.text-white.px-1.rounded "update"])]
      [:details.mt-2
       [:summary.text-xs.text-neutral-subdued-content.cursor-pointer
        (str (count tracked-file-backups) " tracked files")]
       [:ul.mt-1.text-xs.text-neutral-subdued-content.ml-4.list-disc
        (for [path tracked-file-backups]
          ^{:key path} [:li.truncate path])]
       [:div.relative.group.mt-2
        [:div.absolute.top-1.right-1
         [CopyButton {:text yaml-text :label "Copy"}]]
        [:pre.text-xs.whitespace-pre-wrap.break-all.bg-background-base.p-2.rounded.max-h-32.overflow-auto yaml-text]]]]]))

(defn QueueOperationMessage [{:keys [message]}]
  (let [yaml-text (safe-yaml-dump (:rawMessage message))]
    [:div.mb-3.opacity-50
     [:div.rounded-lg.p-3.bg-background-layer-1.border.border-gray-200
      [:div.flex.items-center.gap-2.text-xs.text-neutral-subdued-content
       [:> lucide/ListOrdered {:size 12}]
       [:span (str "Queue: " (:operation message))]]
      [:details.mt-2
       [:summary.text-xs.text-neutral-subdued-content.cursor-pointer "Raw"]
       [:div.mt-1.text-xs.text-neutral-subdued-content
        (when (:content message) [:div [:span.font-medium "Content: "] (:content message)])
        [:div [:span.font-medium "Session: "] (:queueSessionId message)]
        [:div [:span.font-medium "Time: "] (:timestamp message)]]
       [:div.relative.group.mt-2
        [:div.absolute.top-1.right-1
         [CopyButton {:text yaml-text :label "Copy"}]]
        [:pre.text-xs.whitespace-pre-wrap.break-all.bg-background-base.p-2.rounded.max-h-32.overflow-auto yaml-text]]]]]))

(defn UnknownMessage [{:keys [message]}]
  (let [yaml-text (safe-yaml-dump (:rawMessage message))]
    [:div.mb-3
     [:div.rounded-lg.p-3.bg-notice-background.text-white
      [:div.flex.items-center.gap-2.text-xs
       [:> lucide/HelpCircle {:size 12}]
       [:span (str "Unknown: " (:messageId message))]]
      [:details.mt-2
       [:summary.text-xs.cursor-pointer "Raw"]
       [:pre {:class "text-xs whitespace-pre-wrap break-all bg-black/20 p-2 rounded mt-1 max-h-32 overflow-auto"} yaml-text]]]]))

(defn BrokenMessage [{:keys [message]}]
  [:div.mb-3
   [:div.rounded-lg.p-3.bg-negative-background.text-white
    [:div.flex.items-center.gap-2.text-xs
     [:> lucide/AlertTriangle {:size 12}]
     [:span (str "Broken: " (:messageId message))]]
    [:details.mt-2
     [:summary.text-xs.cursor-pointer "Raw"]
     [:pre {:class "text-xs whitespace-pre-wrap break-all bg-black/20 p-2 rounded mt-1 max-h-32 overflow-auto"}
      (:rawMessage message)]]]])

(defn safe-render-message [{:keys [message tool-results displayed-tool-use-ids]}]
  (try
    (case (:__typename message)
      "AssistantMessage" [AssistantMessage {:message message :tool-results tool-results}]
      "UserMessage" [UserMessage {:message message :displayed-tool-use-ids displayed-tool-use-ids}]
      "UnknownMessage" [UnknownMessage {:message message}]
      "BrokenMessage" [BrokenMessage {:message message}]
      "FileHistorySnapshotMessage" [FileHistorySnapshotMessage {:message message}]
      "QueueOperationMessage" [QueueOperationMessage {:message message}]
      "SystemMessage" [SystemMessageItem {:message message}]
      "SummaryMessage" [SummaryMessageItem {:message message}]
      nil)
    (catch :default e
      [:div.mb-3
       [:div.rounded-lg.p-3.bg-negative-background.text-white
        [:div.flex.items-center.gap-2.text-xs
         [:> lucide/AlertTriangle {:size 12}]
         [:span (str "Render error: " (.-message e))]]]])))

(defn MessageList []
  (let [session-id @selected-session-id
        result (apollo.react/useQuery session-messages-query #js {:variables #js {:id session-id}
                                                                   :skip (nil? session-id)})
        loading (.-loading result)
        error (.-error result)
        data (.-data result)]
    (cond
      (nil? session-id) [:div.flex-1.flex.items-center.justify-center.text-neutral-subdued-content "Select a session to view messages"]
      loading [:div.flex-1.flex.items-center.justify-center.text-neutral-subdued-content "Loading messages..."]
      error [:div.flex-1.flex.items-center.justify-center.text-negative-content (str "Error: " (.-message error))]
      :else
      (let [messages (vec (for [edge (-> data .-node .-messages .-edges)]
                            (let [^js node (.-node edge)
                                  typename (.-__typename node)
                                  snapshot (.-snapshot node)]
                              {:__typename typename
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
                               :message (case typename
                                          "AssistantMessage"
                                          (when-let [msg (.-assistantMessage node)]
                                            {:content (mapv (fn [^js block]
                                                              {:type (.-type block)
                                                               :text (.-text block)
                                                               :thinking (.-thinking block)
                                                               :id (.-id block)
                                                               :name (.-name block)
                                                               :input (.-input block)
                                                               :tool_use_id (.-tool_use_id block)
                                                               :content (.-content block)})
                                                            (.-content msg))})
                                          "UserMessage"
                                          (when-let [msg (.-userMessage node)]
                                            (js/console.log "UserMessage msg:" msg)
                                            (js/console.log "msg.content:" (.-content msg))
                                            {:content (mapv (fn [^js block]
                                                              {:type (.-type block)
                                                               :text (.-text block)
                                                               :tool_use_id (.-tool_use_id block)
                                                               :content (.-content block)})
                                                            (.-content msg))})
                                          nil)})))
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
                                        set)
            message-count (count messages)
            tool-call-count (->> messages
                                 (filter #(= (:__typename %) "AssistantMessage"))
                                 (mapcat #(get-in % [:message :content]))
                                 (filter #(= (:type %) "tool_use"))
                                 count)]
        [:div.flex-1.flex.flex-col.min-h-0
         [:div.flex.items-center.gap-4.text-sm.text-neutral-subdued-content.mb-4.shrink-0
          [:span (str message-count " messages")]
          [:span "•"]
          [:span (str tool-call-count " tool calls")]]
         [:div.flex-1.overflow-y-auto.min-h-0.pr-2
          (if (empty? messages)
            [:div.text-neutral-subdued-content "No messages"]
            (for [[idx message] (map-indexed vector messages)]
              ^{:key idx}
              [safe-render-message {:message message
                                    :tool-results tool-results
                                    :displayed-tool-use-ids displayed-tool-use-ids}]))]]))))

(defn MessagesPanel [{:keys [session]}]
  [:div.flex-1.flex.flex-col.bg-background-base.min-h-0.overflow-hidden
   [:div.p-4.border-b.border-gray-200.shrink-0
    [:h2.text-lg.font-semibold.text-neutral-content.truncate
     (or (:sessionId session) "Messages")]
    (when session
      [:div.text-sm.text-neutral-subdued-content.mt-1
       (format-date (:createdAt session))])]
   [:div.flex-1.flex.flex-col.min-h-0.p-5
    [:f> MessageList]]])

(defn MainContent []
  (let [result (apollo.react/useQuery projects-query)
        loading (.-loading result)
        error (.-error result)
        data (.-data result)]
    (cond
      loading [:div.flex-1.flex.items-center.justify-center.text-neutral-subdued-content "Loading..."]
      error [:div.flex-1.flex.items-center.justify-center.text-negative-content (str "Error: " (.-message error))]
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
            selected-session (when selected-project
                               (some #(when (= (:id %) @selected-session-id) %) (:sessions selected-project)))]
        [:div.flex.h-full
         [Sidebar {:projects projects
                   :selected-project selected-project
                   :on-select-project (fn [project]
                                        (reset! selected-project-id (:id project))
                                        (reset! selected-session-id nil))}]
         [SessionsPanel {:project selected-project
                         :sessions (or (:sessions selected-project) [])
                         :selected-session selected-session
                         :on-select-session #(reset! selected-session-id (:id %))}]
         [MessagesPanel {:session selected-session}]]))))

(defn App []
  [:> apollo.react/ApolloProvider {:client apollo-client}
   [:div.h-screen.flex.flex-col.bg-background-base.text-neutral-content.text-sm
    [:f> MainContent]]])

(defonce root (-> js/document (.getElementById "app") reagent.dom.client/create-root))

(defn ^:dev/after-load start []
  (reagent.dom.client/render root [App]))
