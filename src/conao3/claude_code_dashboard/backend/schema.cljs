(ns conao3.claude-code-dashboard.backend.schema
  (:require
   [schema.core :as s]))

(def ID s/Str)
(def ProjectId s/Str)
(def SessionId s/Str)
(def MessageId s/Str)
(def RawId s/Str)
(def Cursor s/Str)
(def Timestamp s/Str)

(def DecodedId
  {:type s/Str
   :raw-id RawId})

(def Project
  {:id ID
   :projectId ProjectId
   :name s/Str})

(def Session
  {:id ID
   :projectId ProjectId
   :sessionId SessionId
   :createdAt Timestamp})

(def PageInfo
  {:hasNextPage s/Bool
   :hasPreviousPage s/Bool
   :startCursor (s/maybe Cursor)
   :endCursor (s/maybe Cursor)})

(def Edge
  {:cursor Cursor
   :node s/Any})

(def Connection
  {:edges [Edge]
   :pageInfo PageInfo})

(def ThinkingMetadata
  {:level (s/maybe s/Str)
   :disabled s/Bool
   :triggers [s/Str]})

(def UserContentBlock
  {:type s/Str
   (s/optional-key :text) (s/maybe s/Str)
   (s/optional-key :tool_use_id) (s/maybe s/Str)
   (s/optional-key :content) (s/maybe s/Str)})

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
   (s/optional-key :messageId) (s/maybe s/Str)
   (s/optional-key :type) (s/maybe s/Str)
   (s/optional-key :role) (s/maybe s/Str)
   (s/optional-key :content) [AssistantContentBlock]
   (s/optional-key :stop_reason) (s/maybe s/Str)
   (s/optional-key :stop_sequence) (s/maybe s/Str)
   (s/optional-key :usage) Usage})

(def BaseMessage
  {:__typename s/Str
   :id ID
   :projectId ProjectId
   :sessionId SessionId
   :messageId MessageId
   :rawMessage s/Str})

(def UserMessage
  (merge BaseMessage
         {(s/optional-key :parentUuid) (s/maybe s/Str)
          (s/optional-key :isSidechain) s/Bool
          (s/optional-key :userType) (s/maybe s/Str)
          (s/optional-key :cwd) (s/maybe s/Str)
          (s/optional-key :version) (s/maybe s/Str)
          (s/optional-key :gitBranch) (s/maybe s/Str)
          (s/optional-key :timestamp) (s/maybe Timestamp)
          (s/optional-key :message) UserMessageContent
          (s/optional-key :thinkingMetadata) (s/maybe ThinkingMetadata)}))

(def AssistantMessage
  (merge BaseMessage
         {(s/optional-key :parentUuid) (s/maybe s/Str)
          (s/optional-key :isSidechain) s/Bool
          (s/optional-key :userType) (s/maybe s/Str)
          (s/optional-key :cwd) (s/maybe s/Str)
          (s/optional-key :version) (s/maybe s/Str)
          (s/optional-key :gitBranch) (s/maybe s/Str)
          (s/optional-key :requestId) (s/maybe s/Str)
          (s/optional-key :timestamp) (s/maybe Timestamp)
          (s/optional-key :message) AssistantMessageContent}))

(def Snapshot
  {(s/optional-key :messageId) (s/maybe s/Str)
   (s/optional-key :trackedFileBackups) (s/maybe s/Str)
   (s/optional-key :timestamp) (s/maybe Timestamp)})

(def FileHistorySnapshotMessage
  (merge BaseMessage
         {(s/optional-key :snapshot) Snapshot
          (s/optional-key :isSnapshotUpdate) s/Bool}))

(def QueueOperationMessage
  (merge BaseMessage
         {(s/optional-key :operation) (s/maybe s/Str)
          (s/optional-key :timestamp) (s/maybe Timestamp)
          (s/optional-key :content) (s/maybe s/Str)
          (s/optional-key :queueSessionId) (s/maybe s/Str)}))

(def CompactMetadata
  {:trigger (s/maybe s/Str)
   :preTokens (s/maybe s/Int)})

(def SystemMessage
  (merge BaseMessage
         {(s/optional-key :parentUuid) (s/maybe s/Str)
          (s/optional-key :logicalParentUuid) (s/maybe s/Str)
          (s/optional-key :isSidechain) s/Bool
          (s/optional-key :userType) (s/maybe s/Str)
          (s/optional-key :cwd) (s/maybe s/Str)
          (s/optional-key :version) (s/maybe s/Str)
          (s/optional-key :gitBranch) (s/maybe s/Str)
          (s/optional-key :subtype) (s/maybe s/Str)
          (s/optional-key :content) (s/maybe s/Str)
          (s/optional-key :isMeta) s/Bool
          (s/optional-key :timestamp) (s/maybe Timestamp)
          (s/optional-key :level) (s/maybe s/Str)
          (s/optional-key :compactMetadata) (s/maybe CompactMetadata)}))

(def SummaryMessage
  (merge BaseMessage
         {(s/optional-key :summary) (s/maybe s/Str)
          (s/optional-key :leafUuid) (s/maybe s/Str)}))

(def UnknownMessage BaseMessage)

(def BrokenMessage BaseMessage)

(def Message
  (s/conditional
   #(= (:__typename %) "UserMessage") UserMessage
   #(= (:__typename %) "AssistantMessage") AssistantMessage
   #(= (:__typename %) "FileHistorySnapshotMessage") FileHistorySnapshotMessage
   #(= (:__typename %) "QueueOperationMessage") QueueOperationMessage
   #(= (:__typename %) "SystemMessage") SystemMessage
   #(= (:__typename %) "SummaryMessage") SummaryMessage
   #(= (:__typename %) "UnknownMessage") UnknownMessage
   #(= (:__typename %) "BrokenMessage") BrokenMessage
   :else s/Any))

(def ClaudeJson
  {:projects {s/Keyword s/Any}
   s/Keyword s/Any})

(def ServerState
  {:server s/Any
   :api-server s/Any
   :admin-server (s/maybe s/Any)})
