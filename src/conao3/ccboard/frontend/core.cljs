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
  [{:keys [project is-selected on-press]} :- c.schema/ProjectItemProps]
  [:> rac/ToggleButton
   {:class (c.util/clsx "flex items-center gap-2 rounded px-3 py-2 text-left aria-pressed:bg-gray-200 not-aria-pressed:hover:bg-gray-100")
    :onPress on-press
    :isSelected is-selected}
   [:> lucide/Folder {:size 16 :class "flex-shrink-0 text-gray-600"}]
   [:div.flex-1.truncate.text-gray-900 (c.lib/project-basename (:name project))]])

(s/defn ^:private parse-project-node :- c.schema/Project
  [node :- s/Any]
  (let [^js node node]
    {:typename "Project"
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
      (and (.-isLoading async-list) (zero? (count (.-items async-list)))) [:div.text-gray-600 "Loading..."]
      (.-error async-list) [:div.text-negative-1100 (str "Error: " (.-error async-list))]
      :else
      [:div.flex.flex-col.gap-1
       {:ref scroll-container-ref
        :on-scroll (fn [_e] (check-scroll-and-load))}
       (for [^js project_ (.-items async-list)
             :let [project (js->clj project_ :keywordize-keys true)]]
         ^{:key (:id project)}
         [ProjectItem {:project project
                       :is-selected (= (:id project) current-project-id)
                       :on-press #(on-select-project project)}])
       (when (.-isLoading async-list)
         [:div.flex.items-center.justify-center.py-2.text-gray-600
          [:> lucide/Loader2 {:size 14 :class "animate-spin"}]])])))


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
   {:class (c.util/clsx "w-full text-left px-3 py-2 outline-none transition-all rounded"
                        {:bg-gray-50 active
                         :shadow-sm active
                         :hover:bg-gray-100 (not active)})
    :onPress on-click}
   [:div.flex.items-center.gap-2
    [:span.h-2.w-2.flex-shrink-0.rounded-full
     {:class (c.util/clsx {:bg-positive-900 active
                           :bg-gray-400 (not active)})}]
    [:span.flex-1.truncate.font-medium.text-gray-900
     (subs (:sessionId session) 0 (min 24 (count (:sessionId session))))
     "..."]
    [:span.text-xs.text-gray-600 (format-relative-date (:createdAt session))]]])

(s/defn ^:private parse-session-node :- c.schema/Session
  [node :- s/Any]
  (let [^js node node]
    {:typename "Session"
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
      (nil? project-id) [:div.text-gray-600 "Select a project"]
      (and (.-isLoading async-list) (zero? (count (.-items async-list)))) [:div.text-gray-600 "Loading sessions..."]
      (.-error async-list) [:div.text-negative-1100 (str "Error: " (.-error async-list))]
      :else
      [:div.flex-1.overflow-x-hidden.overflow-y-auto.pb-2
       {:ref scroll-container-ref}
       (for [^js session (.-items async-list)]
         (let [s {:typename "Session" :id (.-id session) :projectId (.-projectId session) :sessionId (.-sessionId session) :createdAt (.-createdAt session)}]
           ^{:key (:id s)}
           [SessionItem {:session s
                         :active (= (:id s) current-session-id)
                         :on-click #(on-select-session s)}]))
       (when (.-isLoading async-list)
         [:div.flex.items-center.justify-center.py-4.text-gray-600
          [:> lucide/Loader2 {:size 16 :class "animate-spin"}]])])))

(s/defn Sidebar :- c.schema/Hiccup
  [{:keys [on-select-project on-select-session project]} :- c.schema/SidebarProps]
  (let [l-padding "pl-3"]
    [:div.flex.h-full.w-80.flex-col.overflow-hidden.border-r.border-gray-200.bg-background-layer-2
     [:div.flex.items-center.gap-2.p-3.py-2
      [:div.flex.h-8.w-8.flex-shrink-0.items-center.justify-center.rounded-full.bg-notice-300
       [:> lucide/Sparkles {:size 16 :class "text-notice-1200"}]]
      [:span.font-semibold.text-gray-900 "CCboard"]]
     [:div.flex.flex-1.flex-col.gap-2.overflow-hidden.border-t.border-gray-200
      [:div.pt-3.font-medium.text-gray-600
       {:class (c.util/clsx l-padding)}
       "Projects"]
      [:div.flex-1.overflow-auto.pr-3
       {:class (c.util/clsx l-padding)}
       [:f> ProjectsList {:on-select-project on-select-project}]]]
     [:div.flex.flex-1.flex-col.gap-2.overflow-hidden
      [:div.flex.shrink-0.items-center.justify-between.border-t.border-gray-200.px-4.pt-3
       {:class (c.util/clsx l-padding)}
       [:span.font-medium.text-gray-600 "Sessions"]
       (when project
         [:span.max-w-32.truncate.text-xs.text-gray-500 (c.lib/project-basename (:name project))])]
      [:div.flex-1.overflow-auto.py-1
       {:class (c.util/clsx l-padding)}
       (if project
         [:f> SessionsList {:project-id (:id project) :on-select-session on-select-session}]
         [:p.text-gray-600 "Select a project"])]]]))

(s/defn CopyButton :- c.schema/Hiccup
  []
  (let [copied? (r/atom false)]
    (s/fn [{:keys [text label class]} :- c.schema/CopyButtonProps]
      [:> rac/Button
       {:class (c.util/clsx "px-2 py-1 rounded bg-gray-200 text-gray-700 group-hover:opacity-70 hover:opacity-100 pressed:opacity-100 flex items-center gap-1 text-xs" class)
        :onPress (fn []
                   (-> js/navigator .-clipboard (.writeText text))
                   (reset! copied? true)
                   (js/setTimeout #(reset! copied? false) 1000))}
       (if @copied?
         [:<> [:> lucide/Check {:size 12}] "Copied!"]
         [:<> [:> lucide/Copy {:size 12}] (or label "Copy")])])))

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
             (r/as-element [:code.5.rounded.bg-gray-100.px-1.py-0.font-mono (.-children props)])
             (r/as-element [:code.font-mono (.-children props)])))
   :pre (fn [props] (r/as-element [:pre.bg-gray-100.p-3.rounded.overflow-x-auto.mb-2 (.-children props)]))
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
  (let [icon-class "text-gray-600"]
    (case tool-name
      "Read" [:> lucide/FileText {:size 14 :class icon-class}]
      "Edit" [:> lucide/Pencil {:size 14 :class icon-class}]
      "Write" [:> lucide/FilePlus {:size 14 :class icon-class}]
      "Bash" [:> lucide/Terminal {:size 14 :class icon-class}]
      "Glob" [:> lucide/Search {:size 14 :class icon-class}]
      "Grep" [:> lucide/Search {:size 14 :class icon-class}]
      "Task" [:> lucide/ListTodo {:size 14 :class icon-class}]
      "WebFetch" [:> lucide/Globe {:size 14 :class icon-class}]
      "WebSearch" [:> lucide/Search {:size 14 :class icon-class}]
      [:> lucide/Wrench {:size 14 :class icon-class}])))

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
    [:div.overflow-hidden.rounded.border.border-gray-300.font-mono.text-xs
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
         [:pre.text-xs.whitespace-pre-wrap.break-all.bg-background-base.p-3.rounded.overflow-auto.text-gray-900.h-full.max-h-96.resize yaml-text]]))))

(s/defn RawDetails :- c.schema/Hiccup
  [{:keys [message-id]} :- {:message-id (s/maybe s/Str)}]
  [:> rac/DialogTrigger
   [:> rac/Button {:class "cursor-pointer p-1 text-gray-500 opacity-0 transition-opacity group-hover:opacity-100 hover:text-gray-700"}
    [:> lucide/Code {:size 16}]]
   [:> rac/ModalOverlay {:isDismissable true
                         :class (fn [^js props]
                                  (str "fixed inset-0 z-50 bg-black/50 flex items-center justify-center "
                                       (when (.-isEntering props) "animate-in fade-in duration-200 ease-out ")
                                       (when (.-isExiting props) "animate-out fade-out duration-150 ease-in")))}
    [:> rac/Modal {:class (fn [^js props]
                            (str "bg-gray-100 border border-gray-300 rounded shadow p-4 max-h-[80vh] overflow-hidden flex flex-col "
                                 (when (.-isEntering props) "animate-in zoom-in-95 fade-in duration-200 ease-out ")
                                 (when (.-isExiting props) "animate-out zoom-out-95 fade-out duration-150 ease-in")))}
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
    [:div.flex.min-w-0.flex-col.gap-1
     [:div.flex.items-center.gap-2
      [:div.h-2.w-2.flex-shrink-0.rounded-full.bg-positive-900]
      [:> lucide/MessageSquare {:size 14 :class "text-gray-600"}]
      [:div.font-medium.text-gray-900 "Message"]]
     [:div.min-w-0.overflow-hidden.pl-6.text-gray-900
      [Markdown {:children (:text block)}]]]

    "thinking"
    [:details.min-w-0
     [:summary.flex.cursor-pointer.items-center.gap-2.text-gray-600
      [:div.h-2.w-2.flex-shrink-0.rounded-full.bg-notice-700]
      [:> lucide/Brain {:size 14}]
      [:div "Thinking..."]]
     [:div.min-w-0.overflow-hidden.pt-2.pl-6
      [:div.rounded.border.border-notice-400.bg-notice-200.p-3
       [:pre.text-xs.whitespace-pre-wrap.break-all.text-gray-700 (:thinking block)]]]]

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
      [:div.flex.min-w-0.flex-col.gap-1
       [:div.flex.min-w-0.items-baseline.gap-2
        [:div.h-2.w-2.flex-shrink-0.self-center.rounded-full.bg-positive-900]
        [:div.flex.flex-shrink-0.items-center.self-center [tool-icon tool-name]]
        [:div.flex-shrink-0.font-medium.text-gray-900 tool-name]
        (when (seq summary)
          [:div.min-w-0.truncate.font-mono.text-xs.text-gray-600 {:title summary} summary])]
       [:div.pl-4
        [:div.group.flex.items-center.gap-2.opacity-60
         [:> lucide/CornerDownRight {:size 14 :class "text-gray-600"}]
         [:div.font-medium {:class (c.util/clsx {:text-gray-900 result
                                                 :text-gray-400 (not result)})} "Result"]
         (if result
           (when result-summary
             [:div.truncate.font-mono.text-xs.text-gray-600 {:title (str result-content)} result-summary])
           [:div.text-xs.text-gray-400 "not found"])
         [:div.flex-1]
         (when (:source-message-id result)
           [RawDetails {:message-id (:source-message-id result)}])]]
       (when (and is-edit? tool-use-result)
         [:div.mt-1.pl-4
          [EditDiffView {:file-path (:filePath tool-use-result)
                         :structured-patch (:structuredPatch tool-use-result)
                         :old-string (:oldString tool-use-result)
                         :new-string (:newString tool-use-result)}]])])

    "tool_result"
    nil

    [:div.rounded.bg-notice-400.p-3.text-notice-1200
     [:div.text-xs.font-medium (str "Unknown: " (:type block))]]))

(s/defn UserMessageBubble :- c.schema/Hiccup
  [{:keys [content raw-details]} :- {:content c.schema/Hiccup
                                     (s/optional-key :raw-details) (s/maybe c.schema/Hiccup)}]
  [:div.group
   [:div.flex.items-start.gap-2
    [:div.flex-1.rounded.bg-accent-200.p-4
     content]
    raw-details]])

(s/defn AssistantMessageBubble :- c.schema/Hiccup
  [{:keys [content raw-details]} :- {:content c.schema/Hiccup
                                     (s/optional-key :raw-details) (s/maybe c.schema/Hiccup)}]
  [:div.group.min-w-0
   [:div.flex.min-w-0.items-start.gap-2
    [:div.min-w-0.flex-1 content]
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
                            [:div.leading-relaxed.text-gray-900
                             [Markdown {:children (:text block)}]]))
                        content-blocks))
        :raw-details [RawDetails {:message-id (:id message)}]}])))

(s/defn SystemMessageItem :- c.schema/Hiccup
  [{:keys [message]} :- c.schema/SystemMessageItemProps]
  (let [subtype (:subtype message)
        timestamp (:timestamp message)]
    [:div.group.relative.flex.w-full.justify-between
     [:div.flex.flex-col.gap-1.opacity-60
      [:div.flex.items-baseline.gap-2
       [:div.h-2.w-2.flex-shrink-0.self-center.rounded-full.bg-informative-900]
       [:div.flex.flex-shrink-0.items-center.self-center [:> lucide/Settings {:size 14 :class "text-gray-600"}]]
       [:div.flex-shrink-0.font-medium.text-gray-900 "System"]
       (when subtype
         [:div.flex-shrink-0.text-xs.text-gray-600 subtype])
       (when timestamp
         [:div.truncate.text-xs.text-gray-500 timestamp])]]
     [RawDetails {:message-id (:id message)}]]))

(s/defn SummaryMessageItem :- c.schema/Hiccup
  [{:keys [message]} :- c.schema/SummaryMessageItemProps]
  (let [summary-text (:summary message)
        truncated-summary (if (> (count summary-text) 80)
                            (str (subs summary-text 0 80) "...")
                            summary-text)]
    [:div.group.relative.flex.w-full.justify-between
     [:div.flex.flex-col.gap-1.opacity-60
      [:div.flex.items-baseline.gap-2
       [:div.h-2.w-2.flex-shrink-0.self-center.rounded-full.bg-positive-900]
       [:div.flex.flex-shrink-0.items-center.self-center [:> lucide/FileText {:size 14 :class "text-gray-600"}]]
       [:div.flex-shrink-0.font-medium.text-gray-900 "Summary"]
       (when (seq summary-text)
         [:div.truncate.text-xs.text-gray-600 {:title summary-text} truncated-summary])]]
     [RawDetails {:message-id (:id message)}]]))

(s/defn FileHistorySnapshotMessage :- c.schema/Hiccup
  [{:keys [message]} :- c.schema/FileHistorySnapshotMessageProps]
  (let [snapshot (:snapshot message)
        tracked-file-backups (-> (:trackedFileBackups snapshot) js/JSON.parse js/Object.keys js->clj)
        file-count (count tracked-file-backups)
        files-text (str/join "\n" tracked-file-backups)]
    [:div.group.relative.flex.w-full.justify-between
     [:div.flex.flex-col.gap-1.opacity-60
      [:div.flex.items-baseline.gap-2
       [:div.h-2.w-2.flex-shrink-0.self-center.rounded-full.bg-positive-900]
       [:div.flex.flex-shrink-0.items-center.self-center [:> lucide/History {:size 14 :class "text-gray-600"}]]
       [:div.flex-shrink-0.font-medium.text-gray-900 "FileHistorySnapshot"]
       (when (:isSnapshotUpdate message)
         [:div.flex-shrink-0.rounded.bg-gray-600.px-1.text-xs.text-white "update"])
       [:div.truncate.text-xs.text-gray-600 (str file-count " tracked files")]]
      (when (pos? file-count)
        [:div.pl-6.text-xs.text-gray-600
         [:details
          [:summary.flex.cursor-pointer.items-center.gap-1
           "└ Show files"]
          [:pre.p-2.bg-gray-100.rounded.whitespace-pre-wrap.break-all.font-mono.max-h-64.overflow-auto
           files-text]]])]
     [RawDetails {:message-id (:id message)}]]))

(s/defn QueueOperationMessage :- c.schema/Hiccup
  [{:keys [message]} :- c.schema/QueueOperationMessageProps]
  (let [operation (:operation message)
        timestamp (:timestamp message)]
    [:div.group.relative.flex.w-full.justify-between
     [:div.flex.flex-col.gap-1.opacity-60
      [:div.flex.items-baseline.gap-2
       [:div.h-2.w-2.flex-shrink-0.self-center.rounded-full.bg-gray-500]
       [:div.flex.flex-shrink-0.items-center.self-center [:> lucide/ListOrdered {:size 14 :class "text-gray-600"}]]
       [:div.flex-shrink-0.font-medium.text-gray-900 "Queue"]
       (when operation
         [:div.flex-shrink-0.text-xs.text-gray-600 operation])
       (when timestamp
         [:div.truncate.text-xs.text-gray-500 timestamp])]]
     [RawDetails {:message-id (:id message)}]]))

(s/defn UnknownMessage :- c.schema/Hiccup
  [{:keys [message]} :- c.schema/UnknownMessageProps]
  [:div.group.relative.flex.w-full.justify-between
   [:div.flex.flex-col.gap-1.opacity-60
    [:div.flex.items-baseline.gap-2
     [:div.h-2.w-2.flex-shrink-0.self-center.rounded-full.bg-notice-700]
     [:div.flex.flex-shrink-0.items-center.self-center [:> lucide/HelpCircle {:size 14 :class "text-gray-600"}]]
     [:div.flex-shrink-0.font-medium.text-gray-900 "Unknown"]
     [:div.truncate.text-xs.text-gray-600 (:messageId message)]]]
   [RawDetails {:message-id (:id message)}]])

(s/defn BrokenMessage :- c.schema/Hiccup
  [{:keys [message]} :- c.schema/BrokenMessageProps]
  [:div.group.relative.flex.w-full.justify-between
   [:div.flex.flex-col.gap-1
    [:div.flex.items-baseline.gap-2
     [:div.h-2.w-2.flex-shrink-0.self-center.rounded-full.bg-negative-900]
     [:div.flex.flex-shrink-0.items-center.self-center [:> lucide/AlertTriangle {:size 14 :class "text-negative-1100"}]]
     [:div.flex-shrink-0.font-medium.text-negative-1100 "Broken"]
     [:div.truncate.text-xs.text-gray-600 (:messageId message)]]]
   [RawDetails {:message-id (:id message)}]])

(s/defn safe-render-message :- (s/maybe c.schema/Hiccup)
  [{:keys [message tool-results]} :- c.schema/SafeRenderMessageProps]
  (try
    (case (:typename message)
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
       [:div.rounded.bg-negative-700.p-3.text-negative-1400
        [:div.flex.items-center.gap-2.text-xs
         [:> lucide/AlertTriangle {:size 12}]
         [:span (str "Render error: " (.-message e))]]]])))

(s/defn ^:private parse-message-node :- c.schema/FrontendMessage
  [node :- s/Any]
  (let [^js node node
        ^js snapshot (.-snapshot node)]
    {:typename (.-__typename node)
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
     :message (case (.-__typename node)
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
    (let [messages (for [^js item (.-items async-list)]
                     (js->clj item :keywordize-keys true))
          has-next-page (.-current has-next-page-ref)
          tool-results (->> messages
                            (filter #(= "UserMessage" (:typename %)))
                            (mapcat (fn [msg]
                                      (->> (get-in msg [:message :content])
                                           (filter #(= "tool_result" (:type %)))
                                           (map #(assoc % :source-message-id (:id msg))))))
                            (reduce (fn [m block] (assoc m (:tool_use_id block) block)) {}))
          message-count (count messages)
          tool-call-count (->> messages
                               (filter #(= "AssistantMessage" (:typename %)))
                               (mapcat #(get-in % [:message :content]))
                               (filter #(= "tool_use" (:type %)))
                               count)]
      (cond
        (nil? session-id) [:div.flex.flex-1.items-center.justify-center.text-gray-600 "Select a session to view messages"]
        (and (.-isLoading async-list) (zero? (count messages))) [:div.flex.flex-1.items-center.justify-center.text-gray-600 "Loading messages..."]
        (.-error async-list) [:div.flex.flex-1.items-center.justify-center.text-negative-1100 (str "Error: " (.-error async-list))]
        :else
        [:div.flex.min-w-0.flex-1.flex-col.overflow-hidden
         [:div.flex.shrink-0.items-center.gap-4.pb-4.text-gray-600
          [:span (str message-count " messages" (when has-next-page "+"))]
          [:span "•"]
          [:span (str tool-call-count " tool calls")]]
         [:div.flex.min-w-0.flex-1.flex-col.gap-4.overflow-auto.pr-4.pl-2
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
  [:div.flex.h-full.min-w-0.flex-1.flex-col.overflow-hidden.bg-background-base
   [:div.flex.shrink-0.items-center.justify-between.border-b.border-gray-200.p-3
    [:div.flex.items-center.gap-2
     [:h2.truncate.text-base.font-medium.text-gray-900 (or session-title "Select a session")]]]
   [:div.flex.min-w-0.flex-1.flex-col.overflow-hidden.pt-6.pl-6
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
   [:div.h-screen.bg-background-layer-2.text-sm.text-neutral-content.scheme-dark
    [:f> MainContent]]])

(defonce root (-> js/document (.getElementById "app") reagent.dom.client/create-root))

(s/defn start :- (s/eq nil)
  {:dev/after-load true}
  []
  (reagent.dom.client/render root [App])
  nil)
