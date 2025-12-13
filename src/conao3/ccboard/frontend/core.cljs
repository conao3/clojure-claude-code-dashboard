(ns conao3.ccboard.frontend.core
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
   [conao3.ccboard.lib :as c.lib]
   [conao3.ccboard.schema :as c.schema]
   [conao3.ccboard.util :as c.util]
   [reagent.core :as r]
   [reagent.dom.client :as reagent.dom.client]
   [schema.core :as s]))

(enable-console-print!)

(when goog.DEBUG
  (s/set-fn-validation! true))

(def apollo-client
  (apollo/ApolloClient. #js {:link (apollo/HttpLink. #js {:uri "/api/graphql"})
                             :cache (apollo/InMemoryCache.)
                             :defaultOptions {:query {:errorPolicy "all"}
                                              :watchQuery {:errorPolicy "all"}}}))

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

(s/defn ^:private parse-url-path :- (s/maybe c.schema/UrlPath)
  []
  (let [path (.-pathname js/window.location)
        match (re-matches #"/projects/([^/]+)(?:/sessions/([^/]+))?" path)]
    (when match
      {:project-id (second match)
       :session-id (nth match 2 nil)})))

(s/defn ^:private update-url! :- (s/eq nil)
  [project-id :- (s/maybe s/Str)
   session-id :- (s/maybe s/Str)]
  (let [path (cond
               (and project-id session-id) (str "/projects/" project-id "/sessions/" session-id)
               project-id (str "/projects/" project-id)
               :else "/")]
    (.pushState js/window.history nil "" path))
  nil)

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


(s/defn ProjectItem :- c.schema/Hiccup
  [{:keys [project active on-click]} :- c.schema/ProjectItemProps]
  [:> rac/Button
   {:className (str "flex items-center gap-2 w-full rounded-lg px-3 py-2 transition-all outline-none text-left "
                    (if active "bg-gray-200" "hover:bg-gray-100"))
    :onPress on-click}
   [:> lucide/Folder {:size 16 :className "text-gray-600 flex-shrink-0"}]
   [:span.text-sm.truncate.flex-1.text-gray-900 (c.lib/project-basename (:name project))]])

(s/defn ^:private parse-project-node :- c.schema/Project
  [node :- s/Any]
  (let [^js node node]
    {:__typename "Project"
     :id (.-id node)
     :name (.-name node)
     :projectId (.-projectId node)
     :hasSessions (pos? (count (-> node .-sessions .-edges)))}))

(s/defn ProjectsList :- c.schema/Hiccup
  [{:keys [on-select-project]} :- c.schema/ProjectsListProps]
  (let [scroll-container-ref (react/useRef nil)
        current-project-id @selected-project-id
        list (stately/useAsyncList
              #js {:load (fn [^js opts]
                           (let [cursor (.-cursor opts)]
                             (-> (.query apollo-client (clj->js {:query projects-query
                                                                 :variables {:first 20 :after cursor}}))
                                 (.then (fn [^js result]
                                          (let [data (.-data result)
                                                edges (-> data .-projects .-edges)
                                                page-info (-> data .-projects .-pageInfo)
                                                items (mapv #(parse-project-node (.-node %)) edges)]
                                            (clj->js {:items (filterv :hasSessions items)
                                                      :cursor (when (.-hasNextPage page-info)
                                                                (.-endCursor page-info))})))))))})
        check-scroll-and-load (fn []
                                (when-let [container (.-current scroll-container-ref)]
                                  (let [scroll-top (.-scrollTop container)
                                        scroll-height (.-scrollHeight container)
                                        client-height (.-clientHeight container)
                                        threshold 200]
                                    (when (and (not (.-isLoading list))
                                               (> (+ scroll-top client-height threshold) scroll-height))
                                      (.loadMore list)))))]
    (react/useEffect
     (fn []
       (check-scroll-and-load)
       js/undefined)
     #js [(count (.-items list)) (.-isLoading list)])
    (cond
      (and (.-isLoading list) (zero? (count (.-items list)))) [:div.p-3.text-gray-600.text-sm "Loading..."]
      (.-error list) [:div.p-3.text-negative-1100.text-sm (str "Error: " (.-error list))]
      :else
      [:div.flex-1.overflow-y-auto.min-h-0.p-2.flex.flex-col.gap-1
       {:ref scroll-container-ref
        :on-scroll (fn [_e] (check-scroll-and-load))}
       (for [^js project (.-items list)]
         (let [p {:__typename "Project" :id (.-id project) :name (.-name project) :projectId (.-projectId project) :hasSessions (.-hasSessions project)}]
           ^{:key (:id p)}
           [ProjectItem {:project p
                         :active (= (:id p) current-project-id)
                         :on-click #(on-select-project p)}]))
       (when (.-isLoading list)
         [:div.flex.items-center.justify-center.py-2.text-gray-600
          [:> lucide/Loader2 {:size 14 :className "animate-spin"}]])])))


(s/defn format-date :- (s/maybe s/Str)
  [date-str :- (s/maybe s/Str)]
  (when date-str
    (let [date (js/Date. date-str)]
      (str (.toLocaleDateString date "ja-JP") " " (.toLocaleTimeString date "ja-JP" #js {:hour "2-digit" :minute "2-digit"})))))

(s/defn format-relative-date :- s/Str
  [date-str :- (s/maybe s/Str)]
  (if-not date-str
    ""
    (let [date (js/Date. date-str)
          now (js/Date.)
          diff-ms (- now date)
          diff-days (js/Math.floor (/ diff-ms 86400000))]
      (cond
        (zero? diff-days) "Today"
        (= diff-days 1) "Yesterday"
        (< diff-days 7) (str diff-days " days ago")
        :else (.toLocaleDateString date "en-US" #js {:month "short" :day "numeric"})))))

(s/defn SessionItem :- c.schema/Hiccup
  [{:keys [session active on-click]} :- c.schema/SessionItemProps]
  [:> rac/Button
   {:className (str "w-full text-left px-4 py-3 outline-none transition-all rounded-lg mx-2 mb-1 "
                    (if active "bg-gray-50 shadow-sm" "hover:bg-gray-100"))
    :onPress on-click}
   [:div.flex.items-center.gap-2
    [:span.w-2.h-2.rounded-full.flex-shrink-0
     {:class (if active "bg-positive-900" "bg-gray-400")}]
    [:span.text-sm.font-medium.text-gray-900.truncate.flex-1
     (subs (:sessionId session) 0 (min 24 (count (:sessionId session))))
     "..."]
    [:span.text-xs.text-gray-600 (format-relative-date (:createdAt session))]]])

(s/defn ^:private parse-session-node :- c.schema/Session
  [node :- s/Any]
  (let [^js node node]
    {:__typename "Session"
     :id (.-id node)
     :projectId (.-projectId node)
     :sessionId (.-sessionId node)
     :createdAt (.-createdAt node)}))

(s/defn SessionsList :- c.schema/Hiccup
  [{:keys [project-id on-select-session]} :- c.schema/SessionsListProps]
  (let [scroll-container-ref (react/useRef nil)
        project-id-ref (react/useRef project-id)
        current-session-id @selected-session-id
        list (stately/useAsyncList
              #js {:load (fn [^js opts]
                           (let [pid (.-current project-id-ref)]
                             (if (nil? pid)
                               (js/Promise.resolve (clj->js {:items []}))
                               (let [cursor (.-cursor opts)]
                                 (-> (.query apollo-client (clj->js {:query project-sessions-query
                                                                     :variables {:id pid :first 20 :after cursor}}))
                                     (.then (fn [^js result]
                                              (let [data (.-data result)
                                                    edges (-> data .-node .-sessions .-edges)
                                                    page-info (-> data .-node .-sessions .-pageInfo)
                                                    items (mapv #(parse-session-node (.-node %)) edges)]
                                                (clj->js {:items items
                                                          :cursor (when (.-hasNextPage page-info)
                                                                    (.-endCursor page-info))})))))))))})]
    (react/useEffect
     (fn []
       (set! (.-current project-id-ref) project-id)
       (when project-id (.reload list))
       js/undefined)
     #js [project-id])
    (cond
      (nil? project-id) [:p.p-4.text-gray-600.text-sm "Select a project"]
      (and (.-isLoading list) (zero? (count (.-items list)))) [:div.p-4.text-gray-600.text-sm "Loading sessions..."]
      (.-error list) [:div.p-4.text-negative-1100.text-sm (str "Error: " (.-error list))]
      :else
      [:div.flex-1.overflow-y-auto.pb-2
       {:ref scroll-container-ref}
       (for [^js session (.-items list)]
         (let [s {:__typename "Session" :id (.-id session) :projectId (.-projectId session) :sessionId (.-sessionId session) :createdAt (.-createdAt session)}]
           ^{:key (:id s)}
           [SessionItem {:session s
                         :active (= (:id s) current-session-id)
                         :on-click #(on-select-session s)}]))
       (when (.-isLoading list)
         [:div.flex.items-center.justify-center.py-4.text-gray-600
          [:> lucide/Loader2 {:size 16 :className "animate-spin"}]])])))

(s/defn Sidebar :- c.schema/Hiccup
  [{:keys [on-select-project on-select-session project]} :- c.schema/SidebarProps]
  [:div.flex.flex-col.h-full.min-h-0.w-80.bg-gray-75.border-r.border-gray-200
   [:div.p-4.flex.items-center.gap-3.shrink-0
    [:div.w-8.h-8.rounded-full.flex.items-center.justify-center.flex-shrink-0.bg-notice-300
     [:> lucide/Sparkles {:size 16 :className "text-notice-1200"}]]
    [:span.font-semibold.text-gray-900 "Claude Code"]]

   [:div.px-4.pb-3.shrink-0
    [:div.relative
     [:> lucide/Search {:size 14 :className "absolute left-3 top-1/2 -translate-y-1/2 text-gray-500"}]
     [:input
      {:type "text"
       :placeholder "Find a small todo in the codebase and do it"
       :class "w-full bg-gray-50 border border-gray-200 rounded-lg py-2.5 pl-9 pr-3 text-sm text-gray-900 outline-none focus:border-accent-700 focus:ring-1 focus:ring-accent-700"}]]]

   [:div.px-4.pb-3.flex.items-center.justify-between.shrink-0
    [:span.text-sm.font-medium.text-gray-600 "Projects"]]

   [:f> ProjectsList {:on-select-project on-select-project}]

   (when project
     [:<>
      [:div.px-4.py-2.flex.items-center.justify-between.shrink-0.border-t.border-gray-200
       [:span.text-sm.font-medium.text-gray-600 "Sessions"]
       [:span.text-xs.text-gray-500.truncate.max-w-32 (c.lib/project-basename (:name project))]]

      [:f> SessionsList {:project-id (:id project) :on-select-session on-select-session}]])

   [:div.mt-auto.p-4.border-t.border-gray-200.shrink-0
    [:div.flex.items-center.gap-3
     [:div.w-8.h-8.rounded-full.bg-gray-500.flex.items-center.justify-center.text-white.text-sm "C"]
     [:> lucide/Settings {:size 16 :className "ml-auto text-gray-600"}]]]])

(s/defn CopyButton :- c.schema/Hiccup
  []
  (let [copied? (r/atom false)]
    (s/fn [{:keys [text label class]} :- c.schema/CopyButtonProps]
      [:> rac/Button
       {:className (str "px-2 py-1 rounded bg-gray-200 text-gray-700 opacity-0 group-hover:opacity-70 hover:opacity-100 pressed:opacity-100 flex items-center gap-1 text-xs " class)
        :onPress (fn []
                   (-> js/navigator .-clipboard (.writeText text))
                   (reset! copied? true)
                   (js/setTimeout #(reset! copied? false) 1000))}
       (if @copied?
         [:<> [:> lucide/Check {:size 12}] "Copied!"]
         [:<> [:> lucide/Copy {:size 12}] (or label "Copy")])])))

(def markdown-components
  {:h1 (fn [props] (r/as-element [:h1.text-xl.font-bold.mt-4.mb-2.text-gray-900 (.-children props)]))
   :h2 (fn [props] (r/as-element [:h2.text-lg.font-bold.mt-3.mb-2.text-gray-900 (.-children props)]))
   :h3 (fn [props] (r/as-element [:h3.text-base.font-bold.mt-2.mb-1.text-gray-900 (.-children props)]))
   :p (fn [props] (r/as-element [:p.mb-2.last:mb-0.text-gray-900 (.-children props)]))
   :ul (fn [props] (r/as-element [:ul.list-disc.list-inside.mb-2.text-gray-900 (.-children props)]))
   :ol (fn [props] (r/as-element [:ol.list-decimal.list-inside.mb-2.text-gray-900 (.-children props)]))
   :li (fn [props] (r/as-element [:li.mb-1 (.-children props)]))
   :code (fn [props]
           (if (.-inline props)
             (r/as-element [:code.bg-gray-100.px-1.py-0.5.rounded.text-sm.font-mono (.-children props)])
             (r/as-element [:code.font-mono (.-children props)])))
   :pre (fn [props] (r/as-element [:pre.bg-gray-100.p-3.rounded-lg.overflow-x-auto.mb-2.text-sm (.-children props)]))
   :blockquote (fn [props] (r/as-element [:blockquote.border-l-4.border-gray-400.pl-4.italic.text-gray-700.mb-2 (.-children props)]))
   :a (fn [props] (r/as-element [:a.text-accent-1100.underline {:href (.-href props) :target "_blank"} (.-children props)]))
   :table (fn [props] (r/as-element [:table.w-full.border-collapse.mb-2 (.-children props)]))
   :th (fn [props] (r/as-element [:th.border.border-gray-400.px-2.py-1.bg-gray-100.text-left.font-medium (.-children props)]))
   :td (fn [props] (r/as-element [:td.border.border-gray-400.px-2.py-1 (.-children props)]))})

(s/defn Markdown :- c.schema/Hiccup
  [{:keys [children class]} :- c.schema/MarkdownProps]
  [:> ReactMarkdown/default
   {:remarkPlugins #js [remarkGfm/default]
    :components (clj->js markdown-components)
    :className class}
   children])

(s/defn ToolResultBlock :- c.schema/Hiccup
  [{:keys [block show-content?]} :- {:block c.schema/ContentBlock
                                     (s/optional-key :show-content?) (s/maybe s/Bool)}]
  (when show-content?
    [:div.mt-1.ml-6.text-xs.text-gray-600
     [:details
      [:summary.cursor-pointer.flex.items-center.gap-1
       [:span "└"]
       [:span (let [content-str (str (:content block))]
                (if (> (count content-str) 50)
                  (str (subs content-str 0 50) "...")
                  content-str))]]
      [:pre.mt-1.p-2.bg-gray-100.rounded.whitespace-pre-wrap.break-all.font-mono.max-h-48.overflow-auto
       (:content block)]]]))

(s/defn ^:private tool-icon :- c.schema/Hiccup
  [tool-name :- s/Str]
  (let [icon-class "text-gray-600"]
    (case tool-name
      "Read" [:> lucide/FileText {:size 14 :className icon-class}]
      "Edit" [:> lucide/Pencil {:size 14 :className icon-class}]
      "Write" [:> lucide/FilePlus {:size 14 :className icon-class}]
      "Bash" [:> lucide/Terminal {:size 14 :className icon-class}]
      "Glob" [:> lucide/Search {:size 14 :className icon-class}]
      "Grep" [:> lucide/Search {:size 14 :className icon-class}]
      "Task" [:> lucide/ListTodo {:size 14 :className icon-class}]
      "WebFetch" [:> lucide/Globe {:size 14 :className icon-class}]
      "WebSearch" [:> lucide/Search {:size 14 :className icon-class}]
      [:> lucide/Wrench {:size 14 :className icon-class}])))

(s/defn ^:private parse-tool-input :- s/Any
  [input :- (s/maybe s/Str)]
  (when input
    (try
      (js->clj (js/JSON.parse input) :keywordize-keys true)
      (catch :default _ nil))))

(s/defn ^:private tool-use-summary :- s/Str
  [tool-name :- s/Str input-map :- (s/maybe {s/Keyword s/Any})]
  (case tool-name
    "Read" (or (:file_path input-map) "")
    "Edit" (or (:file_path input-map) "")
    "Write" (or (:file_path input-map) "")
    "Glob" (or (:pattern input-map) "")
    "Grep" (or (:pattern input-map) "")
    "Bash" (let [cmd (or (:command input-map) "")]
             (if (> (count cmd) 60) (str (subs cmd 0 60) "...") cmd))
    "Task" (or (:description input-map) "")
    "WebFetch" (or (:url input-map) "")
    "WebSearch" (or (:query input-map) "")
    "TodoWrite" "updating todos"
    ""))

(s/defn ContentBlock :- c.schema/Hiccup
  [{:keys [block tool-results]} :- c.schema/ContentBlockProps]
  (case (:type block)
    "text"
    [:div.mt-2
     [:div.flex.items-center.gap-2.text-sm
      [:span.w-2.h-2.rounded-full.bg-positive-900.flex-shrink-0]
      [:> lucide/MessageSquare {:size 14 :className "text-gray-600"}]
      [:span.font-medium.text-gray-900 "Message"]]
     [:div.ml-6.mt-1.text-sm.text-gray-900
      [Markdown {:children (:text block)}]]]

    "thinking"
    [:details.mt-2
     [:summary.flex.items-center.gap-2.cursor-pointer.text-sm.text-gray-600
      [:span.w-2.h-2.rounded-full.bg-notice-700.flex-shrink-0]
      [:> lucide/Brain {:size 14}]
      [:span "Thinking..."]]
     [:div.mt-2.ml-6.p-3.rounded-lg.bg-notice-200.border.border-notice-400
      [:pre.text-xs.whitespace-pre-wrap.break-all.text-gray-700 (:thinking block)]]]

    "tool_use"
    (let [result (get tool-results (:id block))
          tool-name (:name block)
          input-map (parse-tool-input (:input block))
          summary (tool-use-summary tool-name input-map)
          show-result? (contains? #{"Edit" "Write"} tool-name)]
      [:div.mt-2
       [:div.flex.items-center.gap-2.text-sm
        [:span.w-2.h-2.rounded-full.bg-positive-900.flex-shrink-0]
        [tool-icon tool-name]
        [:span.font-medium.text-gray-900 tool-name]
        (when (seq summary)
          [:span.text-gray-600.font-mono.text-xs.truncate {:title summary} summary])]
       (when (and result show-result? (:content result))
         [:div.mt-1.ml-6.text-xs.text-gray-600
          [:details
           [:summary.cursor-pointer.flex.items-center.gap-1
            "└ Show result"]
           [:pre.mt-1.p-2.bg-gray-100.rounded.whitespace-pre-wrap.break-all.font-mono.max-h-64.overflow-auto
            (:content result)]]])])

    "tool_result"
    nil

    [:div.mt-3.p-3.rounded-lg.bg-notice-400.text-notice-1200
     [:div.text-xs.font-medium (str "Unknown: " (:type block))]]))

(s/defn UserMessageBubble :- c.schema/Hiccup
  [{:keys [content]} :- {:content c.schema/Hiccup}]
  [:div.mb-4.rounded-2xl.p-4.bg-accent-200
   content])

(s/defn AssistantMessageBubble :- c.schema/Hiccup
  [{:keys [content raw-details]} :- {:content c.schema/Hiccup
                                     (s/optional-key :raw-details) (s/maybe c.schema/Hiccup)}]
  [:div.mb-4.relative.group
   content
   raw-details])

(s/defn safe-yaml-dump :- s/Str
  [raw-message :- (s/maybe s/Str)]
  (try
    (-> raw-message js/JSON.parse yaml/dump)
    (catch :default _
      raw-message)))

(s/defn AssistantMessage :- c.schema/Hiccup
  [{:keys [message tool-results]} :- c.schema/AssistantMessageProps]
  (let [yaml-text (safe-yaml-dump (:rawMessage message))
        content-blocks (get-in message [:message :content])]
    [AssistantMessageBubble
     {:content (into [:div]
                     (map-indexed
                      (fn [idx block]
                        ^{:key idx} [ContentBlock {:block block :tool-results tool-results}])
                      content-blocks))
      :raw-details [:details.absolute.top-0.right-2.opacity-0.group-hover:opacity-100.transition-opacity
                    [:summary.text-gray-500.hover:text-gray-700.cursor-pointer.p-1.list-none
                     [:> lucide/Code {:size 16}]]
                    [:div.absolute.right-0.top-6.z-10.bg-white.border.border-gray-200.rounded-lg.shadow-lg.p-3.w-96
                     [:div.flex.justify-between.items-center.mb-2
                      [:span.text-xs.font-medium.text-gray-600 "Raw"]
                      [:div.flex.gap-1
                       [CopyButton {:text yaml-text :label "Copy"}]
                       [CopyButton {:text (:rawMessage message) :label "JSON"}]]]
                     [:pre.text-xs.whitespace-pre-wrap.break-all.bg-gray-100.p-2.rounded.max-h-64.overflow-auto.text-gray-700 yaml-text]]]}]))

(s/defn UserMessage :- c.schema/Hiccup
  [{:keys [message]} :- c.schema/UserMessageProps]
  (let [yaml-text (safe-yaml-dump (:rawMessage message))
        content-blocks (get-in message [:message :content])
        text-blocks (filter #(= (:type %) "text") content-blocks)
        has-text? (seq text-blocks)]
    (when has-text?
      [UserMessageBubble
       {:content (into [:div]
                       (map-indexed
                        (fn [idx block]
                          ^{:key idx}
                          (when (= (:type block) "text")
                            [:div.text-sm.leading-relaxed.text-gray-900
                             [Markdown {:children (:text block)}]]))
                        content-blocks))}])))

(s/defn SystemMessageItem :- c.schema/Hiccup
  [{:keys [message]} :- c.schema/SystemMessageItemProps]
  (let [yaml-text (safe-yaml-dump (:rawMessage message))]
    [:div.mb-3.opacity-60
     [:div {:class "rounded-lg p-3 bg-informative-200 border border-informative-400"}
      [:div.flex.items-center.gap-2.text-xs.text-informative-1200
       [:> lucide/Settings {:size 12}]
       [:span.font-medium (str "System: " (:subtype message))]
       [:span.text-gray-600 (:timestamp message)]]
      [:details.mt-2
       [:summary.text-xs.text-gray-600.cursor-pointer "Raw"]
       [:div.mt-2.text-xs.text-gray-700
        [:div [:span.font-medium "Content: "] (:systemContent message)]
        [:div [:span.font-medium "Level: "] (:level message)]
        (when-let [cm (:compactMetadata message)]
          [:<>
           [:div [:span.font-medium "Trigger: "] (:trigger cm)]
           [:div [:span.font-medium "Pre-tokens: "] (:preTokens cm)]])]
       [:div.relative.group.mt-2
        [:div.absolute.top-1.right-1
         [CopyButton {:text yaml-text :label "Copy"}]]
        [:pre.text-xs.whitespace-pre-wrap.break-all.bg-gray-100.p-2.rounded.max-h-32.overflow-auto yaml-text]]]]]))

(s/defn SummaryMessageItem :- c.schema/Hiccup
  [{:keys [message]} :- c.schema/SummaryMessageItemProps]
  (let [yaml-text (safe-yaml-dump (:rawMessage message))]
    [:div.mb-3.opacity-60
     [:div {:class "rounded-lg p-3 bg-positive-200 border border-positive-400"}
      [:div.flex.items-center.gap-2.text-xs.text-positive-1200
       [:> lucide/FileText {:size 12}]
       [:span.font-medium "Summary"]]
      [:p.mt-1.text-sm.text-gray-800 (:summary message)]
      [:details.mt-2
       [:summary.text-xs.text-gray-600.cursor-pointer "Raw"]
       [:div.relative.group.mt-2
        [:div.absolute.top-1.right-1
         [CopyButton {:text yaml-text :label "Copy"}]]
        [:pre.text-xs.whitespace-pre-wrap.break-all.bg-gray-100.p-2.rounded.max-h-32.overflow-auto yaml-text]]]]]))

(s/defn FileHistorySnapshotMessage :- c.schema/Hiccup
  [{:keys [message]} :- c.schema/FileHistorySnapshotMessageProps]
  (let [yaml-text (safe-yaml-dump (:rawMessage message))
        snapshot (:snapshot message)
        tracked-file-backups (-> (:trackedFileBackups snapshot) js/JSON.parse js/Object.keys js->clj)]
    [:div.mb-3.opacity-50
     [:div.rounded-lg.p-3.bg-gray-100.border.border-gray-300
      [:div.flex.items-center.gap-2.text-xs.text-gray-600
       [:> lucide/History {:size 12}]
       [:span "FileHistorySnapshot"]
       (when (:isSnapshotUpdate message)
         [:span.text-xs.bg-gray-600.text-white.px-1.rounded "update"])]
      [:details.mt-2
       [:summary.text-xs.text-gray-600.cursor-pointer
        (str (count tracked-file-backups) " tracked files")]
       [:ul.mt-1.text-xs.text-gray-600.ml-4.list-disc
        (for [path tracked-file-backups]
          ^{:key path} [:li.truncate path])]
       [:div.relative.group.mt-2
        [:div.absolute.top-1.right-1
         [CopyButton {:text yaml-text :label "Copy"}]]
        [:pre.text-xs.whitespace-pre-wrap.break-all.bg-gray-50.p-2.rounded.max-h-32.overflow-auto yaml-text]]]]]))

(s/defn QueueOperationMessage :- c.schema/Hiccup
  [{:keys [message]} :- c.schema/QueueOperationMessageProps]
  (let [yaml-text (safe-yaml-dump (:rawMessage message))]
    [:div.mb-3.opacity-50
     [:div.rounded-lg.p-3.bg-gray-100.border.border-gray-300
      [:div.flex.items-center.gap-2.text-xs.text-gray-600
       [:> lucide/ListOrdered {:size 12}]
       [:span (str "Queue: " (:operation message))]]
      [:details.mt-2
       [:summary.text-xs.text-gray-600.cursor-pointer "Raw"]
       [:div.mt-1.text-xs.text-gray-700
        (when (:content message) [:div [:span.font-medium "Content: "] (:content message)])
        [:div [:span.font-medium "Session: "] (:queueSessionId message)]
        [:div [:span.font-medium "Time: "] (:timestamp message)]]
       [:div.relative.group.mt-2
        [:div.absolute.top-1.right-1
         [CopyButton {:text yaml-text :label "Copy"}]]
        [:pre.text-xs.whitespace-pre-wrap.break-all.bg-gray-50.p-2.rounded.max-h-32.overflow-auto yaml-text]]]]]))

(s/defn UnknownMessage :- c.schema/Hiccup
  [{:keys [message]} :- c.schema/UnknownMessageProps]
  (let [yaml-text (safe-yaml-dump (:rawMessage message))]
    [:div.mb-3
     [:div.rounded-lg.p-3.bg-notice-600.text-notice-1400
      [:div.flex.items-center.gap-2.text-xs
       [:> lucide/HelpCircle {:size 12}]
       [:span (str "Unknown: " (:messageId message))]]
      [:details.mt-2
       [:summary.text-xs.cursor-pointer "Raw"]
       [:pre {:class "text-xs whitespace-pre-wrap break-all bg-black/20 p-2 rounded mt-1 max-h-32 overflow-auto"} yaml-text]]]]))

(s/defn BrokenMessage :- c.schema/Hiccup
  [{:keys [message]} :- c.schema/BrokenMessageProps]
  [:div.mb-3
   [:div.rounded-lg.p-3.bg-negative-700.text-negative-1400
    [:div.flex.items-center.gap-2.text-xs
     [:> lucide/AlertTriangle {:size 12}]
     [:span (str "Broken: " (:messageId message))]]
    [:details.mt-2
     [:summary.text-xs.cursor-pointer "Raw"]
     [:pre {:class "text-xs whitespace-pre-wrap break-all bg-black/20 p-2 rounded mt-1 max-h-32 overflow-auto"}
      (:rawMessage message)]]]])

(s/defn safe-render-message :- (s/maybe c.schema/Hiccup)
  [{:keys [message tool-results]} :- c.schema/SafeRenderMessageProps]
  (try
    (case (:__typename message)
      "AssistantMessage" [AssistantMessage {:message message :tool-results tool-results}]
      "UserMessage" [UserMessage {:message message}]
      "UnknownMessage" [UnknownMessage {:message message}]
      "BrokenMessage" [BrokenMessage {:message message}]
      "FileHistorySnapshotMessage" [FileHistorySnapshotMessage {:message message}]
      "QueueOperationMessage" [QueueOperationMessage {:message message}]
      "SystemMessage" [SystemMessageItem {:message message}]
      "SummaryMessage" [SummaryMessageItem {:message message}]
      nil)
    (catch :default e
      [:div.mb-3
       [:div.rounded-lg.p-3.bg-negative-700.text-negative-1400
        [:div.flex.items-center.gap-2.text-xs
         [:> lucide/AlertTriangle {:size 12}]
         [:span (str "Render error: " (.-message e))]]]])))

(s/defn ^:private parse-message-node :- c.schema/FrontendMessage
  [node :- s/Any]
  (let [^js node node
        typename (.-__typename node)
        ^js snapshot (.-snapshot node)]
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
     :compactMetadata (when-let [^js cm (.-compactMetadata node)]
                        {:trigger (.-trigger cm)
                         :preTokens (.-preTokens cm)})
     :summary (.-summary node)
     :leafUuid (.-leafUuid node)
     :message (case typename
                "AssistantMessage"
                (when-let [^js msg (.-assistantMessage node)]
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
                (when-let [^js msg (.-userMessage node)]
                  {:content (mapv (fn [^js block]
                                    {:type (.-type block)
                                     :text (.-text block)
                                     :tool_use_id (.-tool_use_id block)
                                     :content (.-content block)})
                                  (.-content msg))})
                nil)}))

(s/defn MessageList :- c.schema/Hiccup
  []
  (let [session-id @selected-session-id
        scroll-container-ref (react/useRef nil)
        session-id-ref (react/useRef session-id)
        has-next-page-ref (react/useRef false)
        list (stately/useAsyncList
              #js {:load (fn [^js opts]
                           (let [sid (.-current session-id-ref)]
                             (if (nil? sid)
                               (js/Promise.resolve (clj->js {:items []}))
                               (let [cursor (.-cursor opts)]
                                 (-> (.query apollo-client (clj->js {:query session-messages-query
                                                                     :variables {:id sid :first 20 :after cursor}}))
                                     (.then (fn [^js result]
                                              (let [data (.-data result)
                                                    edges (-> data .-node .-messages .-edges)
                                                    page-info (-> data .-node .-messages .-pageInfo)
                                                    items (mapv #(parse-message-node (.-node %)) edges)]
                                                (set! (.-current has-next-page-ref) (.-hasNextPage page-info))
                                                (clj->js {:items items
                                                          :cursor (when (.-hasNextPage page-info)
                                                                    (.-endCursor page-info))})))))))))})
        check-scroll-and-load (fn []
                                (when-let [container (.-current scroll-container-ref)]
                                  (let [scroll-top (.-scrollTop container)
                                        scroll-height (.-scrollHeight container)
                                        client-height (.-clientHeight container)
                                        threshold 200]
                                    (when (and (not (.-isLoading list))
                                               (> (+ scroll-top client-height threshold) scroll-height))
                                      (.loadMore list)))))]
    (react/useEffect
     (fn []
       (set! (.-current session-id-ref) session-id)
       (when session-id (.reload list))
       js/undefined)
     #js [session-id])
    (react/useEffect
     (fn []
       (check-scroll-and-load)
       js/undefined)
     #js [(count (.-items list)) (.-isLoading list)])
    (let [messages (vec (for [^js item (.-items list)]
                          (js->clj item :keywordize-keys true)))
          has-next-page (.-current has-next-page-ref)
          tool-results (->> messages
                            (mapcat #(get-in % [:message :content]))
                            (filter #(= (:type %) "tool_result"))
                            (reduce (fn [m block] (assoc m (:tool_use_id block) block)) {}))
          message-count (count messages)
          tool-call-count (->> messages
                               (filter #(= (:__typename %) "AssistantMessage"))
                               (mapcat #(get-in % [:message :content]))
                               (filter #(= (:type %) "tool_use"))
                               count)]
      (cond
        (nil? session-id) [:div.flex-1.flex.items-center.justify-center.text-gray-600 "Select a session to view messages"]
        (and (.-isLoading list) (zero? (count messages))) [:div.flex-1.flex.items-center.justify-center.text-gray-600 "Loading messages..."]
        (.-error list) [:div.flex-1.flex.items-center.justify-center.text-negative-1100 (str "Error: " (.-error list))]
        :else
        [:div.flex-1.flex.flex-col.min-h-0
         [:div.flex.items-center.gap-4.text-sm.text-gray-600.mb-4.shrink-0
          [:span (str message-count " messages" (when has-next-page "+"))]
          [:span "•"]
          [:span (str tool-call-count " tool calls")]]
         [:div.flex-1.overflow-y-auto.min-h-0.pr-4.pl-2
          {:ref scroll-container-ref
           :on-scroll (fn [_e] (check-scroll-and-load))}
          (if (empty? messages)
            [:div.text-gray-600 "No messages"]
            [:<>
             (for [[idx message] (map-indexed vector messages)]
               ^{:key idx}
               [safe-render-message {:message message
                                     :tool-results tool-results}])
             (when (.-isLoading list)
               [:div.flex.items-center.justify-center.py-4.text-gray-600
                [:> lucide/Loader2 {:size 20 :className "animate-spin mr-2"}]
                "Loading more..."])])]]))))

(s/defn MessagesPanel :- c.schema/Hiccup
  [{:keys [session-title]} :- {:session-title (s/maybe s/Str)}]
  [:div.flex-1.flex.flex-col.bg-gray-25.min-h-0.overflow-hidden
   [:div.p-4.border-b.border-gray-200.shrink-0.flex.items-center.justify-between
    [:div.flex.items-center.gap-2
     [:h2.text-base.font-medium.text-gray-900.truncate (or session-title "Select a session")]
     [:> lucide/ChevronDown {:size 16 :className "text-gray-600"}]]]
   [:div.flex-1.flex.flex-col.min-h-0.p-6
    [:f> MessageList]]])

(defonce url-initialized (atom false))

(s/defn ^:private init-url! :- (s/eq nil)
  []
  (when-not @url-initialized
    (when-let [{:keys [project-id session-id]} (parse-url-path)]
      (reset! selected-project-id (js/btoa (str "Project:" project-id)))
      (when session-id
        (reset! selected-session-id (js/btoa (str "Session:" project-id "/" session-id)))))
    (reset! url-initialized true))
  nil)

(s/defn MainContent :- c.schema/Hiccup
  []
  (init-url!)
  (let [current-project-id @selected-project-id
        current-session-id @selected-session-id
        session-title (when current-session-id
                        (try
                          (let [decoded (js/atob current-session-id)
                                parts (.split decoded "/")]
                            (aget parts (dec (.-length parts))))
                          (catch :default _ nil)))]
    [:div.flex.flex-1.min-h-0
     [:f> Sidebar {:on-select-project (fn [project]
                                        (reset! selected-project-id (:id project))
                                        (reset! selected-session-id nil)
                                        (update-url! (:projectId project) nil))
                   :on-select-session (fn [session]
                                        (reset! selected-session-id (:id session))
                                        (update-url! (:projectId session) (:sessionId session)))
                   :project (when current-project-id
                              {:id current-project-id
                               :name (-> (js/atob current-project-id) (.split ":") second)})}]
     [MessagesPanel {:session-title session-title}]]))

(s/defn App :- c.schema/Hiccup
  []
  [:> apollo.react/ApolloProvider {:client apollo-client}
   [:div.h-screen.flex.flex-col.bg-background-layer-2.text-neutral-content.text-sm
    [:f> MainContent]]])

(defonce root (-> js/document (.getElementById "app") reagent.dom.client/create-root))

(s/defn start :- (s/eq nil)
  {:dev/after-load true}
  []
  (reagent.dom.client/render root [App])
  nil)
