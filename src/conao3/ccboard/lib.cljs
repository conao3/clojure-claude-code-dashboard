(ns conao3.ccboard.lib
  (:require
   [conao3.ccboard.schema :as c.schema]
   [conao3.ccboard.util :as c.util]
   [schema.core :as s]))

(when goog.DEBUG
  (s/set-fn-validation! true))

(s/defn ^:private parse-user-content-block :- c.schema/UserContentBlock
  [block :- s/Any
   stringify-fn :- s/Any]
  (if (string? block)
    {:type "text" :text block}
    {:type (:type block)
     :text (:text block)
     :tool_use_id (:tool_use_id block)
     :content (when-let [c (:content block)] (stringify-fn c))}))

(s/defn ^:private parse-user-content :- [c.schema/UserContentBlock]
  [content :- s/Any
   stringify-fn :- s/Any]
  (if (string? content)
    [{:type "text" :text content}]
    (mapv #(parse-user-content-block % stringify-fn) content)))

(s/defn parse-user-message :- c.schema/UserMessage
  [data :- s/Any
   project-id :- c.schema/ProjectId
   session-id :- c.schema/SessionId
   message-id :- c.schema/MessageId
   line :- s/Str
   stringify-fn :- s/Any]
  {:__typename "UserMessage"
   :id (c.util/encode-id "Message" (str project-id "/" session-id "/" message-id))
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
             :content (parse-user-content (get-in data [:message :content]) stringify-fn)}
   :thinkingMetadata (when-let [tm (:thinkingMetadata data)]
                       {:level (:level tm)
                        :disabled (boolean (:disabled tm))
                        :triggers (or (:triggers tm) [])})})

(s/defn ^:private parse-assistant-content-block :- c.schema/AssistantContentBlock
  [block :- s/Any
   stringify-fn :- s/Any]
  {:type (:type block)
   :text (:text block)
   :thinking (:thinking block)
   :signature (:signature block)
   :id (:id block)
   :name (:name block)
   :input (when-let [input (:input block)] (stringify-fn input))
   :tool_use_id (:tool_use_id block)
   :content (when-let [content (:content block)]
              (if (string? content) content (stringify-fn content)))})

(s/defn parse-assistant-message :- c.schema/AssistantMessage
  [data :- s/Any
   project-id :- c.schema/ProjectId
   session-id :- c.schema/SessionId
   message-id :- c.schema/MessageId
   line :- s/Str
   stringify-fn :- s/Any]
  (let [msg (:message data)
        usage (:usage msg)
        cache-creation (:cache_creation usage)]
    {:__typename "AssistantMessage"
     :id (c.util/encode-id "Message" (str project-id "/" session-id "/" message-id))
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
               :content (mapv #(parse-assistant-content-block % stringify-fn) (:content msg))
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

(s/defn parse-file-history-snapshot-message :- c.schema/FileHistorySnapshotMessage
  [data :- s/Any
   project-id :- c.schema/ProjectId
   session-id :- c.schema/SessionId
   message-id :- c.schema/MessageId
   line :- s/Str
   stringify-fn :- s/Any]
  (let [snapshot (:snapshot data)]
    {:__typename "FileHistorySnapshotMessage"
     :id (c.util/encode-id "Message" (str project-id "/" session-id "/" message-id))
     :projectId project-id
     :sessionId session-id
     :messageId message-id
     :rawMessage line
     :snapshot {:messageId (:messageId snapshot)
                :trackedFileBackups (stringify-fn (:trackedFileBackups snapshot))
                :timestamp (:timestamp snapshot)}
     :isSnapshotUpdate (boolean (:isSnapshotUpdate data))}))

(s/defn parse-queue-operation-message :- c.schema/QueueOperationMessage
  [data :- s/Any
   project-id :- c.schema/ProjectId
   session-id :- c.schema/SessionId
   message-id :- c.schema/MessageId
   line :- s/Str]
  {:__typename "QueueOperationMessage"
   :id (c.util/encode-id "Message" (str project-id "/" session-id "/" message-id))
   :projectId project-id
   :sessionId session-id
   :messageId message-id
   :rawMessage line
   :operation (:operation data)
   :timestamp (:timestamp data)
   :content (:content data)
   :queueSessionId (:sessionId data)})

(s/defn parse-system-message :- c.schema/SystemMessage
  [data :- s/Any
   project-id :- c.schema/ProjectId
   session-id :- c.schema/SessionId
   message-id :- c.schema/MessageId
   line :- s/Str]
  {:__typename "SystemMessage"
   :id (c.util/encode-id "Message" (str project-id "/" session-id "/" message-id))
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

(s/defn parse-summary-message :- c.schema/SummaryMessage
  [data :- s/Any
   project-id :- c.schema/ProjectId
   session-id :- c.schema/SessionId
   message-id :- c.schema/MessageId
   line :- s/Str]
  {:__typename "SummaryMessage"
   :id (c.util/encode-id "Message" (str project-id "/" session-id "/" message-id))
   :projectId project-id
   :sessionId session-id
   :messageId message-id
   :rawMessage line
   :summary (:summary data)
   :leafUuid (:leafUuid data)})

(s/defn parse-unknown-message :- c.schema/UnknownMessage
  [project-id :- c.schema/ProjectId
   session-id :- c.schema/SessionId
   message-id :- c.schema/MessageId
   line :- s/Str]
  {:__typename "UnknownMessage"
   :id (c.util/encode-id "Message" (str project-id "/" session-id "/" message-id))
   :projectId project-id
   :sessionId session-id
   :messageId message-id
   :rawMessage line})

(s/defn parse-broken-message :- c.schema/BrokenMessage
  [project-id :- c.schema/ProjectId
   session-id :- c.schema/SessionId
   idx :- s/Int
   line :- s/Str]
  {:__typename "BrokenMessage"
   :id (c.util/encode-id "Message" (str project-id "/" session-id "/" idx))
   :projectId project-id
   :sessionId session-id
   :messageId (str idx)
   :rawMessage line})

(s/defn parse-message :- c.schema/Message
  [data :- s/Any
   project-id :- c.schema/ProjectId
   session-id :- c.schema/SessionId
   message-id :- c.schema/MessageId
   idx :- s/Int
   line :- s/Str
   stringify-fn :- s/Any]
  (case (:type data)
    "user" (parse-user-message data project-id session-id message-id line stringify-fn)
    "assistant" (parse-assistant-message data project-id session-id message-id line stringify-fn)
    "file-history-snapshot" (parse-file-history-snapshot-message data project-id session-id message-id line stringify-fn)
    "queue-operation" (parse-queue-operation-message data project-id session-id message-id line)
    "system" (parse-system-message data project-id session-id message-id line)
    "summary" (parse-summary-message data project-id session-id message-id line)
    (parse-unknown-message project-id session-id message-id line)))

(s/defn make-project :- c.schema/Project
  [project-path :- s/Str]
  (let [project-id (c.util/path->slug project-path)]
    {:__typename "Project"
     :id (c.util/encode-id "Project" project-id)
     :projectId project-id
     :name project-path}))

(s/defn make-session :- c.schema/Session
  [project-id :- c.schema/ProjectId
   session-id :- c.schema/SessionId
   created-at :- c.schema/Timestamp]
  {:__typename "Session"
   :id (c.util/encode-id "Session" (str project-id "/" session-id))
   :projectId project-id
   :sessionId session-id
   :createdAt created-at})

(s/defn claude-json->projects :- [c.schema/Project]
  [claude-json :- c.schema/ClaudeJson]
  (->> (:projects claude-json)
       (map (fn [[k _v]]
              (make-project (subs (str k) 1))))
       vec))

(s/defn find-project-by-id :- (s/maybe c.schema/Project)
  [projects :- [c.schema/Project]
   project-id :- c.schema/ProjectId]
  (->> projects
       (filter #(= (:projectId %) project-id))
       first))
