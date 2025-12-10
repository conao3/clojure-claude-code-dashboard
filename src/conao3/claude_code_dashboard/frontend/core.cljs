(ns conao3.claude-code-dashboard.frontend.core
  (:require
   ["@apollo/client" :as apollo]
   ["@apollo/client/react" :as apollo.react]
   ["js-yaml" :as yaml]
   ["lucide-react" :as lucide]
   ["react" :as react]
   ["react-aria-components" :as rac]
   ["react-markdown" :as ReactMarkdown]
   ["react-stately" :as stately]
   ["remark-gfm" :as remarkGfm]
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
  (apollo/gql "query Projects($first: Int, $after: String) {
    projects(first: $first, after: $after) {
      pageInfo {
        hasNextPage
        endCursor
      }
      edges {
        node {
          id
          name
          projectId
          sessions(first: 10) {
            edges {
              node {
                id
              }
            }
          }
        }
      }
    }
  }"))

(def project-sessions-query
  (apollo/gql "query ProjectSessions($id: ID!, $first: Int, $after: String) {
    node(id: $id) {
      ... on Project {
        id
        projectId
        name
        sessions(first: $first, after: $after) {
          pageInfo {
            hasNextPage
            endCursor
          }
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
  }"))

(defonce sidebar-collapsed (r/atom false))
(defonce selected-project-id (r/atom nil))
(defonce selected-session-id (r/atom nil))
(defonce session-search (r/atom ""))

(defn- parse-url-path []
  (let [path (.-pathname js/window.location)
        match (re-matches #"/projects/([^/]+)(?:/sessions/([^/]+))?" path)]
    (when match
      {:project-id (second match)
       :session-id (nth match 2 nil)})))

(defn- update-url! [project-id session-id]
  (let [path (cond
               (and project-id session-id) (str "/projects/" project-id "/sessions/" session-id)
               project-id (str "/projects/" project-id)
               :else "/")]
    (.pushState js/window.history nil "" path)))

(def session-messages-query
  (apollo/gql "query SessionMessages($id: ID!, $first: Int, $after: String) {
    node(id: $id) {
      ... on Session {
        id
        sessionId
        messages(first: $first, after: $after) {
          pageInfo {
            hasNextPage
            endCursor
          }
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
  [:> rac/Button
   {:className (str "flex items-center gap-2 w-full rounded transition-all outline-none "
                    (if collapsed "p-2 justify-center" "px-3 py-2 justify-start")
                    (if active " bg-accent-background/15 text-neutral-content" " text-neutral-subdued-content hover:text-neutral-content"))
    :onPress on-click
    :isDisabled (not (:hasSessions project))}
   [:> lucide/GitBranch {:size 14 :className "text-neutral-subdued-content flex-shrink-0"}]
   (when-not collapsed
     [:span.text-sm.truncate.flex-1.text-left (project-basename (:name project))])])

(defn- parse-project-node [^js node]
  {:id (.-id node)
   :name (.-name node)
   :projectId (.-projectId node)
   :hasSessions (pos? (count (-> node .-sessions .-edges)))})

(defn ProjectsList [{:keys [on-select-project collapsed]}]
  (let [scroll-container-ref (react/useRef nil)
        current-project-id @selected-project-id
        list (stately/useAsyncList
              #js {:load (fn [^js opts]
                           (let [cursor (.-cursor opts)]
                             (-> (.query apollo-client #js {:query projects-query
                                                            :variables #js {:first 20 :after cursor}})
                                 (.then (fn [^js result]
                                          (let [data (.-data result)
                                                edges (-> data .-projects .-edges)
                                                page-info (-> data .-projects .-pageInfo)
                                                items (mapv #(parse-project-node (.-node %)) edges)]
                                            #js {:items (clj->js items)
                                                 :cursor (when (.-hasNextPage page-info)
                                                           (.-endCursor page-info))}))))))})]
    (cond
      (and (.-isLoading list) (zero? (count (.-items list)))) [:div.p-2.text-neutral-subdued-content.text-sm "Loading..."]
      (.-error list) [:div.p-2.text-negative-content.text-sm "Error"]
      :else
      [:div.flex-1.overflow-y-auto.min-h-0.p-2.flex.flex-col.gap-0.5
       {:ref scroll-container-ref
        :on-scroll (fn [e]
                     (when-let [container (.-current scroll-container-ref)]
                       (let [scroll-top (.-scrollTop container)
                             scroll-height (.-scrollHeight container)
                             client-height (.-clientHeight container)
                             threshold 200]
                         (when (and (not (.-isLoading list))
                                    (> (+ scroll-top client-height threshold) scroll-height))
                           (.loadMore list)))))}
       (for [^js project (.-items list)]
         (let [p {:id (.-id project) :name (.-name project) :projectId (.-projectId project) :hasSessions (.-hasSessions project)}]
           ^{:key (:id p)}
           [ProjectItem {:project p
                         :active (= (:id p) current-project-id)
                         :collapsed collapsed
                         :on-click #(on-select-project p)}]))
       (when (.-isLoading list)
         [:div.flex.items-center.justify-center.py-2.text-neutral-subdued-content
          [:> lucide/Loader2 {:size 14 :className "animate-spin"}]])])))

(defn Sidebar [{:keys [on-select-project]}]
  (let [collapsed @sidebar-collapsed]
    [:div {:class (str "flex flex-col h-full min-h-0 bg-background-layer-1 border-r border-gray-200 transition-all duration-200 "
                       (if collapsed "w-16" "w-60"))}
     [:div.p-4.border-b.border-gray-200.flex.items-center.shrink-0
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

     [:div.p-2.flex.flex-col.gap-1.shrink-0
      [NavItem {:icon lucide/Home :label "Dashboard" :active false :collapsed collapsed :on-click #()}]
      [NavItem {:icon lucide/Folder :label "Projects" :active true :collapsed collapsed :on-click #()}]
      [NavItem {:icon lucide/History :label "Recent" :active false :collapsed collapsed :on-click #()}]]

     (when-not collapsed
       [:div.px-2.mt-2.shrink-0
        [:div.text-xs.font-medium.text-neutral-subdued-content.uppercase.tracking-wide.px-3.py-2
         "All Projects"]])

     [:f> ProjectsList {:on-select-project on-select-project :collapsed collapsed}]

     [:div.p-2.border-t.border-gray-200.shrink-0
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

(defn- parse-session-node [^js node]
  {:id (.-id node)
   :projectId (.-projectId node)
   :sessionId (.-sessionId node)
   :createdAt (.-createdAt node)})

(defn SessionsList [{:keys [project-id on-select-session]}]
  (let [scroll-container-ref (react/useRef nil)
        project-id-ref (react/useRef project-id)
        current-session-id @selected-session-id
        list (stately/useAsyncList
              #js {:load (fn [^js opts]
                           (let [pid (.-current project-id-ref)]
                             (js/console.log "SessionsList load called, pid:" pid)
                             (if (nil? pid)
                               (js/Promise.resolve #js {:items #js []})
                               (let [cursor (.-cursor opts)]
                                 (-> (.query apollo-client #js {:query project-sessions-query
                                                                 :variables #js {:id pid :first 20 :after cursor}})
                                     (.then (fn [^js result]
                                              (let [data (.-data result)
                                                    edges (-> data .-node .-sessions .-edges)
                                                    page-info (-> data .-node .-sessions .-pageInfo)
                                                    items (mapv #(parse-session-node (.-node %)) edges)]
                                                (js/console.log "SessionsList loaded items:" (count items))
                                                #js {:items (clj->js items)
                                                     :cursor (when (.-hasNextPage page-info)
                                                               (.-endCursor page-info))}))))))))})]
    (react/useEffect
     (fn []
       (js/console.log "SessionsList useEffect, project-id:" project-id)
       (set! (.-current project-id-ref) project-id)
       (when project-id (.reload list))
       js/undefined)
     #js [project-id])
    (let [search-term (str/lower-case @session-search)
          sessions (.-items list)
          filtered-sessions (if (str/blank? search-term)
                              sessions
                              (filter #(str/includes? (str/lower-case (.-sessionId %)) search-term) sessions))]
      (cond
        (nil? project-id) [:p.p-4.text-neutral-subdued-content.text-sm "Select a project"]
        (and (.-isLoading list) (zero? (count sessions))) [:div.p-4.text-neutral-subdued-content.text-sm "Loading sessions..."]
        (.-error list) [:div.p-4.text-negative-content.text-sm (str "Error: " (.-error list))]
        :else
        [:div.flex-1.overflow-y-auto
         {:ref scroll-container-ref
          :on-scroll (fn [e]
                       (when-let [container (.-current scroll-container-ref)]
                         (let [scroll-top (.-scrollTop container)
                               scroll-height (.-scrollHeight container)
                               client-height (.-clientHeight container)
                               threshold 200]
                           (when (and (not (.-isLoading list))
                                      (> (+ scroll-top client-height threshold) scroll-height))
                             (.loadMore list)))))}
         (for [^js session filtered-sessions]
           (let [s {:id (.-id session) :projectId (.-projectId session) :sessionId (.-sessionId session) :createdAt (.-createdAt session)}]
             ^{:key (:id s)}
             [SessionItem {:session s
                           :active (= (:id s) current-session-id)
                           :on-click #(on-select-session s)}]))
         (when (.-isLoading list)
           [:div.flex.items-center.justify-center.py-4.text-neutral-subdued-content
            [:> lucide/Loader2 {:size 16 :className "animate-spin mr-2"}]
            "Loading more..."])]))))

(defn SessionsPanel [{:keys [project on-select-session]}]
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
       :id "session-search"
       :name "session-search"
       :placeholder "Search sessions..."
       :value @session-search
       :on-change #(reset! session-search (-> % .-target .-value))
       :class "w-full bg-background-layer-1 border border-gray-300 rounded-md py-2 pl-8 pr-3 text-sm text-neutral-content outline-none focus:border-accent-background"}]]]

   [:f> SessionsList {:project-id (:id project)
                      :on-select-session on-select-session}]])

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

(def markdown-components
  #js {:h1 (fn [props] (r/as-element [:h1.text-xl.font-bold.mt-4.mb-2.text-neutral-content (.-children props)]))
       :h2 (fn [props] (r/as-element [:h2.text-lg.font-bold.mt-3.mb-2.text-neutral-content (.-children props)]))
       :h3 (fn [props] (r/as-element [:h3.text-base.font-bold.mt-2.mb-1.text-neutral-content (.-children props)]))
       :p (fn [props] (r/as-element [:p.mb-2.last:mb-0.text-neutral-content (.-children props)]))
       :ul (fn [props] (r/as-element [:ul.list-disc.list-inside.mb-2.text-neutral-content (.-children props)]))
       :ol (fn [props] (r/as-element [:ol.list-decimal.list-inside.mb-2.text-neutral-content (.-children props)]))
       :li (fn [props] (r/as-element [:li.mb-1 (.-children props)]))
       :code (fn [props]
               (if (.-inline props)
                 (r/as-element [:code.bg-background-layer-1.px-1.py-0.5.rounded.text-sm.font-mono (.-children props)])
                 (r/as-element [:code.font-mono (.-children props)])))
       :pre (fn [props] (r/as-element [:pre.bg-background-layer-1.p-3.rounded-lg.overflow-x-auto.mb-2.text-sm (.-children props)]))
       :blockquote (fn [props] (r/as-element [:blockquote.border-l-4.border-gray-300.pl-4.italic.text-neutral-subdued-content.mb-2 (.-children props)]))
       :a (fn [props] (r/as-element [:a.text-accent-content.underline {:href (.-href props) :target "_blank"} (.-children props)]))
       :table (fn [props] (r/as-element [:table.w-full.border-collapse.mb-2 (.-children props)]))
       :th (fn [props] (r/as-element [:th.border.border-gray-300.px-2.py-1.bg-background-layer-1.text-left.font-medium (.-children props)]))
       :td (fn [props] (r/as-element [:td.border.border-gray-300.px-2.py-1 (.-children props)]))})

(defn Markdown [{:keys [children class]}]
  [:> ReactMarkdown/default
   {:remarkPlugins #js [remarkGfm/default]
    :components markdown-components
    :className class}
   children])

(defn ToolResultBlock [{:keys [block]}]
  [:div.mt-2.p-3.rounded-lg.bg-background-layer-1.border.border-gray-200
   [:div.text-xs.font-medium.text-neutral-subdued-content.mb-1 "Tool Result"]
   [:div.text-xs.text-neutral-subdued-content.mb-2 (str "ID: " (:tool_use_id block))]
   (when (:content block)
     [:pre.text-xs.whitespace-pre-wrap.break-all.text-neutral-content (:content block)])])

(defn ContentBlock [{:keys [block tool-results]}]
  (case (:type block)
    "text"
    [:div.text-sm.leading-relaxed
     [Markdown {:children (:text block)}]]

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

(defn MessageBubble [{:keys [role icon icon-class time tool-count thinking?]} & children]
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
    (into [:<>] children)]])

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
           "text" [:div.text-sm.leading-relaxed [Markdown {:children (:text block)}]]
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

(defn- parse-message-node [^js node]
  (let [typename (.-__typename node)
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
                  {:content (mapv (fn [^js block]
                                    {:type (.-type block)
                                     :text (.-text block)
                                     :tool_use_id (.-tool_use_id block)
                                     :content (.-content block)})
                                  (.-content msg))})
                nil)}))

(defn MessageList []
  (let [session-id @selected-session-id
        scroll-container-ref (react/useRef nil)
        session-id-ref (react/useRef session-id)
        has-next-page-ref (react/useRef false)
        list (stately/useAsyncList
              #js {:load (fn [^js opts]
                           (let [sid (.-current session-id-ref)]
                             (js/console.log "MessageList load called, sid:" sid)
                             (if (nil? sid)
                               (js/Promise.resolve #js {:items #js []})
                               (let [cursor (.-cursor opts)]
                                 (-> (.query apollo-client #js {:query session-messages-query
                                                                 :variables #js {:id sid :first 20 :after cursor}})
                                     (.then (fn [^js result]
                                              (let [data (.-data result)
                                                    edges (-> data .-node .-messages .-edges)
                                                    page-info (-> data .-node .-messages .-pageInfo)
                                                    items (mapv #(parse-message-node (.-node %)) edges)]
                                                (js/console.log "MessageList loaded items:" (count items))
                                                (set! (.-current has-next-page-ref) (.-hasNextPage page-info))
                                                #js {:items (clj->js items)
                                                     :cursor (when (.-hasNextPage page-info)
                                                               (.-endCursor page-info))}))))))))})]
    (react/useEffect
     (fn []
       (js/console.log "MessageList useEffect, session-id:" session-id)
       (set! (.-current session-id-ref) session-id)
       (when session-id (.reload list))
       js/undefined)
     #js [session-id])
    (let [messages (vec (for [^js item (.-items list)]
                          (js->clj item :keywordize-keys true)))
          has-next-page (.-current has-next-page-ref)
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
      (cond
        (nil? session-id) [:div.flex-1.flex.items-center.justify-center.text-neutral-subdued-content "Select a session to view messages"]
        (and (.-isLoading list) (zero? (count messages))) [:div.flex-1.flex.items-center.justify-center.text-neutral-subdued-content "Loading messages..."]
        (.-error list) [:div.flex-1.flex.items-center.justify-center.text-negative-content (str "Error: " (.-error list))]
        :else
        [:div.flex-1.flex.flex-col.min-h-0
         [:div.flex.items-center.gap-4.text-sm.text-neutral-subdued-content.mb-4.shrink-0
          [:span (str message-count " messages" (when has-next-page "+"))]
          [:span "•"]
          [:span (str tool-call-count " tool calls")]]
         [:div.flex-1.overflow-y-auto.min-h-0.pr-2
          {:ref scroll-container-ref
           :on-scroll (fn [e]
                        (when-let [container (.-current scroll-container-ref)]
                          (let [scroll-top (.-scrollTop container)
                                scroll-height (.-scrollHeight container)
                                client-height (.-clientHeight container)
                                threshold 200]
                            (when (and (not (.-isLoading list))
                                       (> (+ scroll-top client-height threshold) scroll-height))
                              (.loadMore list)))))}
          (if (empty? messages)
            [:div.text-neutral-subdued-content "No messages"]
            [:<>
             (for [[idx message] (map-indexed vector messages)]
               ^{:key idx}
               [safe-render-message {:message message
                                     :tool-results tool-results
                                     :displayed-tool-use-ids displayed-tool-use-ids}])
             (when (.-isLoading list)
               [:div.flex.items-center.justify-center.py-4.text-neutral-subdued-content
              [:> lucide/Loader2 {:size 20 :className "animate-spin mr-2"}]
              "Loading more..."])])]]))))

(defn MessagesPanel []
  [:div.flex-1.flex.flex-col.bg-background-base.min-h-0.overflow-hidden
   [:div.p-4.border-b.border-gray-200.shrink-0
    [:h2.text-lg.font-semibold.text-neutral-content.truncate "Messages"]]
   [:div.flex-1.flex.flex-col.min-h-0.p-5
    [:f> MessageList]]])

(defonce url-initialized (atom false))

(defn- init-url! []
  (when-not @url-initialized
    (when-let [{:keys [project-id session-id]} (parse-url-path)]
      (reset! selected-project-id (js/btoa (str "Project:/" project-id)))
      (when session-id
        (reset! selected-session-id (js/btoa (str "Session:" project-id "/" session-id)))))
    (reset! url-initialized true)))

(defn MainContent []
  (init-url!)
  (let [current-project-id @selected-project-id
        current-project-id-decoded (when current-project-id
                                     (try
                                       (let [decoded (js/atob current-project-id)
                                             [_ raw-id] (.split decoded ":")]
                                         (subs raw-id 1))
                                       (catch :default _ nil)))]
    [:div.flex.flex-1.min-h-0
     [:f> Sidebar {:on-select-project (fn [project]
                                        (reset! selected-project-id (:id project))
                                        (reset! selected-session-id nil)
                                        (update-url! (:projectId project) nil))}]
     [SessionsPanel {:project (when current-project-id
                                {:id current-project-id
                                 :name current-project-id-decoded})
                     :on-select-session (fn [session]
                                          (reset! selected-session-id (:id session))
                                          (update-url! current-project-id-decoded (:sessionId session)))}]
     [MessagesPanel]]))

(defn App []
  [:> apollo.react/ApolloProvider {:client apollo-client}
   [:div.h-screen.flex.flex-col.bg-background-base.text-neutral-content.text-sm
    [:f> MainContent]]])

(defonce root (-> js/document (.getElementById "app") reagent.dom.client/create-root))

(defn ^:dev/after-load start []
  (reagent.dom.client/render root [App]))
