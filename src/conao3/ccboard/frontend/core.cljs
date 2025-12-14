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
   [schema.core :as s]
   [shadow.resource :as rc]))

(enable-console-print!)

(when goog.DEBUG
  (s/set-fn-validation! true))

(def apollo-client
  (apollo/ApolloClient. #js {:link (apollo/HttpLink. #js {:uri "/api/graphql"})
                             :cache (apollo/InMemoryCache. #js {:possibleTypes (js/JSON.parse (rc/inline "public/possibleTypes.json"))})
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

(defonce selected-project-id (r/atom nil))
(defonce selected-session-id (r/atom nil))

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
              id
              messageId
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
                    toolUseResult {
                      type
                      filePath
                      oldString
                      newString
                      content
                      structuredPatch {
                        oldStart
                        oldLines
                        newStart
                        newLines
                        lines
                      }
                    }
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

(def message-raw-query
  (apollo/gql "query MessageRaw($id: ID!) {
    node(id: $id) {
      ... on Message { rawMessage }
    }
  }"))

(s/defn ProjectItem :- c.schema/Hiccup
  [{:keys [project active on-click]} :- c.schema/ProjectItemProps]
  [:> rac/Button
   {:class (c.util/clsx "group flex items-center gap-3 w-full rounded-lg px-3 py-2.5 transition-all duration-200 outline-none text-left border border-transparent"
                        {"bg-cyan-900/20" active
                         "border-cyan-700/50" active
                         "shadow-[0_0_12px_-3px_rgba(24,142,220,0.4)]" active
                         "hover:bg-gray-100/80" (not active)
                         "hover:border-gray-300/30" (not active)})
    :onPress on-click}
   [:div {:class (c.util/clsx "flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-md transition-all duration-200"
                              {"bg-cyan-800/30" active
                               :text-cyan-400 active
                               :bg-gray-200 (not active)
                               :text-gray-500 (not active)
                               :group-hover:bg-gray-300 (not active)
                               :group-hover:text-gray-600 (not active)})}
    [:> lucide/Folder {:size 14}]]
   [:span {:class (c.util/clsx "flex-1 truncate text-sm font-medium transition-colors duration-200"
                               {:text-cyan-300 active
                                :text-gray-800 (not active)
                                :group-hover:text-gray-900 (not active)})}
    (c.lib/project-basename (:name project))]])

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
        async-list (stately/useAsyncList
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
                                    (when (and (not (.-isLoading async-list))
                                               (> (+ scroll-top client-height threshold) scroll-height))
                                      (.loadMore async-list)))))]
    (react/useEffect
     (fn []
       (check-scroll-and-load)
       js/undefined)
     #js [(count (.-items async-list)) (.-isLoading async-list)])
    (cond
      (and (.-isLoading async-list) (zero? (count (.-items async-list))))
      [:div.flex.items-center.gap-2.py-4.text-sm.text-gray-500
       [:div.relative.h-4.w-4
        [:span.absolute.inset-0.animate-ping.rounded-full.bg-cyan-500.opacity-30]
        [:span.relative.flex.h-4.w-4.items-center.justify-center.rounded-full.bg-cyan-600
         [:> lucide/Loader2 {:size 10 :class "animate-spin text-white"}]]]
       "Loading projects..."]
      (.-error async-list) [:div.text-sm.text-negative-1100 (str "Error: " (.-error async-list))]
      :else
      [:div.flex.flex-1.flex-col.gap-1.overflow-y-auto
       {:ref scroll-container-ref
        :on-scroll (fn [_e] (check-scroll-and-load))}
       (for [^js project (.-items async-list)]
         (let [p {:__typename "Project" :id (.-id project) :name (.-name project) :projectId (.-projectId project) :hasSessions (.-hasSessions project)}]
           ^{:key (:id p)}
           [ProjectItem {:project p
                         :active (= (:id p) current-project-id)
                         :on-click #(on-select-project p)}]))
       (when (.-isLoading async-list)
         [:div.flex.items-center.justify-center.gap-2.py-3.text-xs.text-gray-500
          [:> lucide/Loader2 {:size 14 :class "animate-spin text-cyan-500"}]
          "Loading more..."])])))


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
        (= 1 diff-days) "Yesterday"
        (< diff-days 7) (str diff-days " days ago")
        :else (.toLocaleDateString date "en-US" #js {:month "short" :day "numeric"})))))

(s/defn SessionItem :- c.schema/Hiccup
  [{:keys [session active on-click]} :- c.schema/SessionItemProps]
  [:> rac/Button
   {:class (c.util/clsx "group w-full text-left px-3 py-3 outline-none transition-all duration-200 rounded-lg border border-transparent"
                        {"bg-purple-900/20" active
                         "border-purple-700/50" active
                         "shadow-[0_0_12px_-3px_rgba(173,105,233,0.4)]" active
                         "hover:bg-gray-100/60" (not active)
                         "hover:border-gray-300/20" (not active)})
    :onPress on-click}
   [:div.flex.items-center.gap-3
    [:div {:class (c.util/clsx "relative flex h-2.5 w-2.5 flex-shrink-0"
                               {:animate-glow-pulse active})}
     [:span {:class (c.util/clsx "absolute inset-0 rounded-full"
                                 {:bg-purple-500 active
                                  "shadow-[0_0_8px_2px_rgba(173,105,233,0.6)]" active
                                  :bg-gray-400 (not active)})}]
     [:span {:class (c.util/clsx "relative inline-flex h-full w-full rounded-full"
                                 {:bg-purple-400 active
                                  :bg-gray-400 (not active)})}]]
    [:div.5.flex.min-w-0.flex-1.flex-col.gap-0
     [:span {:class (c.util/clsx "truncate font-mono text-xs font-medium tracking-tight transition-colors duration-200"
                                 {:text-purple-600 active
                                  :text-gray-800 (not active)
                                  :group-hover:text-gray-900 (not active)})}
      (subs (:sessionId session) 0 (min 20 (count (:sessionId session))))
      "..."]
     [:span.text-xs.text-gray-600 (format-relative-date (:createdAt session))]]]])

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
        async-list (stately/useAsyncList
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
       (when project-id (.reload async-list))
       js/undefined)
     #js [project-id])
    (cond
      (nil? project-id) [:div.text-sm.text-gray-500 "Select a project"]
      (and (.-isLoading async-list) (zero? (count (.-items async-list))))
      [:div.flex.items-center.gap-2.py-4.text-sm.text-gray-500
       [:div.relative.h-4.w-4
        [:span.absolute.inset-0.animate-ping.rounded-full.bg-purple-500.opacity-30]
        [:span.relative.flex.h-4.w-4.items-center.justify-center.rounded-full.bg-purple-600
         [:> lucide/Loader2 {:size 10 :class "animate-spin text-white"}]]]
       "Loading sessions..."]
      (.-error async-list) [:div.text-sm.text-negative-1100 (str "Error: " (.-error async-list))]
      :else
      [:div.flex-1.overflow-x-hidden.overflow-y-auto.pb-2
       {:ref scroll-container-ref}
       (for [^js session (.-items async-list)]
         (let [s {:__typename "Session" :id (.-id session) :projectId (.-projectId session) :sessionId (.-sessionId session) :createdAt (.-createdAt session)}]
           ^{:key (:id s)}
           [SessionItem {:session s
                         :active (= (:id s) current-session-id)
                         :on-click #(on-select-session s)}]))
       (when (.-isLoading async-list)
         [:div.flex.items-center.justify-center.gap-2.py-3.text-xs.text-gray-500
          [:> lucide/Loader2 {:size 14 :class "animate-spin text-purple-500"}]
          "Loading more..."])])))

(s/defn Sidebar :- c.schema/Hiccup
  [{:keys [on-select-project on-select-session project]} :- c.schema/SidebarProps]
  (let [l-padding "pl-3"]
    [:div.noise-overlay.flex.h-full.w-80.flex-shrink-0.flex-col.overflow-hidden.border-r.bg-gray-50
     {:class "border-gray-200/50"}
     [:div.flex.items-center.gap-3.p-4.py-3
      [:div.tech-gradient.flex.h-9.w-9.flex-shrink-0.items-center.justify-center.rounded-xl.shadow-lg
       {:class "shadow-cyan-900/30"}
       [:> lucide/Sparkles {:size 18 :class "text-white"}]]
      [:div.flex.flex-col
       [:span.font-semibold.tracking-tight.text-gray-900 "CCboard"]
       [:span.text-xs.text-gray-500 "Dashboard"]]]
     [:div.flex.flex-1.flex-col.gap-2.overflow-hidden.border-t
      {:class "border-gray-200/50"}
      [:div.flex.items-center.gap-2.pt-4.text-xs.font-semibold.tracking-wider.text-gray-600.uppercase
       {:class (c.util/clsx l-padding)}
       [:> lucide/FolderOpen {:size 12}]
       "Projects"]
      [:div.flex-1.overflow-auto.pr-3
       {:class (c.util/clsx l-padding)}
       [:f> ProjectsList {:on-select-project on-select-project}]]]
     [:div.flex.flex-1.flex-col.gap-2.overflow-hidden
      [:div.flex.shrink-0.items-center.justify-between.border-t.px-4.pt-4
       {:class (c.util/clsx "border-gray-200/50" l-padding)}
       [:div.flex.items-center.gap-2.text-xs.font-semibold.tracking-wider.text-gray-600.uppercase
        [:> lucide/MessageCircle {:size 12}]
        "Sessions"]
       (when project
         [:span.5.max-w-32.truncate.rounded-full.bg-gray-200.px-2.py-0.font-mono.text-xs.text-gray-600
          (c.lib/project-basename (:name project))])]
      [:div.flex-1.overflow-auto.py-1
       {:class (c.util/clsx l-padding)}
       (if project
         [:f> SessionsList {:project-id (:id project) :on-select-session on-select-session}]
         [:div.flex.flex-col.items-center.justify-center.gap-2.py-8.text-center
          [:div.flex.h-10.w-10.items-center.justify-center.rounded-full.bg-gray-200
           [:> lucide/MousePointerClick {:size 18 :class "text-gray-400"}]]
          [:p.text-sm.text-gray-500 "Select a project"]])]]]))

(s/defn CopyButton :- c.schema/Hiccup
  []
  (let [copied? (r/atom false)]
    (s/fn [{:keys [text label class]} :- c.schema/CopyButtonProps]
      [:> rac/Button
       {:class (c.util/clsx "px-2 py-1 rounded-md border border-gray-300/50 bg-gray-100 text-gray-600 opacity-0 group-hover:opacity-100 hover:border-cyan-600/50 hover:bg-cyan-900/20 hover:text-cyan-400 pressed:bg-cyan-900/30 flex items-center gap-1.5 text-xs font-medium transition-all duration-200" class)
        :onPress (fn []
                   (-> js/navigator .-clipboard (.writeText text))
                   (reset! copied? true)
                   (js/setTimeout #(reset! copied? false) 1000))}
       (if @copied?
         [:<>
          [:> lucide/Check {:size 12 :class "text-green-400"}]
          [:span.text-green-400 "Copied!"]]
         [:<>
          [:> lucide/Copy {:size 12}]
          (or label "Copy")])])))

(def markdown-components
  {:h1 (fn [props] (r/as-element [:h1.mt-4.mb-2.text-xl.font-bold.text-gray-900 (.-children props)]))
   :h2 (fn [props] (r/as-element [:h2.mt-3.mb-2.text-lg.font-bold.text-gray-900 (.-children props)]))
   :h3 (fn [props] (r/as-element [:h3.mt-2.mb-1.text-base.font-bold.text-gray-900 (.-children props)]))
   :p (fn [props] (r/as-element [:p.last.mb-2:mb-0.text-gray-900 (.-children props)]))
   :ul (fn [props] (r/as-element [:ul.mb-2.list-inside.list-disc.text-gray-900 (.-children props)]))
   :ol (fn [props] (r/as-element [:ol.mb-2.list-inside.list-decimal.text-gray-900 (.-children props)]))
   :li (fn [props] (r/as-element [:li.mb-1 (.-children props)]))
   :code (fn [props]
           (if (.-inline props)
             (r/as-element [:code.5.rounded.bg-gray-100.px-1.py-0.font-mono.text-sm (.-children props)])
             (r/as-element [:code.font-mono (.-children props)])))
   :pre (fn [props] (r/as-element [:pre.bg-gray-100.p-3.rounded-lg.overflow-x-auto.mb-2.text-sm (.-children props)]))
   :blockquote (fn [props] (r/as-element [:blockquote.border-l-4.border-gray-400.pl-4.italic.text-gray-700.mb-2 (.-children props)]))
   :a (fn [props] (r/as-element [:a.text-accent-1100.underline {:href (.-href props) :target "_blank"} (.-children props)]))
   :table (fn [props] (r/as-element [:table.mb-2.w-full.border-collapse (.-children props)]))
   :th (fn [props] (r/as-element [:th.border.border-gray-400.bg-gray-100.px-2.py-1.text-left.font-medium (.-children props)]))
   :td (fn [props] (r/as-element [:td.border.border-gray-400.px-2.py-1 (.-children props)]))})

(s/defn Markdown :- c.schema/Hiccup
  [{:keys [children class]} :- c.schema/MarkdownProps]
  [:> ReactMarkdown/default
   {:remarkPlugins #js [remarkGfm/default]
    :components (clj->js markdown-components)
    :class class}
   children])

(s/defn ^:private tool-icon :- c.schema/Hiccup
  [tool-name :- s/Str]
  (let [icon-class "text-purple-500"]
    (case tool-name
      "Read" [:> lucide/FileText {:size 12 :class icon-class}]
      "Edit" [:> lucide/Pencil {:size 12 :class icon-class}]
      "Write" [:> lucide/FilePlus {:size 12 :class icon-class}]
      "Bash" [:> lucide/Terminal {:size 12 :class icon-class}]
      "Glob" [:> lucide/FolderSearch {:size 12 :class icon-class}]
      "Grep" [:> lucide/FileSearch {:size 12 :class icon-class}]
      "Task" [:> lucide/ListTodo {:size 12 :class icon-class}]
      "WebFetch" [:> lucide/Globe {:size 12 :class icon-class}]
      "WebSearch" [:> lucide/Search {:size 12 :class icon-class}]
      "TodoWrite" [:> lucide/CheckSquare {:size 12 :class icon-class}]
      [:> lucide/Wrench {:size 12 :class icon-class}])))

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

(s/defn ^:private EditDiffView :- c.schema/Hiccup
  [{:keys [file-path structured-patch old-string new-string]} :- {:file-path (s/maybe s/Str)
                                                                  (s/optional-key :structured-patch) s/Any
                                                                  (s/optional-key :old-string) (s/maybe s/Str)
                                                                  (s/optional-key :new-string) (s/maybe s/Str)}]
  (let [render-structured-patch
        (fn []
          (let [all-lines (mapcat (fn [hunk]
                                    (let [old-start (:oldStart hunk)
                                          new-start (:newStart hunk)]
                                      (loop [lines (:lines hunk)
                                             old-line-num old-start
                                             new-line-num new-start
                                             result []]
                                        (if (empty? lines)
                                          result
                                          (let [line (first lines)
                                                prefix (subs line 0 1)
                                                content (subs line 1)]
                                            (case prefix
                                              "-" (recur (rest lines)
                                                         (inc old-line-num)
                                                         new-line-num
                                                         (conj result {:type :removed :old-num old-line-num :new-num nil :content content}))
                                              "+" (recur (rest lines)
                                                         old-line-num
                                                         (inc new-line-num)
                                                         (conj result {:type :added :old-num nil :new-num new-line-num :content content}))
                                              (recur (rest lines)
                                                     (inc old-line-num)
                                                     (inc new-line-num)
                                                     (conj result {:type :context :old-num old-line-num :new-num new-line-num :content content}))))))))
                                  structured-patch)]
            [:div.overflow-x-auto
             [:table.w-full.border-collapse
              [:tbody
               (map-indexed
                (fn [idx {:keys [old-num new-num content] line-type :type}]
                  ^{:key idx}
                  [:tr {:class (c.util/clsx {:bg-red-300.text-white (= :removed line-type)
                                             :bg-green-300.text-white (= :added line-type)
                                             :bg-gray-50.text-gray-700 (= :context line-type)})}
                   [:td.pr-2.text-right.select-none
                    {:class (c.util/clsx {:bg-red-400 (= :removed line-type)
                                          :bg-green-400 (= :added line-type)
                                          :bg-gray-100.text-gray-500 (= :context line-type)})}
                    (or old-num "")]
                   [:td.pr-2.text-right.select-none
                    {:class (c.util/clsx {:bg-red-400 (= :removed line-type)
                                          :bg-green-400 (= :added line-type)
                                          :bg-gray-100.text-gray-500 (= :context line-type)})}
                    (or new-num "")]
                   [:td.w-4.px-1.select-none
                    (case line-type :removed "-" :added "+" :context " ")]
                   [:td.px-2.whitespace-pre content]])
                all-lines)]]]))
        render-old-new
        (fn []
          (let [old-lines (if old-string (str/split-lines old-string) [])
                new-lines (if new-string (str/split-lines new-string) [])]
            [:div.overflow-x-auto
             [:table.w-full.border-collapse
              [:tbody
               (concat
                (map-indexed
                 (fn [idx line]
                   ^{:key (str "old-" idx)}
                   [:tr.bg-red-300.text-white
                    [:td.bg-red-400.pr-2.text-right.select-none (inc idx)]
                    [:td.bg-red-400.pr-2.text-right.select-none]
                    [:td.w-4.px-1.select-none "-"]
                    [:td.px-2.whitespace-pre line]])
                 old-lines)
                (map-indexed
                 (fn [idx line]
                   ^{:key (str "new-" idx)}
                   [:tr.bg-green-300.text-white
                    [:td.bg-green-400.pr-2.text-right.select-none]
                    [:td.bg-green-400.pr-2.text-right.select-none (inc idx)]
                    [:td.w-4.px-1.select-none "+"]
                    [:td.px-2.whitespace-pre line]])
                 new-lines))]]]))]
    [:div.overflow-hidden.rounded-lg.border.border-gray-300.font-mono.text-xs
     (when file-path
       [:div.5.truncate.border-b.border-gray-300.bg-gray-100.px-3.py-1.text-gray-700
        {:title file-path}
        file-path])
     (if (seq structured-patch)
       (render-structured-patch)
       (render-old-new))]))

(s/defn safe-yaml-dump :- s/Str
  [raw-message :- (s/maybe s/Str)]
  (try
    (-> raw-message js/JSON.parse yaml/dump)
    (catch :default _
      raw-message)))

(defn ^:private RawDetailsContent
  [{:keys [message-id]}]
  (let [result (apollo.react/useQuery message-raw-query #js {:variables #js {:id message-id}})
        loading (.-loading result)
        node (some-> result .-data .-node)
        raw-message (some-> ^js node .-rawMessage)]
    (cond
      loading
      [:div.flex.flex-1.items-center.justify-center
       [:> lucide/Loader2 {:size 24 :class "animate-spin text-gray-500"}]]

      (nil? raw-message)
      [:div.flex.flex-1.items-center.justify-center.text-gray-500
       "No data available"]

      :else
      (let [yaml-text (safe-yaml-dump raw-message)]
        [:div.group.relative.flex-1
         [:div.group-hover.absolute.top-2.right-2.flex.gap-1.opacity-0:opacity-100.transition-opacity.z-10
          [CopyButton {:text yaml-text :label "YAML" :class "bg-gray-300 text-gray-900"}]
          [CopyButton {:text raw-message :label "JSON" :class "bg-gray-300 text-gray-900"}]]
         [:pre.text-xs.whitespace-pre-wrap.break-all.bg-gray-25.p-3.rounded.overflow-auto.text-gray-900.h-full.max-h-96 yaml-text]]))))

(s/defn RawDetails :- c.schema/Hiccup
  [{:keys [message-id]} :- {:message-id (s/maybe s/Str)}]
  [:> rac/DialogTrigger
   [:> rac/Button {:class "cursor-pointer p-1 text-gray-500 opacity-0 transition-opacity group-hover:opacity-100 hover:text-gray-700"}
    [:> lucide/Code {:size 16}]]
   [:> rac/ModalOverlay {:isDismissable true
                         :class (fn [^js props]
                                  (c.util/clsx
                                   "fixed inset-0 z-50 flex items-center justify-center bg-black/50"
                                   {"animate-in fade-in duration-200 ease-out" (.-isEntering props)
                                    "animate-out fade-out duration-150 ease-in" (.-isExiting props)}))}
    [:> rac/Modal {:class (fn [^js props]
                            (c.util/clsx
                             "flex max-h-[80vh] w-[32rem] flex-col overflow-hidden rounded-lg border border-gray-300 bg-gray-100 p-4 shadow-lg"
                             {"animate-in zoom-in-95 fade-in duration-200 ease-out" (.-isEntering props)
                              "animate-out zoom-out-95 fade-out duration-150 ease-in" (.-isExiting props)}))}
     [:> rac/Dialog {:class "flex min-h-0 flex-1 flex-col outline-none"}
      [:div.mb-3.flex.shrink-0.items-center.justify-between
       [:> rac/Heading {:slot "title" :class "text-sm font-medium text-gray-800"} "Raw"]
       [:> rac/Button {:slot "close" :class "cursor-pointer rounded p-1 transition-colors hover:bg-gray-300"}
        [:> lucide/X {:size 16 :class "text-gray-600"}]]]
      [:f> RawDetailsContent {:message-id message-id}]]]]])

(s/defn ContentBlock :- c.schema/Hiccup
  [{:keys [block tool-results]} :- c.schema/ContentBlockProps]
  (case (:type block)
    "text"
    [:div.flex.min-w-0.flex-col.gap-2
     [:div.min-w-0.overflow-hidden.text-sm.leading-relaxed.text-gray-800
      [Markdown {:children (:text block)}]]]

    "thinking"
    [:details.group.min-w-0
     [:summary.flex.cursor-pointer.items-center.gap-2.rounded-lg.border.px-3.py-2.text-sm.transition-all.duration-200
      {:class "border-orange-500/40 bg-orange-900/15 hover:border-orange-400/60 hover:bg-orange-900/25"}
      [:div.relative.flex.h-2.w-2.flex-shrink-0
       [:span.absolute.inset-0.animate-ping.rounded-full.bg-orange-500.opacity-50]
       [:span.relative.inline-flex.h-2.w-2.rounded-full.bg-orange-400]]
      [:> lucide/Brain {:size 14 :class "text-orange-500"}]
      [:div.font-medium.text-orange-600 "Extended Thinking"]
      [:> lucide/ChevronRight {:size 14 :class "text-orange-500 transition-transform duration-200 group-open:rotate-90"}]]
     [:div.min-w-0.overflow-hidden.pt-2.pl-2
      [:div.rounded-lg.border.p-4
       {:class "border-orange-700/20 bg-orange-900/5"}
       [:pre.font-mono.text-xs.leading-relaxed.whitespace-pre-wrap.break-all.text-gray-600 (:thinking block)]]]]

    "tool_use"
    (let [result (get tool-results (:id block))
          tool-name (:name block)
          input-map (parse-tool-input (:input block))
          summary (tool-use-summary tool-name input-map)
          is-edit? (= "Edit" tool-name)
          result-content (:content result)
          tool-use-result (:toolUseResult result)
          result-summary (when (and result-content (not is-edit?))
                           (let [s (str result-content)]
                             (if (> (count s) 60) (str (subs s 0 60) "...") s)))]
      [:div.flex.min-w-0.flex-col.gap-2
       [:div.flex.min-w-0.items-center.gap-2.rounded-lg.border.px-3.py-2.text-sm.transition-all.duration-200
        {:class "border-purple-600/40 bg-purple-900/15"}
        [:div.flex.h-6.w-6.flex-shrink-0.items-center.justify-center.rounded-md
         {:class "bg-purple-700/50"}
         [tool-icon tool-name]]
        [:div.flex-shrink-0.font-medium.text-purple-600 tool-name]
        (when (seq summary)
          [:div.min-w-0.truncate.font-mono.text-xs.text-purple-500 {:title summary} summary])]
       [:div.ml-3.border-l-2.pl-4
        {:class "border-purple-700/40"}
        [:div.flex.items-center.gap-2.text-sm
         [:div {:class (c.util/clsx "h-1.5 w-1.5 rounded-full"
                                    {:bg-green-500 result
                                     :bg-gray-400 (not result)})}]
         [:div {:class (c.util/clsx "text-xs font-medium"
                                    {:text-green-600 result
                                     :text-gray-500 (not result)})}
          (if result "Completed" "Pending")]
         (when result-summary
           [:div.truncate.font-mono.text-xs.text-gray-800 {:title (str result-content)} result-summary])
         [:div.flex-1]
         (when (:source-message-id result)
           [RawDetails {:message-id (:source-message-id result)}])]]
       (when (and is-edit? tool-use-result)
         [:div.mt-1.ml-3.border-l-2.pl-4
          {:class "border-purple-800/30"}
          [EditDiffView {:file-path (:filePath tool-use-result)
                         :structured-patch (:structuredPatch tool-use-result)
                         :old-string (:oldString tool-use-result)
                         :new-string (:newString tool-use-result)}]])])

    "tool_result"
    nil

    [:div.rounded-lg.border.p-3
     {:class "border-orange-600/30 bg-orange-900/20"}
     [:div.flex.items-center.gap-2.text-xs.font-medium.text-orange-300
      [:> lucide/AlertCircle {:size 12}]
      (str "Unknown: " (:type block))]]))

(s/defn UserMessageBubble :- c.schema/Hiccup
  [{:keys [content raw-details]} :- {:content c.schema/Hiccup
                                     (s/optional-key :raw-details) (s/maybe c.schema/Hiccup)}]
  [:div.group.animate-fade-in-up
   [:div.flex.items-start.gap-3
    [:div.flex-1.rounded-2xl.border.p-4.shadow-lg
     {:class "border-cyan-700/30 bg-cyan-900/20 shadow-cyan-900/10"}
     content]
    raw-details]])

(s/defn AssistantMessageBubble :- c.schema/Hiccup
  [{:keys [content raw-details]} :- {:content c.schema/Hiccup
                                     (s/optional-key :raw-details) (s/maybe c.schema/Hiccup)}]
  [:div.group.animate-fade-in-up.min-w-0
   [:div.flex.min-w-0.items-start.gap-3
    [:div.min-w-0.flex-1.rounded-2xl.border.bg-gray-50.p-4.shadow-lg
     {:class "border-gray-200/50 shadow-black/5"}
     content]
    raw-details]])


(s/defn AssistantMessage :- c.schema/Hiccup
  [{:keys [message tool-results]} :- c.schema/AssistantMessageProps]
  (let [content-blocks (get-in message [:message :content])]
    [AssistantMessageBubble
     {:content (into [:div.flex.min-w-0.flex-col.gap-2]
                     (map-indexed
                      (fn [idx block]
                        ^{:key idx} [ContentBlock {:block block :tool-results tool-results}])
                      content-blocks))
      :raw-details [RawDetails {:message-id (:id message)}]}]))

(s/defn UserMessage :- c.schema/Hiccup
  [{:keys [message]} :- c.schema/UserMessageProps]
  (let [content-blocks (get-in message [:message :content])
        text-blocks (filter #(= "text" (:type %)) content-blocks)
        has-text? (seq text-blocks)]
    (when has-text?
      [UserMessageBubble
       {:content (into [:div]
                       (map-indexed
                        (fn [idx block]
                          ^{:key idx}
                          (when (= "text" (:type block))
                            [:div.text-sm.leading-relaxed.text-gray-900
                             [Markdown {:children (:text block)}]]))
                        content-blocks))
        :raw-details [RawDetails {:message-id (:id message)}]}])))

(s/defn SystemMessageItem :- c.schema/Hiccup
  [{:keys [message]} :- c.schema/SystemMessageItemProps]
  (let [subtype (:subtype message)
        timestamp (:timestamp message)]
    [:div.group.relative.flex.w-full.items-center.justify-between.rounded-lg.border.px-3.py-2.transition-all.duration-200
     {:class "border-blue-800/20 bg-blue-900/5 hover:border-blue-700/30 hover:bg-blue-900/10"}
     [:div.flex.items-center.gap-3
      [:div.flex.h-6.w-6.items-center.justify-center.rounded-md
       {:class "bg-blue-800/30"}
       [:> lucide/Settings {:size 12 :class "text-blue-400"}]]
      [:div.flex.items-center.gap-2.text-xs
       [:span.font-medium.text-blue-300 "System"]
       (when subtype
         [:span.5.5.rounded.px-1.py-0.font-mono.text-blue-400 {:class "bg-blue-800/30"} subtype])
       (when timestamp
         [:span.text-gray-500 timestamp])]]
     [RawDetails {:message-id (:id message)}]]))

(s/defn SummaryMessageItem :- c.schema/Hiccup
  [{:keys [message]} :- c.schema/SummaryMessageItemProps]
  (let [summary-text (:summary message)
        truncated-summary (if (> (count summary-text) 80)
                            (str (subs summary-text 0 80) "...")
                            summary-text)]
    [:div.group.relative.flex.w-full.items-center.justify-between.rounded-lg.border.px-3.py-2.transition-all.duration-200
     {:class "border-green-800/20 bg-green-900/5 hover:border-green-700/30 hover:bg-green-900/10"}
     [:div.flex.items-center.gap-3
      [:div.flex.h-6.w-6.items-center.justify-center.rounded-md
       {:class "bg-green-800/30"}
       [:> lucide/FileText {:size 12 :class "text-green-400"}]]
      [:div.flex.items-center.gap-2.text-xs
       [:span.font-medium.text-green-300 "Summary"]
       (when (seq summary-text)
         [:span.truncate.text-gray-500 {:title summary-text} truncated-summary])]]
     [RawDetails {:message-id (:id message)}]]))

(s/defn FileHistorySnapshotMessage :- c.schema/Hiccup
  [{:keys [message]} :- c.schema/FileHistorySnapshotMessageProps]
  (let [snapshot (:snapshot message)
        tracked-file-backups (-> (:trackedFileBackups snapshot) js/JSON.parse js/Object.keys js->clj)
        file-count (count tracked-file-backups)
        files-text (str/join "\n" tracked-file-backups)]
    [:div.group.relative.flex.w-full.flex-col.gap-2.rounded-lg.border.px-3.py-2.transition-all.duration-200
     {:class "border-gray-300/30 bg-gray-100/60 hover:border-gray-400/50"}
     [:div.flex.items-center.justify-between
      [:div.flex.items-center.gap-3
       [:div.flex.h-6.w-6.items-center.justify-center.rounded-md
        {:class "bg-gray-300/60"}
        [:> lucide/History {:size 12 :class "text-gray-600"}]]
       [:div.flex.items-center.gap-2.text-xs
        [:span.font-medium.text-gray-700 "FileHistorySnapshot"]
        (when (:isSnapshotUpdate message)
          [:span.5.5.rounded.px-1.py-0.font-mono.text-xs.text-cyan-600 {:class "bg-cyan-800/30"} "update"])
        [:span.text-gray-700 (str file-count " files")]]]
      [RawDetails {:message-id (:id message)}]]
     (when (pos? file-count)
       [:details.group/details
        [:summary.hover.flex.cursor-pointer.items-center.gap-1.text-xs.text-gray-600.transition-colors:text-gray-800
         [:> lucide/ChevronRight {:size 12 :class "transition-transform duration-200 group-open/details:rotate-90"}]
         "Show tracked files"]
        [:pre.mt-2.max-h-48.overflow-auto.rounded-lg.border.bg-gray-50.p-3.font-mono.text-xs.leading-relaxed.whitespace-pre-wrap.break-all.text-gray-600
         {:class "border-gray-200/50"}
         files-text]])]))

(s/defn QueueOperationMessage :- c.schema/Hiccup
  [{:keys [message]} :- c.schema/QueueOperationMessageProps]
  (let [operation (:operation message)
        timestamp (:timestamp message)]
    [:div.group.relative.flex.w-full.items-center.justify-between.rounded-lg.border.px-3.py-2.transition-all.duration-200
     {:class "border-gray-300/20 bg-gray-100/50 hover:border-gray-300/40"}
     [:div.flex.items-center.gap-3
      [:div.flex.h-6.w-6.items-center.justify-center.rounded-md
       {:class "bg-gray-300/50"}
       [:> lucide/ListOrdered {:size 12 :class "text-gray-500"}]]
      [:div.flex.items-center.gap-2.text-xs
       [:span.font-medium.text-gray-600 "Queue"]
       (when operation
         [:span.5.5.rounded.px-1.py-0.font-mono.text-gray-600 {:class "bg-gray-300/50"} operation])
       (when timestamp
         [:span.text-gray-500 timestamp])]]
     [RawDetails {:message-id (:id message)}]]))

(s/defn UnknownMessage :- c.schema/Hiccup
  [{:keys [message]} :- c.schema/UnknownMessageProps]
  [:div.group.relative.flex.w-full.items-center.justify-between.rounded-lg.border.px-3.py-2.transition-all.duration-200
   {:class "border-orange-700/30 bg-orange-900/10 hover:border-orange-600/40"}
   [:div.flex.items-center.gap-3
    [:div.flex.h-6.w-6.items-center.justify-center.rounded-md
     {:class "bg-orange-800/30"}
     [:> lucide/HelpCircle {:size 12 :class "text-orange-400"}]]
    [:div.flex.items-center.gap-2.text-xs
     [:span.font-medium.text-orange-300 "Unknown"]
     [:span.truncate.font-mono {:class "text-orange-400/70"} (:messageId message)]]]
   [RawDetails {:message-id (:id message)}]])

(s/defn BrokenMessage :- c.schema/Hiccup
  [{:keys [message]} :- c.schema/BrokenMessageProps]
  [:div.group.relative.flex.w-full.items-center.justify-between.rounded-lg.border.px-3.py-2.transition-all.duration-200
   {:class "border-red-700/30 bg-red-900/10 hover:border-red-600/40"}
   [:div.flex.items-center.gap-3
    [:div.flex.h-6.w-6.items-center.justify-center.rounded-md
     {:class "bg-red-800/30"}
     [:> lucide/AlertTriangle {:size 12 :class "text-red-400"}]]
    [:div.flex.items-center.gap-2.text-xs
     [:span.font-medium.text-red-300 "Broken"]
     [:span.truncate.font-mono {:class "text-red-400/70"} (:messageId message)]]]
   [RawDetails {:message-id (:id message)}]])

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
      [:div
       [:div.rounded-lg.bg-negative-700.p-3.text-negative-1400
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
                                     :content (.-content block)
                                     :toolUseResult (when-let [^js tur (.-toolUseResult block)]
                                                      {:type (.-type tur)
                                                       :filePath (.-filePath tur)
                                                       :oldString (.-oldString tur)
                                                       :newString (.-newString tur)
                                                       :content (.-content tur)
                                                       :structuredPatch (when-let [sp (.-structuredPatch tur)]
                                                                          (mapv (fn [^js hunk]
                                                                                  {:oldStart (.-oldStart hunk)
                                                                                   :oldLines (.-oldLines hunk)
                                                                                   :newStart (.-newStart hunk)
                                                                                   :newLines (.-newLines hunk)
                                                                                   :lines (js->clj (.-lines hunk))})
                                                                                sp))})})
                                  (.-content msg))})
                nil)}))

(s/defn MessageList :- c.schema/Hiccup
  []
  (let [session-id @selected-session-id
        scroll-container-ref (react/useRef nil)
        session-id-ref (react/useRef session-id)
        has-next-page-ref (react/useRef false)
        async-list (stately/useAsyncList
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
                                    (when (and (not (.-isLoading async-list))
                                               (> (+ scroll-top client-height threshold) scroll-height))
                                      (.loadMore async-list)))))]
    (react/useEffect
     (fn []
       (set! (.-current session-id-ref) session-id)
       (when session-id (.reload async-list))
       js/undefined)
     #js [session-id])
    (react/useEffect
     (fn []
       (check-scroll-and-load)
       js/undefined)
     #js [(count (.-items async-list)) (.-isLoading async-list)])
    (let [messages (vec (for [^js item (.-items async-list)]
                          (js->clj item :keywordize-keys true)))
          has-next-page (.-current has-next-page-ref)
          tool-results (->> messages
                            (filter #(= "UserMessage" (:__typename %)))
                            (mapcat (fn [msg]
                                      (->> (get-in msg [:message :content])
                                           (filter #(= "tool_result" (:type %)))
                                           (map #(assoc % :source-message-id (:id msg))))))
                            (reduce (fn [m block] (assoc m (:tool_use_id block) block)) {}))
          message-count (count messages)
          tool-call-count (->> messages
                               (filter #(= "AssistantMessage" (:__typename %)))
                               (mapcat #(get-in % [:message :content]))
                               (filter #(= "tool_use" (:type %)))
                               count)]
      (cond
        (nil? session-id) [:div.flex.flex-1.items-center.justify-center.text-gray-600 "Select a session to view messages"]
        (and (.-isLoading async-list) (zero? (count messages))) [:div.flex.flex-1.items-center.justify-center.text-gray-600 "Loading messages..."]
        (.-error async-list) [:div.flex.flex-1.items-center.justify-center.text-negative-1100 (str "Error: " (.-error async-list))]
        :else
        [:div.flex.min-w-0.flex-1.flex-col.overflow-hidden
         [:div.mb-4.flex.shrink-0.items-center.gap-4.text-sm.text-gray-700
          [:span (str message-count " messages" (when has-next-page "+"))]
          [:span "•"]
          [:span (str tool-call-count " tool calls")]]
         [:div.flex.min-w-0.flex-1.flex-col.gap-4.overflow-x-hidden.overflow-y-auto.pr-4.pl-2
          {:ref scroll-container-ref
           :on-scroll (fn [_e] (check-scroll-and-load))}
          (if (empty? messages)
            [:div.text-gray-600 "No messages"]
            [:<>
             (for [[idx message] (map-indexed vector messages)]
               ^{:key idx}
               [safe-render-message {:message message
                                     :tool-results tool-results}])
             (when (.-isLoading async-list)
               [:div.flex.items-center.justify-center.py-4.text-gray-600
                [:> lucide/Loader2 {:size 20 :class "mr-2 animate-spin"}]
                "Loading more..."])])]]))))

(s/defn MessagesPanel :- c.schema/Hiccup
  [{:keys [session-title]} :- {:session-title (s/maybe s/Str)}]
  [:div.noise-overlay.flex.h-full.min-w-0.flex-1.flex-col.overflow-hidden.bg-gray-25
   [:div.flex.shrink-0.items-center.justify-between.border-b.p-4.backdrop-blur-sm
    {:class "border-gray-200/50 bg-gray-50/50"}
    [:div.flex.items-center.gap-3
     (when session-title
       [:div.flex.h-8.w-8.items-center.justify-center.rounded-lg
        {:class "bg-purple-900/20"}
        [:> lucide/MessageSquare {:size 16 :class "text-purple-400"}]])
     [:div.flex.flex-col
      [:h2.truncate.text-base.font-semibold.tracking-tight.text-gray-900
       (or session-title "Select a session")]
      (when session-title
        [:span.font-mono.text-xs.text-gray-700 "Session transcript"])]]]
   [:div.flex.min-w-0.flex-1.flex-col.overflow-hidden.p-6
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
    [:div.flex.h-full
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
   [:div.noise-overlay.relative.h-screen.overflow-hidden.bg-gray-25.font-sans.text-sm.text-gray-800
    [:div.tech-gradient-subtle.pointer-events-none.absolute.inset-0.opacity-50]
    [:div.relative.z-10.h-full
     [:f> MainContent]]]])

(defonce root (-> js/document (.getElementById "app") reagent.dom.client/create-root))

(s/defn start :- (s/eq nil)
  {:dev/after-load true}
  []
  (reagent.dom.client/render root [App])
  nil)
