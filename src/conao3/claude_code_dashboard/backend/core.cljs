(ns conao3.claude-code-dashboard.backend.core
  (:require
   ["@anthropic-ai/claude-agent-sdk" :as claude]
   ["@apollo/server" :as apollo]
   ["@apollo/server/plugin/disabled" :as apollo.plugin.disabled]
   ["@apollo/server/plugin/landingPage/default" :as apollo.landing]
   ["@as-integrations/express5" :as apollo.express]
   ["cors" :as cors]
   ["express" :as express]
   ["node:fs" :as fs]
   ["node:os" :as os]
   ["node:path" :as path]
   [conao3.claude-code-dashboard.schema :as s.schema]
   [schema.core :as s]
   [shadow.resource :as shadow.resource]))

(defonce server-state (atom nil))

(when goog.DEBUG
  (s/set-fn-validation! true))

(s/defn ^:private encode-id :- s.schema/ID
  [type :- s/Str
   raw-id :- s.schema/RawId]
  (js/btoa (str type ":" raw-id)))

(s/defn ^:private decode-id :- s.schema/DecodedId
  [id :- s.schema/ID]
  (let [[type raw-id] (.split (js/atob id) ":")]
    {:type type :raw-id raw-id}))

(s/defn ^:private read-claude-json :- s.schema/ClaudeJson
  []
  (let [claude-json-path (.join path (.homedir os) ".claude.json")]
    (-> (.readFileSync fs claude-json-path "utf-8")
        (js/JSON.parse)
        (js->clj :keywordize-keys true))))

(s/defn ^:private path->slug :- s/Str
  [p :- s/Str]
  (.replace p (js/RegExp. "/" "g") "-"))

(s/defn ^:private projects-dir :- s/Str
  []
  (.join path (.homedir os) ".claude" "projects"))

(s/defn ^:private list-sessions :- [s.schema/Session]
  [project-id :- s.schema/ProjectId]
  (let [project-dir (.join path (projects-dir) project-id)
        files (try (js->clj (.readdirSync fs project-dir)) (catch :default _ []))]
    (->> files
         (filter #(and (.endsWith % ".jsonl") (not (.startsWith % "agent-"))))
         (map (fn [filename]
                (let [session-id (.replace filename ".jsonl" "")
                      file-path (.join path project-dir filename)
                      stat (.statSync fs file-path)
                      created-at (.toISOString (.-birthtime stat))]
                  {:id (encode-id "Session" (str project-id "/" session-id))
                   :projectId project-id
                   :sessionId session-id
                   :createdAt created-at})))
         (sort-by :createdAt)
         reverse
         vec)))

(s/defn ^:private list-projects :- [s.schema/Project]
  []
  (let [claude-json (read-claude-json)]
    (->> (:projects claude-json)
         (map (fn [[k _v]]
                (let [project-path (subs (str k) 1)]
                  {:id (encode-id "Project" project-path)
                   :projectId (path->slug project-path)
                   :name project-path})))
         vec)))

(s/defn ^:private find-cursor-idx :- (s/maybe s/Int)
  [items :- [s/Any]
   cursor :- (s/maybe s.schema/Cursor)]
  (when cursor
    (->> items
         (keep-indexed (fn [idx item] (when (= (:id item) cursor) idx)))
         first)))

(s/defn ^:private paginate :- s/Any
  [all-items :- [s/Any]
   args :- s/Any]
  (let [^js args args
        first-n (.-first args)
        after-cursor (.-after args)
        last-n (.-last args)
        before-cursor (.-before args)
        all-items (vec all-items)
        after-idx (find-cursor-idx all-items after-cursor)
        before-idx (find-cursor-idx all-items before-cursor)
        filtered-items (cond
                         (and after-idx before-idx)
                         (subvec all-items (inc after-idx) before-idx)

                         after-idx
                         (subvec all-items (inc after-idx))

                         before-idx
                         (subvec all-items 0 before-idx)

                         :else
                         all-items)
        items (cond
                first-n (vec (take first-n filtered-items))
                last-n (vec (take-last last-n filtered-items))
                :else filtered-items)
        has-next-page (boolean (or (some? before-idx)
                                   (and first-n (> (count filtered-items) (count items)))))
        has-previous-page (boolean (or (some? after-idx)
                                       (and last-n (> (count filtered-items) (count items)))))]
    {:edges (map (fn [item] {:cursor (:id item) :node item}) items)
     :pageInfo {:hasNextPage has-next-page
                :hasPreviousPage has-previous-page
                :startCursor (some-> (first items) :id)
                :endCursor (some-> (last items) :id)}}))

(s/defn ^:private content->string :- s/Str
  [content :- s/Any]
  (if (string? content)
    content
    (js/JSON.stringify (clj->js content))))

(s/defn ^:private parse-user-content-block :- s.schema/UserContentBlock
  [block :- s/Any]
  (if (string? block)
    {:type "text" :text block}
    {:type (:type block)
     :text (:text block)
     :tool_use_id (:tool_use_id block)
     :content (when-let [c (:content block)] (content->string c))}))

(s/defn ^:private parse-user-content :- [s.schema/UserContentBlock]
  [content :- s/Any]
  (if (string? content)
    [{:type "text" :text content}]
    (mapv parse-user-content-block content)))

(s/defn ^:private parse-user-message :- s.schema/UserMessage
  [data :- s/Any
   project-id :- s.schema/ProjectId
   session-id :- s.schema/SessionId
   message-id :- s.schema/MessageId
   line :- s/Str]
  {:__typename "UserMessage"
   :id (encode-id "Message" (str project-id "/" session-id "/" message-id))
   :projectId project-id
   :sessionId session-id
   :messageId message-id
   :rawMessage line
   :parentUuid (:parentUuid data)
   :isSidechain (boolean (:isSidechain data))
   :userType (:userType data)
   :cwd (:cwd data)
   :version (:version data)
   :gitBranch (:gitBranch data)
   :timestamp (:timestamp data)
   :message {:role (get-in data [:message :role])
             :content (parse-user-content (get-in data [:message :content]))}
   :thinkingMetadata (when-let [tm (:thinkingMetadata data)]
                       {:level (:level tm)
                        :disabled (boolean (:disabled tm))
                        :triggers (or (:triggers tm) [])})})

(s/defn ^:private parse-assistant-content-block :- s.schema/AssistantContentBlock
  [block :- s/Any]
  {:type (:type block)
   :text (:text block)
   :thinking (:thinking block)
   :signature (:signature block)
   :id (:id block)
   :name (:name block)
   :input (when-let [input (:input block)] (js/JSON.stringify (clj->js input)))
   :tool_use_id (:tool_use_id block)
   :content (when-let [content (:content block)] (content->string content))})

(s/defn ^:private parse-assistant-message :- s.schema/AssistantMessage
  [data :- s/Any
   project-id :- s.schema/ProjectId
   session-id :- s.schema/SessionId
   message-id :- s.schema/MessageId
   line :- s/Str]
  (let [msg (:message data)
        usage (:usage msg)
        cache-creation (:cache_creation usage)]
    {:__typename "AssistantMessage"
     :id (encode-id "Message" (str project-id "/" session-id "/" message-id))
     :projectId project-id
     :sessionId session-id
     :messageId message-id
     :rawMessage line
     :parentUuid (:parentUuid data)
     :isSidechain (boolean (:isSidechain data))
     :userType (:userType data)
     :cwd (:cwd data)
     :version (:version data)
     :gitBranch (:gitBranch data)
     :requestId (:requestId data)
     :timestamp (:timestamp data)
     :message {:model (:model msg)
               :messageId (:id msg)
               :type (:type msg)
               :role (:role msg)
               :content (mapv parse-assistant-content-block (:content msg))
               :stop_reason (:stop_reason msg)
               :stop_sequence (:stop_sequence msg)
               :usage {:input_tokens (:input_tokens usage)
                       :cache_creation_input_tokens (:cache_creation_input_tokens usage)
                       :cache_read_input_tokens (:cache_read_input_tokens usage)
                       :cache_creation (when cache-creation
                                         {:ephemeral_5m_input_tokens (:ephemeral_5m_input_tokens cache-creation)
                                          :ephemeral_1h_input_tokens (:ephemeral_1h_input_tokens cache-creation)})
                       :output_tokens (:output_tokens usage)
                       :service_tier (:service_tier usage)}}}))

(s/defn ^:private parse-file-history-snapshot-message :- s.schema/FileHistorySnapshotMessage
  [data :- s/Any
   project-id :- s.schema/ProjectId
   session-id :- s.schema/SessionId
   message-id :- s.schema/MessageId
   line :- s/Str]
  (let [snapshot (:snapshot data)]
    {:__typename "FileHistorySnapshotMessage"
     :id (encode-id "Message" (str project-id "/" session-id "/" message-id))
     :projectId project-id
     :sessionId session-id
     :messageId message-id
     :rawMessage line
     :snapshot {:messageId (:messageId snapshot)
                :trackedFileBackups (js/JSON.stringify (clj->js (:trackedFileBackups snapshot)))
                :timestamp (:timestamp snapshot)}
     :isSnapshotUpdate (boolean (:isSnapshotUpdate data))}))

(s/defn ^:private parse-queue-operation-message :- s.schema/QueueOperationMessage
  [data :- s/Any
   project-id :- s.schema/ProjectId
   session-id :- s.schema/SessionId
   message-id :- s.schema/MessageId
   line :- s/Str]
  {:__typename "QueueOperationMessage"
   :id (encode-id "Message" (str project-id "/" session-id "/" message-id))
   :projectId project-id
   :sessionId session-id
   :messageId message-id
   :rawMessage line
   :operation (:operation data)
   :timestamp (:timestamp data)
   :content (:content data)
   :queueSessionId (:sessionId data)})

(s/defn ^:private parse-system-message :- s.schema/SystemMessage
  [data :- s/Any
   project-id :- s.schema/ProjectId
   session-id :- s.schema/SessionId
   message-id :- s.schema/MessageId
   line :- s/Str]
  {:__typename "SystemMessage"
   :id (encode-id "Message" (str project-id "/" session-id "/" message-id))
   :projectId project-id
   :sessionId session-id
   :messageId message-id
   :rawMessage line
   :parentUuid (:parentUuid data)
   :logicalParentUuid (:logicalParentUuid data)
   :isSidechain (boolean (:isSidechain data))
   :userType (:userType data)
   :cwd (:cwd data)
   :version (:version data)
   :gitBranch (:gitBranch data)
   :subtype (:subtype data)
   :content (:content data)
   :isMeta (boolean (:isMeta data))
   :timestamp (:timestamp data)
   :level (:level data)
   :compactMetadata (when-let [cm (:compactMetadata data)]
                      {:trigger (:trigger cm)
                       :preTokens (:preTokens cm)})})

(s/defn ^:private parse-summary-message :- s.schema/SummaryMessage
  [data :- s/Any
   project-id :- s.schema/ProjectId
   session-id :- s.schema/SessionId
   message-id :- s.schema/MessageId
   line :- s/Str]
  {:__typename "SummaryMessage"
   :id (encode-id "Message" (str project-id "/" session-id "/" message-id))
   :projectId project-id
   :sessionId session-id
   :messageId message-id
   :rawMessage line
   :summary (:summary data)
   :leafUuid (:leafUuid data)})

(s/defn ^:private parse-message-line :- s.schema/Message
  [project-id :- s.schema/ProjectId
   session-id :- s.schema/SessionId
   idx :- s/Int
   line :- s/Str]
  (try
    (let [data (js->clj (js/JSON.parse line) :keywordize-keys true)
          message-id (or (:uuid data) (:messageId data) (str idx))]
      (case (:type data)
        "user" (parse-user-message data project-id session-id message-id line)
        "assistant" (parse-assistant-message data project-id session-id message-id line)
        "file-history-snapshot" (parse-file-history-snapshot-message data project-id session-id message-id line)
        "queue-operation" (parse-queue-operation-message data project-id session-id message-id line)
        "system" (parse-system-message data project-id session-id message-id line)
        "summary" (parse-summary-message data project-id session-id message-id line)
        {:__typename "UnknownMessage"
         :id (encode-id "Message" (str project-id "/" session-id "/" message-id))
         :projectId project-id
         :sessionId session-id
         :messageId message-id
         :rawMessage line}))
    (catch :default _e
      {:__typename "BrokenMessage"
       :id (encode-id "Message" (str project-id "/" session-id "/" idx))
       :projectId project-id
       :sessionId session-id
       :messageId (str idx)
       :rawMessage line})))

(s/defn ^:private list-messages :- [s.schema/Message]
  [project-id :- s.schema/ProjectId
   session-id :- s.schema/SessionId]
  (let [file-path (.join path (projects-dir) project-id (str session-id ".jsonl"))
        content (try (.readFileSync fs file-path "utf-8") (catch :default _ ""))
        lines (->> (.split content "\n") (filter #(not= % "")))]
    (->> lines
         (map-indexed (fn [idx line] (parse-message-line project-id session-id idx line)))
         vec)))

(s/defn ^:private node-resolver :- (s/maybe s/Any)
  [args :- s/Any]
  (let [{:keys [type raw-id]} (decode-id (.-id args))]
    (case type
      "Project" (let [claude-json (read-claude-json)
                      project-path raw-id]
                  (when (get-in claude-json [:projects (keyword project-path)])
                    {:__typename "Project"
                     :id (.-id args)
                     :projectId (path->slug project-path)
                     :name project-path}))
      "Session" (let [[project-id session-id] (.split raw-id "/")
                      file-path (.join path (projects-dir) project-id (str session-id ".jsonl"))]
                  (when (.existsSync fs file-path)
                    (let [stat (.statSync fs file-path)]
                      {:__typename "Session"
                       :id (.-id args)
                       :projectId project-id
                       :sessionId session-id
                       :createdAt (.toISOString (.-birthtime stat))})))
      "Message" (let [[project-id session-id message-id] (.split raw-id "/")
                      file-path (.join path (projects-dir) project-id (str session-id ".jsonl"))
                      content (try (.readFileSync fs file-path "utf-8") (catch :default _ ""))
                      lines (->> (.split content "\n") (filter #(not= % "")))
                      idx (->> lines
                               (keep-indexed (fn [i l]
                                               (let [data (js->clj (js/JSON.parse l) :keywordize-keys true)]
                                                 (when (= message-id (or (:uuid data) (:messageId data)))
                                                   i))))
                               first)]
                  (when idx
                    (parse-message-line project-id session-id idx (nth lines idx))))
      nil)))

(def resolvers
  {"Query" {"hello" (fn [] "Hello from Apollo Server!")
            "projects" (fn [_ args] (clj->js (paginate (list-projects) args)))
            "node" (fn [_ args] (clj->js (node-resolver args)))}
   "Project" {"sessions"
              (fn [parent args]
                (-> (list-sessions (aget parent "projectId"))
                    (paginate args)
                    clj->js))}
   "Session" {"messages"
              (fn messages-resolver [parent args]
                (-> (list-messages (aget parent "projectId") (aget parent "sessionId"))
                    (paginate args)
                    clj->js))}
   "Message" {"__resolveType" (fn [obj] (aget obj "__typename"))}})

(s/defn start-server :- (s/eq nil)
  []
  (let [type-defs (shadow.resource/inline "schema.graphql")
        api-server (apollo/ApolloServer. (clj->js {:typeDefs type-defs
                                                   :resolvers resolvers
                                                   :plugins [(apollo.plugin.disabled/ApolloServerPluginLandingPageDisabled)]}))
        admin-server (apollo/ApolloServer. (clj->js {:typeDefs type-defs
                                                     :resolvers resolvers
                                                     :plugins [(apollo.landing/ApolloServerPluginLandingPageLocalDefault)]}))
        app (express)]
    (-> (js/Promise.all (clj->js [(.start api-server) (when goog.DEBUG (.start admin-server))]))
        (.then (fn []
                 (.use app "/api/graphql" (cors) (express/json) (apollo.express/expressMiddleware api-server))
                 (when goog.DEBUG
                   (.use app "/admin/apollo" (cors) (express/json) (apollo.express/expressMiddleware admin-server)))
                 (let [server (.listen app 4000)]
                   (reset! server-state {:server server :api-server api-server :admin-server admin-server})
                   (println "Server ready at http://localhost:4000/api/graphql")
                   (when goog.DEBUG
                     (println "Apollo Sandbox at http://localhost:4000/admin/apollo"))
                   nil)))))
  nil)

(s/defn stop-server :- (s/eq nil)
  []
  (when-let [{:keys [server api-server admin-server]} @server-state]
    (-> (js/Promise.all (clj->js [(.stop api-server) (when admin-server (.stop admin-server))]))
        (.then (fn []
                 (.close server)
                 (reset! server-state nil)
                 (println "Server stopped")
                 nil))))
  nil)

(s/defn reload :- (s/eq nil)
  {:dev/after-load true}
  []
  (println "Reloading...")
  (-> (stop-server)
      (.then start-server))
  nil)

(s/defn main :- (s/eq nil)
  [& _args :- [s/Any]]
  (start-server)
  nil)
