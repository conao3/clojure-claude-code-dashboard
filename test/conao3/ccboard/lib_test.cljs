(ns conao3.ccboard.lib-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [conao3.ccboard.lib :as c.lib]))

(defn stringify [x]
  (js/JSON.stringify (clj->js x)))

(deftest parse-user-message-test
  (testing "parses user message with string content"
    (let [data {:type "user"
                :uuid "uuid-123"
                :parentUuid "parent-uuid"
                :isSidechain false
                :userType "human"
                :cwd "/home/user"
                :version "1.0.0"
                :gitBranch "main"
                :timestamp "2024-01-01T00:00:00Z"
                :message {:role "user" :content "Hello, world!"}}
          result (c.lib/parse-user-message data "project-id" "session-id" "uuid-123" "{}" stringify)]
      (is (= "UserMessage" (:__typename result)))
      (is (= "project-id" (:projectId result)))
      (is (= "session-id" (:sessionId result)))
      (is (= "uuid-123" (:messageId result)))
      (is (= "parent-uuid" (:parentUuid result)))
      (is (false? (:isSidechain result)))
      (is (= "human" (:userType result)))
      (is (= "/home/user" (:cwd result)))
      (is (= "1.0.0" (:version result)))
      (is (= "main" (:gitBranch result)))
      (is (= "2024-01-01T00:00:00Z" (:timestamp result)))
      (is (= "user" (-> result :message :role)))
      (is (= 1 (count (-> result :message :content))))
      (is (= "text" (-> result :message :content first :type)))
      (is (= "Hello, world!" (-> result :message :content first :text)))))

  (testing "parses user message with array content"
    (let [data {:type "user"
                :uuid "uuid-456"
                :message {:role "user"
                          :content [{:type "text" :text "First block"}
                                    {:type "tool_result" :tool_use_id "tool-1" :content {:result "ok"}}]}}
          result (c.lib/parse-user-message data "proj" "sess" "uuid-456" "{}" stringify)]
      (is (= 2 (count (-> result :message :content))))
      (is (= "text" (-> result :message :content first :type)))
      (is (= "First block" (-> result :message :content first :text)))
      (is (= "tool_result" (-> result :message :content second :type)))
      (is (= "tool-1" (-> result :message :content second :tool_use_id)))))

  (testing "parses thinking metadata"
    (let [data {:type "user"
                :uuid "uuid-789"
                :message {:role "user" :content "test"}
                :thinkingMetadata {:level "high" :disabled false :triggers ["trigger1"]}}
          result (c.lib/parse-user-message data "proj" "sess" "uuid-789" "{}" stringify)]
      (is (= "high" (-> result :thinkingMetadata :level)))
      (is (false? (-> result :thinkingMetadata :disabled)))
      (is (= ["trigger1"] (-> result :thinkingMetadata :triggers))))))

(deftest parse-assistant-message-test
  (testing "parses assistant message with text content"
    (let [data {:type "assistant"
                :uuid "uuid-asst"
                :parentUuid "parent-uuid"
                :isSidechain false
                :userType "assistant"
                :cwd "/home/user"
                :version "1.0.0"
                :gitBranch "main"
                :requestId "req-123"
                :timestamp "2024-01-01T00:00:00Z"
                :message {:model "claude-3"
                          :id "msg-id"
                          :type "message"
                          :role "assistant"
                          :content [{:type "text" :text "Hello!"}]
                          :stop_reason "end_turn"
                          :usage {:input_tokens 100
                                  :output_tokens 50
                                  :cache_creation_input_tokens 10
                                  :cache_read_input_tokens 5}}}
          result (c.lib/parse-assistant-message data "proj" "sess" "uuid-asst" "{}" stringify)]
      (is (= "AssistantMessage" (:__typename result)))
      (is (= "proj" (:projectId result)))
      (is (= "sess" (:sessionId result)))
      (is (= "uuid-asst" (:messageId result)))
      (is (= "req-123" (:requestId result)))
      (is (= "claude-3" (-> result :message :model)))
      (is (= "msg-id" (-> result :message :messageId)))
      (is (= "assistant" (-> result :message :role)))
      (is (= "end_turn" (-> result :message :stop_reason)))
      (is (= 100 (-> result :message :usage :input_tokens)))
      (is (= 50 (-> result :message :usage :output_tokens)))))

  (testing "parses assistant message with tool use"
    (let [data {:type "assistant"
                :uuid "uuid-tool"
                :message {:model "claude-3"
                          :role "assistant"
                          :content [{:type "tool_use"
                                     :id "tool-use-id"
                                     :name "read_file"
                                     :input {:path "/home/user/file.txt"}}]
                          :usage {}}}
          result (c.lib/parse-assistant-message data "proj" "sess" "uuid-tool" "{}" stringify)]
      (is (= 1 (count (-> result :message :content))))
      (is (= "tool_use" (-> result :message :content first :type)))
      (is (= "tool-use-id" (-> result :message :content first :id)))
      (is (= "read_file" (-> result :message :content first :name)))
      (is (some? (-> result :message :content first :input)))))

  (testing "parses assistant message with thinking"
    (let [data {:type "assistant"
                :uuid "uuid-think"
                :message {:model "claude-3"
                          :role "assistant"
                          :content [{:type "thinking"
                                     :thinking "Let me think about this..."
                                     :signature "sig-123"}]
                          :usage {}}}
          result (c.lib/parse-assistant-message data "proj" "sess" "uuid-think" "{}" stringify)]
      (is (= "thinking" (-> result :message :content first :type)))
      (is (= "Let me think about this..." (-> result :message :content first :thinking)))
      (is (= "sig-123" (-> result :message :content first :signature))))))

(deftest parse-system-message-test
  (testing "parses system message"
    (let [data {:type "system"
                :uuid "uuid-sys"
                :parentUuid "parent"
                :logicalParentUuid "logical-parent"
                :isSidechain true
                :userType "system"
                :cwd "/home"
                :version "1.0.0"
                :gitBranch "main"
                :subtype "info"
                :content "System message content"
                :isMeta true
                :timestamp "2024-01-01T00:00:00Z"
                :level "info"
                :compactMetadata {:trigger "auto" :preTokens 100}}
          result (c.lib/parse-system-message data "proj" "sess" "uuid-sys" "{}")]
      (is (= "SystemMessage" (:__typename result)))
      (is (= "parent" (:parentUuid result)))
      (is (= "logical-parent" (:logicalParentUuid result)))
      (is (true? (:isSidechain result)))
      (is (= "info" (:subtype result)))
      (is (= "System message content" (:content result)))
      (is (true? (:isMeta result)))
      (is (= "info" (:level result)))
      (is (= "auto" (-> result :compactMetadata :trigger)))
      (is (= 100 (-> result :compactMetadata :preTokens))))))

(deftest parse-summary-message-test
  (testing "parses summary message"
    (let [data {:type "summary"
                :uuid "uuid-sum"
                :summary "This is a summary"
                :leafUuid "leaf-uuid"}
          result (c.lib/parse-summary-message data "proj" "sess" "uuid-sum" "{}")]
      (is (= "SummaryMessage" (:__typename result)))
      (is (= "This is a summary" (:summary result)))
      (is (= "leaf-uuid" (:leafUuid result))))))

(deftest parse-file-history-snapshot-message-test
  (testing "parses file history snapshot message"
    (let [data {:type "file-history-snapshot"
                :uuid "uuid-fhs"
                :snapshot {:messageId "msg-id"
                           :trackedFileBackups {:file1 "backup1"}
                           :timestamp "2024-01-01T00:00:00Z"}
                :isSnapshotUpdate true}
          result (c.lib/parse-file-history-snapshot-message data "proj" "sess" "uuid-fhs" "{}" stringify)]
      (is (= "FileHistorySnapshotMessage" (:__typename result)))
      (is (= "msg-id" (-> result :snapshot :messageId)))
      (is (some? (-> result :snapshot :trackedFileBackups)))
      (is (= "2024-01-01T00:00:00Z" (-> result :snapshot :timestamp)))
      (is (true? (:isSnapshotUpdate result))))))

(deftest parse-queue-operation-message-test
  (testing "parses queue operation message"
    (let [data {:type "queue-operation"
                :uuid "uuid-queue"
                :operation "enqueue"
                :timestamp "2024-01-01T00:00:00Z"
                :content "queue content"
                :sessionId "queue-session-id"}
          result (c.lib/parse-queue-operation-message data "proj" "sess" "uuid-queue" "{}")]
      (is (= "QueueOperationMessage" (:__typename result)))
      (is (= "enqueue" (:operation result)))
      (is (= "2024-01-01T00:00:00Z" (:timestamp result)))
      (is (= "queue content" (:content result)))
      (is (= "queue-session-id" (:queueSessionId result))))))

(deftest parse-unknown-message-test
  (testing "parses unknown message"
    (let [result (c.lib/parse-unknown-message "proj" "sess" "msg-id" "raw line")]
      (is (= "UnknownMessage" (:__typename result)))
      (is (= "proj" (:projectId result)))
      (is (= "sess" (:sessionId result)))
      (is (= "msg-id" (:messageId result)))
      (is (= "raw line" (:rawMessage result))))))

(deftest parse-broken-message-test
  (testing "parses broken message with index as id"
    (let [result (c.lib/parse-broken-message "proj" "sess" 5 "broken json")]
      (is (= "BrokenMessage" (:__typename result)))
      (is (= "proj" (:projectId result)))
      (is (= "sess" (:sessionId result)))
      (is (= "5" (:messageId result)))
      (is (= "broken json" (:rawMessage result))))))

(deftest parse-message-test
  (testing "dispatches to correct parser based on type"
    (let [user-data {:type "user" :uuid "u1" :message {:role "user" :content "hi"}}
          assistant-data {:type "assistant" :uuid "a1" :message {:role "assistant" :content [] :usage {}}}
          system-data {:type "system" :uuid "s1"}
          summary-data {:type "summary" :uuid "sum1" :summary "sum" :leafUuid "leaf"}
          unknown-data {:type "something-else" :uuid "x1"}]

      (is (= "UserMessage" (:__typename (c.lib/parse-message user-data "p" "s" "u1" 0 "{}" stringify))))
      (is (= "AssistantMessage" (:__typename (c.lib/parse-message assistant-data "p" "s" "a1" 1 "{}" stringify))))
      (is (= "SystemMessage" (:__typename (c.lib/parse-message system-data "p" "s" "s1" 2 "{}" stringify))))
      (is (= "SummaryMessage" (:__typename (c.lib/parse-message summary-data "p" "s" "sum1" 3 "{}" stringify))))
      (is (= "UnknownMessage" (:__typename (c.lib/parse-message unknown-data "p" "s" "x1" 4 "{}" stringify)))))))

(deftest make-project-test
  (testing "creates project from path"
    (let [result (c.lib/make-project "/home/user/myproject")]
      (is (= "Project" (:__typename result)))
      (is (= "-home-user-myproject" (:projectId result)))
      (is (= "/home/user/myproject" (:name result)))
      (is (some? (:id result))))))

(deftest make-session-test
  (testing "creates session"
    (let [result (c.lib/make-session "project-id" "session-id" "2024-01-01T00:00:00Z")]
      (is (= "Session" (:__typename result)))
      (is (= "project-id" (:projectId result)))
      (is (= "session-id" (:sessionId result)))
      (is (= "2024-01-01T00:00:00Z" (:createdAt result)))
      (is (some? (:id result))))))

(deftest claude-json->projects-test
  (testing "converts claude json to projects"
    (let [claude-json {:projects {(keyword "/home/user/proj1") {:some "data"}
                                  (keyword "/home/user/proj2") {:other "data"}}}
          result (c.lib/claude-json->projects claude-json)]
      (is (= 2 (count result)))
      (is (every? #(= "Project" (:__typename %)) result))
      (is (some #(= "/home/user/proj1" (:name %)) result))
      (is (some #(= "/home/user/proj2" (:name %)) result)))))

(deftest find-project-by-id-test
  (testing "finds project by projectId"
    (let [projects [{:__typename "Project" :id "id1" :projectId "proj-1" :name "/path/1"}
                    {:__typename "Project" :id "id2" :projectId "proj-2" :name "/path/2"}
                    {:__typename "Project" :id "id3" :projectId "proj-3" :name "/path/3"}]]
      (is (= "proj-2" (:projectId (c.lib/find-project-by-id projects "proj-2"))))
      (is (nil? (c.lib/find-project-by-id projects "proj-99"))))))
