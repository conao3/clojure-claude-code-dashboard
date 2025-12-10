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
   [shadow.resource :as shadow.resource]))

(defonce server-state (atom nil))

(defn- encode-id [type raw-id]
  (js/btoa (str type ":" raw-id)))

(defn- decode-id [id]
  (let [[type raw-id] (.split (js/atob id) ":")]
    {:type type :raw-id raw-id}))

(defn- read-claude-json []
  (let [claude-json-path (.join path (.homedir os) ".claude.json")]
    (-> (.readFileSync fs claude-json-path "utf-8")
        (js/JSON.parse)
        (js->clj :keywordize-keys true))))

(defn- path->slug [p]
  (.replace p (js/RegExp. "/" "g") "-"))

(defn- projects-dir []
  (.join path (.homedir os) ".claude" "projects"))

(defn- list-sessions [project-id]
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
         reverse)))

(defn- list-projects []
  (let [claude-json (read-claude-json)]
    (->> (:projects claude-json)
         (map (fn [[k _v]]
                (let [project-path (subs (str k) 1)]
                  {:id (encode-id "Project" project-path)
                   :projectId (path->slug project-path)
                   :name project-path}))))))

(defn- sessions-resolver [parent]
  (let [project-id (aget parent "projectId")
        sessions (list-sessions project-id)]
    #js {:edges (clj->js (map (fn [s] {:cursor (:id s) :node s}) sessions))
         :pageInfo #js {:hasNextPage false
                        :hasPreviousPage false
                        :startCursor (some-> (first sessions) :id)
                        :endCursor (some-> (last sessions) :id)}}))

(defn- message-type->typename [type]
  (case type
    "assistant" "AssistantMessage"
    "user" "UserMessage"
    "UnknownMessage"))

(defn- content->string [content]
  (if (string? content)
    content
    (js/JSON.stringify (clj->js content))))

(defn- parse-user-content-block [block]
  (if (string? block)
    {:type "text" :text block}
    {:type (:type block)
     :text (:text block)
     :tool_use_id (:tool_use_id block)
     :content (when-let [c (:content block)] (content->string c))}))

(defn- parse-user-content [content]
  (if (string? content)
    [{:type "text" :text content}]
    (mapv parse-user-content-block content)))

(defn- parse-user-message [data project-id session-id message-id line]
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

(defn- parse-assistant-content-block [block]
  {:type (:type block)
   :text (:text block)
   :thinking (:thinking block)
   :signature (:signature block)
   :id (:id block)
   :name (:name block)
   :input (when-let [input (:input block)] (js/JSON.stringify (clj->js input)))
   :tool_use_id (:tool_use_id block)
   :content (when-let [content (:content block)] (content->string content))})

(defn- parse-assistant-message [data project-id session-id message-id line]
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
               :content (map parse-assistant-content-block (:content msg))
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

(defn- parse-file-history-snapshot-message [data project-id session-id message-id line]
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

(defn- parse-queue-operation-message [data project-id session-id message-id line]
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

(defn- parse-system-message [data project-id session-id message-id line]
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

(defn- parse-summary-message [data project-id session-id message-id line]
  {:__typename "SummaryMessage"
   :id (encode-id "Message" (str project-id "/" session-id "/" message-id))
   :projectId project-id
   :sessionId session-id
   :messageId message-id
   :rawMessage line
   :summary (:summary data)
   :leafUuid (:leafUuid data)})

(defn- parse-message-line [project-id session-id idx line]
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

(defn- list-messages [project-id session-id]
  (let [file-path (.join path (projects-dir) project-id (str session-id ".jsonl"))
        content (try (.readFileSync fs file-path "utf-8") (catch :default _ ""))
        lines (->> (.split content "\n") (filter #(not= % "")))]
    (->> lines
         (map-indexed (fn [idx line] (parse-message-line project-id session-id idx line))))))

(defn- find-cursor-idx [messages cursor]
  (when cursor
    (->> messages
         (keep-indexed (fn [idx m] (when (= (:id m) cursor) idx)))
         first)))

(defn- messages-resolver [parent ^js args]
  (let [project-id (aget parent "projectId")
        session-id (aget parent "sessionId")
        first-n (.-first args)
        after-cursor (.-after args)
        last-n (.-last args)
        before-cursor (.-before args)
        all-messages (vec (list-messages project-id session-id))
        after-idx (find-cursor-idx all-messages after-cursor)
        before-idx (find-cursor-idx all-messages before-cursor)
        filtered-messages (cond
                            (and after-idx before-idx)
                            (subvec all-messages (inc after-idx) before-idx)

                            after-idx
                            (subvec all-messages (inc after-idx))

                            before-idx
                            (subvec all-messages 0 before-idx)

                            :else
                            all-messages)
        messages (cond
                   first-n (vec (take first-n filtered-messages))
                   last-n (vec (take-last last-n filtered-messages))
                   :else filtered-messages)
        has-next-page (boolean (or (some? before-idx)
                                   (and first-n (> (count filtered-messages) (count messages)))))
        has-previous-page (boolean (or (some? after-idx)
                                       (and last-n (> (count filtered-messages) (count messages)))))]
    #js {:edges (clj->js (map (fn [m] {:cursor (:id m) :node m}) messages))
         :pageInfo #js {:hasNextPage has-next-page
                        :hasPreviousPage has-previous-page
                        :startCursor (some-> (first messages) :id)
                        :endCursor (some-> (last messages) :id)}}))

(def resolvers
  (clj->js
   {"Query" {"hello" (fn [] "Hello from Apollo Server!")
             "projects" (fn []
                          (let [projects (list-projects)]
                            #js {:edges (clj->js (map (fn [p] {:cursor (:id p) :node p}) projects))
                                 :pageInfo #js {:hasNextPage false
                                                :hasPreviousPage false
                                                :startCursor (some-> (first projects) :id)
                                                :endCursor (some-> (last projects) :id)}}))
             "node" (fn [_ args]
                      (let [{:keys [type raw-id]} (decode-id (.-id args))]
                        (case type
                          "Project" (let [claude-json (read-claude-json)
                                          project-path raw-id]
                                      (when (get-in claude-json [:projects (keyword project-path)])
                                        #js {:__typename "Project"
                                             :id (.-id args)
                                             :projectId (path->slug project-path)
                                             :name project-path}))
                          "Session" (let [[project-id session-id] (.split raw-id "/")
                                          file-path (.join path (projects-dir) project-id (str session-id ".jsonl"))]
                                      (when (.existsSync fs file-path)
                                        (let [stat (.statSync fs file-path)]
                                          #js {:__typename "Session"
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
                                        (clj->js (parse-message-line project-id session-id idx (nth lines idx)))))
                          nil)))}
    "Project" {"sessions" sessions-resolver}
    "Session" {"messages" messages-resolver}
    "Message" {"__resolveType" (fn [obj] (aget obj "__typename"))}}))

(defn start-server []
  (let [type-defs (shadow.resource/inline "schema.graphql")
        api-server (apollo/ApolloServer. #js {:typeDefs type-defs
                                              :resolvers resolvers
                                              :plugins #js [(apollo.plugin.disabled/ApolloServerPluginLandingPageDisabled)]})
        admin-server (apollo/ApolloServer. #js {:typeDefs type-defs
                                                :resolvers resolvers
                                                :plugins #js [(apollo.landing/ApolloServerPluginLandingPageLocalDefault)]})
        app (express)]
    (-> (js/Promise.all #js [(.start api-server) (when goog.DEBUG (.start admin-server))])
        (.then (fn []
                 (.use app "/api/graphql" (cors) (express/json) (apollo.express/expressMiddleware api-server))
                 (when goog.DEBUG
                   (.use app "/admin/apollo" (cors) (express/json) (apollo.express/expressMiddleware admin-server)))
                 (let [server (.listen app 4000)]
                   (reset! server-state {:server server :api-server api-server :admin-server admin-server})
                   (println "Server ready at http://localhost:4000/api/graphql")
                   (when goog.DEBUG
                     (println "Apollo Sandbox at http://localhost:4000/admin/apollo"))))))))

(defn stop-server []
  (when-let [{:keys [server api-server admin-server]} @server-state]
    (-> (js/Promise.all #js [(.stop api-server) (when admin-server (.stop admin-server))])
        (.then (fn []
                 (.close server)
                 (reset! server-state nil)
                 (println "Server stopped"))))))

(defn ^:dev/after-load reload []
  (println "Reloading...")
  (-> (stop-server)
      (.then start-server)))

(defn main [& _args]
  (start-server))
