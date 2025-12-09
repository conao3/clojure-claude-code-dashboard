(ns conao3.claude-code-dashboard.storybook.stories
  (:require
   ["lucide-react" :as lucide]
   ["react-aria-components" :as rac]
   [reagent.core :as r]))

(defn NavItem [{:keys [icon label active collapsed on-click badge]}]
  [:> rac/Button
   {:className (str "flex items-center gap-3 w-full rounded-md transition-all outline-none "
                    (if collapsed "p-3 justify-center" "px-3 py-2.5 justify-start")
                    (if active " bg-accent-background/20 text-accent-content" " text-neutral-subdued-content hover:text-neutral-content"))
    :onPress on-click}
   [:> icon {:size 18}]
   (when-not collapsed
     [:<>
      [:span.flex-1.text-left.text-sm label]
      (when badge
        [:span.bg-accent-background.text-white.text-xs.px-1.5.py-0.5.rounded-full.min-w-5.text-center badge])])])

(defn ProjectItem [{:keys [project active collapsed on-click]}]
  (let [session-count (count (:sessions project))]
    [:> rac/Button
     {:className (str "flex items-center gap-2 w-full rounded transition-all outline-none "
                      (if collapsed "p-2 justify-center" "px-3 py-2 justify-start")
                      (if active " bg-accent-background/15 text-neutral-content" " text-neutral-subdued-content hover:text-neutral-content"))
      :onPress on-click
      :isDisabled (zero? session-count)}
     [:> lucide/GitBranch {:size 14 :className "text-neutral-subdued-content flex-shrink-0"}]
     (when-not collapsed
       [:span.text-sm.truncate.flex-1.text-left (:name project)])
     (when (and (not collapsed) (pos? session-count))
       [:span.text-xs.text-neutral-subdued-content session-count])]))

(defn SessionItem [{:keys [session active on-click]}]
  [:> rac/Button
   {:className (str "w-full text-left p-3 border-b border-gray-200 outline-none transition-all "
                    (if active
                      "bg-accent-background/10 border-l-3 border-l-accent-background"
                      "border-l-3 border-l-transparent hover:bg-background-layer-1"))
    :onPress on-click}
   [:div.flex.items-center.gap-2.mb-1
    [:span.w-2.h-2.rounded-full.bg-positive-visual.flex-shrink-0]
    [:span.text-sm.font-medium.text-neutral-content.truncate.flex-1
     (:sessionId session)]]
   [:div.text-xs.text-neutral-subdued-content
    (:createdAt session)]])

(defn CopyButton []
  (let [copied? (r/atom false)]
    (fn [{:keys [text label class]}]
      [:> rac/Button
       {:className (str "px-2 py-1 rounded bg-background-layer-1 opacity-0 group-hover:opacity-70 hover:opacity-100 pressed:opacity-100 flex items-center gap-1 text-xs " class)
        :onPress (fn []
                   (-> js/navigator .-clipboard (.writeText text))
                   (reset! copied? true)
                   (js/setTimeout #(reset! copied? false) 1000))}
       (if @copied?
         [:<> [:> lucide/Check {:size 12}] "Copied!"]
         [:<> [:> lucide/Copy {:size 12}] (or label "Copy")])])))

(defn ToolResultBlock [{:keys [block]}]
  [:div.mt-2.p-3.rounded-lg.bg-background-layer-1.border.border-gray-200
   [:div.text-xs.font-medium.text-neutral-subdued-content.mb-1 "Tool Result"]
   [:div.text-xs.text-neutral-subdued-content.mb-2 (str "ID: " (:tool_use_id block))]
   (when (:content block)
     [:pre.text-xs.whitespace-pre-wrap.break-all.text-neutral-content (:content block)])])

(defn ContentBlock [{:keys [block tool-results]}]
  (case (:type block)
    "text"
    [:p.text-sm.leading-relaxed.text-neutral-content (:text block)]

    "thinking"
    [:div {:class "mt-3 p-3 rounded-lg bg-yellow-900/10 border border-yellow-700/20"}
     [:div.flex.items-center.gap-1.5.text-xs.font-medium.text-yellow-600.mb-2
      [:> lucide/Brain {:size 12}]
      "Thinking"]
     [:pre.text-xs.whitespace-pre-wrap.break-all.text-neutral-subdued-content (:thinking block)]]

    "tool_use"
    (let [result (get tool-results (:id block))]
      [:div.mt-3
       [:div {:class "p-3 rounded-lg bg-cyan-900/10 border border-cyan-700/20"}
        [:div.flex.items-center.gap-1.5.text-xs.font-medium.text-cyan-500.mb-2
         [:> lucide/Terminal {:size 12}]
         (:name block)]
        [:div.text-xs.text-neutral-subdued-content.mb-2 (str "ID: " (:id block))]
        (when (:input block)
          [:pre.text-xs.whitespace-pre-wrap.break-all.font-mono.text-neutral-content.bg-background-layer-1.p-2.rounded
           (:input block)])]
       (when result
         [ToolResultBlock {:block result}])])

    "tool_result"
    [ToolResultBlock {:block block}]

    [:div.mt-3.p-3.rounded-lg.bg-notice-background.text-white
     [:div.text-xs.font-medium (str "Unknown: " (:type block))]]))

(defn MessageBubble [{:keys [role icon icon-class children time tool-count thinking?]}]
  [:div {:class (str "mb-4 " (when (= role :user) "pl-12"))}
   [:div {:class (str "rounded-xl p-4 border "
                      (if (= role :user)
                        "bg-accent-background-subdued border-accent-background"
                        "bg-background-layer-1 border-gray-200"))}
    [:div.flex.items-center.gap-2.mb-2
     (when icon
       [:> icon {:size 14 :className icon-class}])
     [:span {:class (str "text-xs font-medium " (if (= role :user) "text-accent-content" "text-purple-400"))}
      (if (= role :user) "You" "Claude")]
     [:span.text-xs.text-neutral-subdued-content time]
     (when (or tool-count thinking?)
       [:div.ml-auto.flex.gap-3
        (when thinking?
          [:span.text-xs.text-yellow-600.flex.items-center.gap-1
           [:> lucide/Brain {:size 12}] "Thinking"])
        (when tool-count
          [:span.text-xs.text-cyan-500.flex.items-center.gap-1
           [:> lucide/Terminal {:size 12}] (str tool-count " tools")])])]
    children]])

(defn AssistantMessage [{:keys [message tool-results]}]
  (let [content-blocks (get-in message [:message :content])
        tool-count (->> content-blocks (filter #(= (:type %) "tool_use")) count)
        has-thinking? (some #(= (:type %) "thinking") content-blocks)]
    [MessageBubble {:role :assistant
                    :icon lucide/Cpu
                    :icon-class "text-purple-400"
                    :tool-count (when (pos? tool-count) tool-count)
                    :thinking? has-thinking?}
     [:<>
      (for [[idx block] (map-indexed vector content-blocks)]
        ^{:key idx} [ContentBlock {:block block :tool-results tool-results}])]]))

(defn UserMessage [{:keys [message displayed-tool-use-ids]}]
  (let [content-blocks (get-in message [:message :content])
        tool-result-ids (->> content-blocks
                             (filter #(= (:type %) "tool_result"))
                             (map :tool_use_id)
                             set)
        all-displayed? (and (seq tool-result-ids)
                            (every? #(contains? displayed-tool-use-ids %) tool-result-ids))]
    [:div {:class (when all-displayed? "opacity-50")}
     [MessageBubble {:role :user
                     :icon lucide/User
                     :icon-class "text-accent-content"}
      [:<>
       (for [[idx block] (map-indexed vector content-blocks)]
         ^{:key idx}
         (case (:type block)
           "text" [:p.text-sm.leading-relaxed.text-neutral-content (:text block)]
           "tool_result" [ToolResultBlock {:block block}]
           [:div.text-xs.text-notice-content (str "Unknown: " (:type block))]))]]]))

(defn SystemMessageItem [{:keys [message]}]
  [:div.mb-3.opacity-60
   [:div {:class "rounded-lg p-3 bg-informative-background-subdued border border-informative-background"}
    [:div.flex.items-center.gap-2.text-xs.text-informative-content
     [:> lucide/Settings {:size 12}]
     [:span.font-medium (str "System: " (:subtype message))]
     [:span.text-neutral-subdued-content (:timestamp message)]]
    [:div.mt-2.text-xs.text-neutral-subdued-content
     [:div [:span.font-medium "Content: "] (:systemContent message)]
     [:div [:span.font-medium "Level: "] (:level message)]]]])

(defn SummaryMessageItem [{:keys [message]}]
  [:div.mb-3.opacity-60
   [:div {:class "rounded-lg p-3 bg-positive-background-subdued border border-positive-background"}
    [:div.flex.items-center.gap-2.text-xs.text-positive-content
     [:> lucide/FileText {:size 12}]
     [:span.font-medium "Summary"]]
    [:p.mt-1.text-sm.text-neutral-content (:summary message)]]])

(defn FileHistorySnapshotMessage [{:keys [message]}]
  (let [tracked-file-backups (:trackedFileBackups message)]
    [:div.mb-3.opacity-50
     [:div.rounded-lg.p-3.bg-background-layer-1.border.border-gray-200
      [:div.flex.items-center.gap-2.text-xs.text-neutral-subdued-content
       [:> lucide/History {:size 12}]
       [:span "FileHistorySnapshot"]
       (when (:isSnapshotUpdate message)
         [:span.text-xs.bg-gray-600.text-white.px-1.rounded "update"])]
      (when (seq tracked-file-backups)
        [:div.mt-2.text-xs.text-neutral-subdued-content
         (str (count tracked-file-backups) " tracked files")])]]))

(defn QueueOperationMessage [{:keys [message]}]
  [:div.mb-3.opacity-50
   [:div.rounded-lg.p-3.bg-background-layer-1.border.border-gray-200
    [:div.flex.items-center.gap-2.text-xs.text-neutral-subdued-content
     [:> lucide/ListOrdered {:size 12}]
     [:span (str "Queue: " (:operation message))]]
    [:div.mt-1.text-xs.text-neutral-subdued-content
     (when (:content message) [:div [:span.font-medium "Content: "] (:content message)])
     [:div [:span.font-medium "Session: "] (:queueSessionId message)]
     [:div [:span.font-medium "Time: "] (:timestamp message)]]]])

(defn UnknownMessage [{:keys [message]}]
  [:div.mb-3
   [:div.rounded-lg.p-3.bg-notice-background.text-white
    [:div.flex.items-center.gap-2.text-xs
     [:> lucide/HelpCircle {:size 12}]
     [:span (str "Unknown: " (:messageId message))]]]])

(defn BrokenMessage [{:keys [message]}]
  [:div.mb-3
   [:div.rounded-lg.p-3.bg-negative-background.text-white
    [:div.flex.items-center.gap-2.text-xs
     [:> lucide/AlertTriangle {:size 12}]
     [:span (str "Broken: " (:messageId message))]]
    [:div.mt-2.text-xs
     [:pre {:class "whitespace-pre-wrap break-all bg-black/20 p-2 rounded max-h-32 overflow-auto"}
      (:rawMessage message)]]]])

(defn Sidebar [{:keys [projects selected-project on-select-project collapsed]}]
  [:div {:class (str "flex flex-col bg-background-layer-1 border-r border-gray-200 transition-all duration-200 "
                     (if collapsed "w-16" "w-60"))}
   [:div.p-4.border-b.border-gray-200.flex.items-center
    {:class (if collapsed "justify-center" "justify-between")}
    [:div.flex.items-center.gap-2.5
     [:div.w-8.h-8.rounded-lg.flex.items-center.justify-center.flex-shrink-0
      {:class "bg-gradient-to-br from-purple-600 to-blue-600"}
      [:> lucide/Zap {:size 18 :className "text-white"}]]
     (when-not collapsed
       [:span.font-semibold.text-neutral-content "Claude Code"])]
    (when-not collapsed
      [:> rac/Button
       {:className "p-1 text-neutral-subdued-content hover:text-neutral-content outline-none"
        :onPress #()}
       [:> lucide/PanelLeftClose {:size 18}]])]

   (when collapsed
     [:> rac/Button
      {:className "p-3 mx-auto my-2 text-neutral-subdued-content hover:text-neutral-content outline-none"
       :onPress #()}
      [:> lucide/PanelLeft {:size 18}]])

   [:div.p-2.flex.flex-col.gap-1
    [NavItem {:icon lucide/Home :label "Dashboard" :active false :collapsed collapsed :on-click #()}]
    [NavItem {:icon lucide/Folder :label "Projects" :active true :collapsed collapsed :on-click #() :badge (str (count projects))}]
    [NavItem {:icon lucide/History :label "Recent" :active false :collapsed collapsed :on-click #()}]]

   (when-not collapsed
     [:div.px-2.mt-2
      [:div.text-xs.font-medium.text-neutral-subdued-content.uppercase.tracking-wide.px-3.py-2
       "All Projects"]])

   [:div.flex-1.overflow-y-auto.p-2.flex.flex-col.gap-0.5
    (for [project projects]
      ^{:key (:id project)}
      [ProjectItem {:project project
                    :active (= (:id project) (:id selected-project))
                    :collapsed collapsed
                    :on-click #(when on-select-project (on-select-project project))}])]

   [:div.p-2.border-t.border-gray-200
    [NavItem {:icon lucide/Settings :label "Settings" :active false :collapsed collapsed :on-click #()}]]])

(defn SessionsPanel [{:keys [project sessions on-select-session selected-session]}]
  [:div.w-72.bg-background-base.border-r.border-gray-200.flex.flex-col
   [:div.p-4.border-b.border-gray-200.flex.items-center.justify-between
    [:h2.text-base.font-semibold.text-neutral-content.truncate
     (if project (:name project) "Sessions")]
    [:> rac/Button
     {:className "p-1 text-neutral-subdued-content hover:text-neutral-content outline-none"}
     [:> lucide/MoreHorizontal {:size 18}]]]

   [:div.p-3
    [:div.relative
     [:> lucide/Search {:size 14 :className "absolute left-2.5 top-1/2 -translate-y-1/2 text-neutral-subdued-content"}]
     [:input
      {:type "text"
       :placeholder "Search sessions..."
       :class "w-full bg-background-layer-1 border border-gray-300 rounded-md py-2 pl-8 pr-3 text-sm text-neutral-content outline-none focus:border-accent-background"}]]]

   [:div.flex-1.overflow-y-auto
    (if (empty? sessions)
      [:p.p-4.text-neutral-subdued-content.text-sm "Select a project"]
      (for [session sessions]
        ^{:key (:id session)}
        [SessionItem {:session session
                      :active (= (:id session) (:id selected-session))
                      :on-click #(when on-select-session (on-select-session session))}]))]])

(defn MessagesPanel [{:keys [session]}]
  [:div.flex-1.flex.flex-col.bg-background-base.min-h-0.overflow-hidden
   [:div.p-4.border-b.border-gray-200.shrink-0
    [:h2.text-lg.font-semibold.text-neutral-content.truncate
     (or (:sessionId session) "Messages")]
    (when session
      [:div.text-sm.text-neutral-subdued-content.mt-1
       (:createdAt session)])]
   [:div.flex-1.flex.items-center.justify-center.text-neutral-subdued-content
    (if session
      "Select a session to view messages"
      "No session selected")]])
