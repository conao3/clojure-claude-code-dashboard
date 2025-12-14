(ns conao3.ccboard.portfolio.messages
  (:require
   ["lucide-react" :as lucide]
   ["react-aria-components" :as rac]
   [portfolio.reagent-18 :refer-macros [defscene]]
   [reagent.core :as r]))

(defn CopyButton []
  (let [copied? (r/atom false)]
    (fn [{:keys [text label class]}]
      [:> rac/Button
       {:className (str "px-2 py-1 rounded bg-background-layer-1 flex items-center gap-1 text-xs " class)
        :onPress (fn []
                   (-> js/navigator .-clipboard (.writeText text))
                   (reset! copied? true)
                   (js/setTimeout #(reset! copied? false) 1000))}
       (if @copied?
         [:<> [:> lucide/Check {:size 12}] "Copied!"]
         [:<> [:> lucide/Copy {:size 12}] (or label "Copy")])])))

(defn ToolResultBlock [{:keys [block]}]
  [:div.mt-2.rounded-lg.border.border-gray-200.bg-background-layer-1.p-3
   [:div.mb-1.text-xs.font-medium.text-neutral-subdued-content "Tool Result"]
   [:div.mb-2.text-xs.text-neutral-subdued-content (str "ID: " (:tool_use_id block))]
   (when (:content block)
     [:pre.text-xs.whitespace-pre-wrap.break-all.text-neutral-content (:content block)])])

(defn ContentBlock [{:keys [block tool-results]}]
  (case (:type block)
    "text"
    [:p.text-sm.leading-relaxed.text-neutral-content (:text block)]

    "thinking"
    [:div {:class "mt-3 rounded-lg border border-yellow-700/20 bg-yellow-900/10 p-3"}
     [:div.5.mb-2.flex.items-center.gap-1.text-xs.font-medium.text-yellow-600
      [:> lucide/Brain {:size 12}]
      "Thinking"]
     [:pre.text-xs.whitespace-pre-wrap.break-all.text-neutral-subdued-content (:thinking block)]]

    "tool_use"
    (let [result (get tool-results (:id block))]
      [:div.mt-3
       [:div {:class "rounded-lg border border-cyan-700/20 bg-cyan-900/10 p-3"}
        [:div.5.mb-2.flex.items-center.gap-1.text-xs.font-medium.text-cyan-500
         [:> lucide/Terminal {:size 12}]
         (:name block)]
        [:div.mb-2.text-xs.text-neutral-subdued-content (str "ID: " (:id block))]
        (when (:input block)
          [:pre.text-xs.whitespace-pre-wrap.break-all.font-mono.text-neutral-content.bg-background-layer-1.p-2.rounded
           (:input block)])]
       (when result
         [ToolResultBlock {:block result}])])

    "tool_result"
    [ToolResultBlock {:block block}]

    [:div.mt-3.rounded-lg.bg-notice-background.p-3.text-white
     [:div.text-xs.font-medium (str "Unknown: " (:type block))]]))

(defn MessageBubble [{:keys [role icon icon-class children timestamp tool-count thinking?]}]
  [:div {:class (str "mb-4 " (when (= :user role) "pl-12"))}
   [:div {:class (str "rounded-xl p-4 border "
                      (if (= :user role)
                        "bg-accent-background-subdued border-accent-background"
                        "bg-background-layer-1 border-gray-200"))}
    [:div.mb-2.flex.items-center.gap-2
     (when icon
       [:> icon {:size 14 :className icon-class}])
     [:span {:class (str "text-xs font-medium " (if (= :user role) "text-accent-content" "text-purple-400"))}
      (if (= :user role) "You" "Claude")]
     [:span.text-xs.text-neutral-subdued-content timestamp]
     (when (or tool-count thinking?)
       [:div.ml-auto.flex.gap-3
        (when thinking?
          [:span.flex.items-center.gap-1.text-xs.text-yellow-600
           [:> lucide/Brain {:size 12}] "Thinking"])
        (when tool-count
          [:span.flex.items-center.gap-1.text-xs.text-cyan-500
           [:> lucide/Terminal {:size 12}] (str tool-count " tools")])])]
    children]])

(defn SystemMessageItem [{:keys [message]}]
  [:div.mb-3.opacity-60
   [:div {:class "bg-informative-background-subdued rounded-lg border border-informative-background p-3"}
    [:div.text-informative-content.flex.items-center.gap-2.text-xs
     [:> lucide/Settings {:size 12}]
     [:span.font-medium (str "System: " (:subtype message))]
     [:span.text-neutral-subdued-content (:timestamp message)]]
    [:div.mt-2.text-xs.text-neutral-subdued-content
     [:div [:span.font-medium "Content: "] (:systemContent message)]
     [:div [:span.font-medium "Level: "] (:level message)]]]])

(defn SummaryMessageItem [{:keys [message]}]
  [:div.mb-3.opacity-60
   [:div {:class "bg-positive-background-subdued rounded-lg border border-positive-background p-3"}
    [:div.text-positive-content.flex.items-center.gap-2.text-xs
     [:> lucide/FileText {:size 12}]
     [:span.font-medium "Summary"]]
    [:p.mt-1.text-sm.text-neutral-content (:summary message)]]])

(defn FileHistorySnapshotMessage [{:keys [message]}]
  (let [tracked-file-backups (:trackedFileBackups message)]
    [:div.mb-3.opacity-50
     [:div.rounded-lg.border.border-gray-200.bg-background-layer-1.p-3
      [:div.flex.items-center.gap-2.text-xs.text-neutral-subdued-content
       [:> lucide/History {:size 12}]
       [:span "FileHistorySnapshot"]
       (when (:isSnapshotUpdate message)
         [:span.rounded.bg-gray-600.px-1.text-xs.text-white "update"])]
      (when (seq tracked-file-backups)
        [:div.mt-2.text-xs.text-neutral-subdued-content
         (str (count tracked-file-backups) " tracked files")])]]))

(defn QueueOperationMessage [{:keys [message]}]
  [:div.mb-3.opacity-50
   [:div.rounded-lg.border.border-gray-200.bg-background-layer-1.p-3
    [:div.flex.items-center.gap-2.text-xs.text-neutral-subdued-content
     [:> lucide/ListOrdered {:size 12}]
     [:span (str "Queue: " (:operation message))]]
    [:div.mt-1.text-xs.text-neutral-subdued-content
     (when (:content message) [:div [:span.font-medium "Content: "] (:content message)])
     [:div [:span.font-medium "Session: "] (:queueSessionId message)]
     [:div [:span.font-medium "Time: "] (:timestamp message)]]]])

(defn UnknownMessage [{:keys [message]}]
  [:div.mb-3
   [:div.rounded-lg.bg-notice-background.p-3.text-white
    [:div.flex.items-center.gap-2.text-xs
     [:> lucide/HelpCircle {:size 12}]
     [:span (str "Unknown: " (:messageId message))]]]])

(defn BrokenMessage [{:keys [message]}]
  [:div.mb-3
   [:div.rounded-lg.bg-negative-background.p-3.text-white
    [:div.flex.items-center.gap-2.text-xs
     [:> lucide/AlertTriangle {:size 12}]
     [:span (str "Broken: " (:messageId message))]]
    [:div.mt-2.text-xs
     [:pre {:class "max-h-32 overflow-auto rounded bg-black/20 p-2 break-all whitespace-pre-wrap"}
      (:rawMessage message)]]]])

(defscene copy-button
  :title "CopyButton"
  [:div.flex.gap-2.bg-background-base.p-4
   [CopyButton {:text "Sample text" :label "Copy"}]
   [CopyButton {:text "{\"key\": \"value\"}" :label "Copy JSON"}]
   [CopyButton {:text "raw message" :label "Raw"}]])

(defscene content-block-text
  :title "ContentBlock - Text"
  [:div.w-150.bg-background-base.p-4
   [ContentBlock {:block {:type "text"
                          :text "これはテキストブロックのサンプルです。コードの説明や応答内容がここに表示されます。"}
                  :tool-results {}}]])

(defscene content-block-thinking
  :title "ContentBlock - Thinking"
  [:div.w-150.bg-background-base.p-4
   [ContentBlock {:block {:type "thinking"
                          :thinking "ユーザーの質問を分析しています...\nコードの構造を理解する必要があります。\n最適なアプローチを検討中です。"}
                  :tool-results {}}]])

(defscene content-block-tool-use
  :title "ContentBlock - Tool Use"
  [:div.w-150.bg-background-base.p-4
   [ContentBlock {:block {:type "tool_use"
                          :id "tool-123"
                          :name "Read"
                          :input "{\n  \"file_path\": \"/src/main.cljs\",\n  \"offset\": 0,\n  \"limit\": 100\n}"}
                  :tool-results {}}]])

(defscene content-block-tool-use-with-result
  :title "ContentBlock - Tool Use + Result"
  [:div.w-150.bg-background-base.p-4
   [ContentBlock {:block {:type "tool_use"
                          :id "tool-456"
                          :name "Bash"
                          :input "{\n  \"command\": \"git status\"\n}"}
                  :tool-results {"tool-456" {:type "tool_result"
                                             :tool_use_id "tool-456"
                                             :content "On branch main\nYour branch is up to date with 'origin/main'.\n\nnothing to commit, working tree clean"}}}]])

(defscene tool-result-block
  :title "ToolResultBlock"
  [:div.w-150.bg-background-base.p-4
   [ToolResultBlock {:block {:type "tool_result"
                             :tool_use_id "tool-789"
                             :content "File contents:\n(ns example.core)\n\n(defn hello [name]\n  (str \"Hello, \" name \"!\"))"}}]])

(defscene message-bubble-user
  :title "MessageBubble - User"
  [:div.w-150.bg-background-base.p-4
   [MessageBubble {:role :user
                   :icon lucide/User
                   :icon-class "text-accent-content"
                   :time "14:30"}
    [:p.text-sm.leading-relaxed.text-neutral-content
     "こんにちは、コードのレビューをお願いします。"]]])

(defscene message-bubble-assistant
  :title "MessageBubble - Assistant"
  [:div.w-150.bg-background-base.p-4
   [MessageBubble {:role :assistant
                   :icon lucide/Cpu
                   :icon-class "text-purple-400"
                   :time "14:31"}
    [:p.text-sm.leading-relaxed.text-neutral-content
     "はい、コードを確認しますね。いくつかの改善点を見つけました。"]]])

(defscene message-bubble-with-tools
  :title "MessageBubble - With Tools"
  [:div.w-150.bg-background-base.p-4
   [MessageBubble {:role :assistant
                   :icon lucide/Cpu
                   :icon-class "text-purple-400"
                   :time "14:32"
                   :tool-count 3}
    [:p.text-sm.leading-relaxed.text-neutral-content
     "ファイルを読み込んでいます..."]]])

(defscene message-bubble-with-thinking
  :title "MessageBubble - With Thinking"
  [:div.w-150.bg-background-base.p-4
   [MessageBubble {:role :assistant
                   :icon lucide/Cpu
                   :icon-class "text-purple-400"
                   :time "14:33"
                   :thinking? true}
    [:p.text-sm.leading-relaxed.text-neutral-content
     "問題を分析しています..."]]])

(defscene message-bubble-with-tools-and-thinking
  :title "MessageBubble - With Tools and Thinking"
  [:div.w-150.bg-background-base.p-4
   [MessageBubble {:role :assistant
                   :icon lucide/Cpu
                   :icon-class "text-purple-400"
                   :time "14:34"
                   :tool-count 5
                   :thinking? true}
    [:p.text-sm.leading-relaxed.text-neutral-content
     "複雑なタスクを実行中です..."]]])

(defscene system-message
  :title "SystemMessageItem"
  [:div.w-150.bg-background-base.p-4
   [SystemMessageItem {:message {:subtype "context_loaded"
                                 :timestamp "2024/12/10 14:30:00"
                                 :systemContent "コンテキストが正常に読み込まれました。"
                                 :level "info"}}]])

(defscene summary-message
  :title "SummaryMessageItem"
  [:div.w-150.bg-background-base.p-4
   [SummaryMessageItem {:message {:summary "このセッションでは、ユーザーがコードレビューを依頼し、いくつかの改善点を提案しました。主な変更点は関数の分割とエラーハンドリングの追加です。"}}]])

(defscene file-history-snapshot
  :title "FileHistorySnapshotMessage"
  [:div.w-150.bg-background-base.p-4
   [FileHistorySnapshotMessage {:message {:isSnapshotUpdate false
                                          :trackedFileBackups ["/src/main.cljs"
                                                               "/src/components/button.cljs"
                                                               "/test/main_test.cljs"]}}]])

(defscene file-history-snapshot-update
  :title "FileHistorySnapshotMessage - Update"
  [:div.w-150.bg-background-base.p-4
   [FileHistorySnapshotMessage {:message {:isSnapshotUpdate true
                                          :trackedFileBackups ["/src/main.cljs"]}}]])

(defscene queue-operation
  :title "QueueOperationMessage"
  [:div.w-150.bg-background-base.p-4
   [QueueOperationMessage {:message {:operation "enqueue"
                                     :content "新しいタスクをキューに追加"
                                     :queueSessionId "queue-session-abc123"
                                     :timestamp "2024/12/10 14:35:00"}}]])

(defscene unknown-message
  :title "UnknownMessage"
  [:div.w-150.bg-background-base.p-4
   [UnknownMessage {:message {:messageId "unknown-msg-123"}}]])

(defscene broken-message
  :title "BrokenMessage"
  [:div.w-150.bg-background-base.p-4
   [BrokenMessage {:message {:messageId "broken-msg-456"
                             :rawMessage "{\"type\": \"invalid\", \"data\": null, \"error\": \"Parse failed\"}"}}]])
