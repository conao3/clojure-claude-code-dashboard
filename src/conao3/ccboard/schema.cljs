(ns conao3.ccboard.schema
  (:require
   [schema.core :as s]))

(def ID s/Str)
(def ProjectId s/Str)
(def SessionId s/Str)
(def MessageId s/Str)
(def Timestamp s/Str)
(def Hiccup s/Any)

(def UrlPath
  {(s/optional-key :project-id) (s/maybe s/Str)
   (s/optional-key :session-id) (s/maybe s/Str)})

(def Project
  {:typename s/Str
   :id ID
   :projectId ProjectId
   :name s/Str
   (s/optional-key :hasSessions) s/Bool})

(def Session
  {:typename s/Str
   :id ID
   :projectId ProjectId
   :sessionId SessionId
   :createdAt Timestamp})

(def ThinkingMetadata
  {:level (s/maybe s/Str)
   :disabled s/Bool
   :triggers [s/Str]})

(def StructuredPatchHunk
  {:oldStart s/Int
   :oldLines s/Int
   :newStart s/Int
   :newLines s/Int
   :lines [s/Str]})

(def ToolUseResult
  {(s/optional-key :type) (s/maybe s/Str)
   (s/optional-key :filePath) (s/maybe s/Str)
   (s/optional-key :oldString) (s/maybe s/Str)
   (s/optional-key :newString) (s/maybe s/Str)
   (s/optional-key :content) (s/maybe s/Str)
   (s/optional-key :structuredPatch) (s/maybe [StructuredPatchHunk])})

(def UserContentBlock
  {:type s/Str
   (s/optional-key :text) (s/maybe s/Str)
   (s/optional-key :tool_use_id) (s/maybe s/Str)
   (s/optional-key :content) (s/maybe s/Str)
   (s/optional-key :toolUseResult) (s/maybe ToolUseResult)})

(def UserMessageContent
  {:role (s/maybe s/Str)
   :content [UserContentBlock]})

(def AssistantContentBlock
  {:type (s/maybe s/Str)
   (s/optional-key :text) (s/maybe s/Str)
   (s/optional-key :thinking) (s/maybe s/Str)
   (s/optional-key :signature) (s/maybe s/Str)
   (s/optional-key :id) (s/maybe s/Str)
   (s/optional-key :name) (s/maybe s/Str)
   (s/optional-key :input) (s/maybe s/Str)
   (s/optional-key :tool_use_id) (s/maybe s/Str)
   (s/optional-key :content) (s/maybe s/Str)})

(def CacheCreation
  {(s/optional-key :ephemeral_5m_input_tokens) (s/maybe s/Int)
   (s/optional-key :ephemeral_1h_input_tokens) (s/maybe s/Int)})

(def Usage
  {(s/optional-key :input_tokens) (s/maybe s/Int)
   (s/optional-key :cache_creation_input_tokens) (s/maybe s/Int)
   (s/optional-key :cache_read_input_tokens) (s/maybe s/Int)
   (s/optional-key :cache_creation) (s/maybe CacheCreation)
   (s/optional-key :output_tokens) (s/maybe s/Int)
   (s/optional-key :service_tier) (s/maybe s/Str)})

(def AssistantMessageContent
  {(s/optional-key :model) (s/maybe s/Str)
   (s/optional-key :message-id) (s/maybe s/Str)
   (s/optional-key :type) (s/maybe s/Str)
   (s/optional-key :role) (s/maybe s/Str)
   (s/optional-key :content) [AssistantContentBlock]
   (s/optional-key :stop_reason) (s/maybe s/Str)
   (s/optional-key :stop_sequence) (s/maybe s/Str)
   (s/optional-key :usage) Usage})

(def CompactMetadata
  {:trigger (s/maybe s/Str)
   :preTokens (s/maybe s/Int)})

(def Snapshot
  {(s/optional-key :message-id) (s/maybe s/Str)
   (s/optional-key :tracked-file-backups) (s/maybe s/Str)
   (s/optional-key :timestamp) (s/maybe Timestamp)})

(def BaseMessage
  {:typename s/Str
   :id ID
   :project-id ProjectId
   :session-id SessionId
   :message-id MessageId
   :raw-message s/Str})

(def UserMessage
  (merge BaseMessage
         {(s/optional-key :parent-uuid) (s/maybe s/Str)
          (s/optional-key :is-sidechain) s/Bool
          (s/optional-key :user-type) (s/maybe s/Str)
          (s/optional-key :cwd) (s/maybe s/Str)
          (s/optional-key :version) (s/maybe s/Str)
          (s/optional-key :git-branch) (s/maybe s/Str)
          (s/optional-key :timestamp) (s/maybe Timestamp)
          (s/optional-key :message) UserMessageContent
          (s/optional-key :thinking-metadata) (s/maybe ThinkingMetadata)}))

(def AssistantMessage
  (merge BaseMessage
         {(s/optional-key :parent-uuid) (s/maybe s/Str)
          (s/optional-key :is-sidechain) s/Bool
          (s/optional-key :user-type) (s/maybe s/Str)
          (s/optional-key :cwd) (s/maybe s/Str)
          (s/optional-key :version) (s/maybe s/Str)
          (s/optional-key :git-branch) (s/maybe s/Str)
          (s/optional-key :request-id) (s/maybe s/Str)
          (s/optional-key :timestamp) (s/maybe Timestamp)
          (s/optional-key :message) AssistantMessageContent}))

(def FileHistorySnapshotMessage
  (merge BaseMessage
         {(s/optional-key :snapshot) Snapshot
          (s/optional-key :is-snapshot-update) s/Bool}))

(def QueueOperationMessage
  (merge BaseMessage
         {(s/optional-key :operation) (s/maybe s/Str)
          (s/optional-key :timestamp) (s/maybe Timestamp)
          (s/optional-key :content) (s/maybe s/Str)
          (s/optional-key :queue-session-id) (s/maybe s/Str)}))

(def SystemMessage
  (merge BaseMessage
         {(s/optional-key :parent-uuid) (s/maybe s/Str)
          (s/optional-key :logical-parent-uuid) (s/maybe s/Str)
          (s/optional-key :is-sidechain) s/Bool
          (s/optional-key :user-type) (s/maybe s/Str)
          (s/optional-key :cwd) (s/maybe s/Str)
          (s/optional-key :version) (s/maybe s/Str)
          (s/optional-key :git-branch) (s/maybe s/Str)
          (s/optional-key :subtype) (s/maybe s/Str)
          (s/optional-key :content) (s/maybe s/Str)
          (s/optional-key :is-meta) s/Bool
          (s/optional-key :timestamp) (s/maybe Timestamp)
          (s/optional-key :level) (s/maybe s/Str)
          (s/optional-key :compact-metadata) (s/maybe CompactMetadata)}))

(def SummaryMessage
  (merge BaseMessage
         {(s/optional-key :summary) (s/maybe s/Str)
          (s/optional-key :leaf-uuid) (s/maybe s/Str)}))

(def UnknownMessage BaseMessage)

(def BrokenMessage BaseMessage)

(def Message
  (s/conditional
   #(= "UserMessage" (:typename %)) UserMessage
   #(= "AssistantMessage" (:typename %)) AssistantMessage
   #(= "FileHistorySnapshotMessage" (:typename %)) FileHistorySnapshotMessage
   #(= "QueueOperationMessage" (:typename %)) QueueOperationMessage
   #(= "SystemMessage" (:typename %)) SystemMessage
   #(= "SummaryMessage" (:typename %)) SummaryMessage
   #(= "UnknownMessage" (:typename %)) UnknownMessage
   #(= "BrokenMessage" (:typename %)) BrokenMessage
   :else s/Any))

(def ClaudeJson
  {:projects {s/Keyword s/Any}
   s/Keyword s/Any})
