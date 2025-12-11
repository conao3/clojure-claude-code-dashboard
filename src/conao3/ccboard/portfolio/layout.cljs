(ns conao3.ccboard.portfolio.layout
  (:require
   [portfolio.reagent-18 :refer-macros [defscene]]
   ["lucide-react" :as lucide]
   ["react-aria-components" :as rac]
   [conao3.ccboard.portfolio.navigation :as nav]))

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
    [nav/NavItem {:icon lucide/Home :label "Dashboard" :active false :collapsed collapsed :on-click #()}]
    [nav/NavItem {:icon lucide/Folder :label "Projects" :active true :collapsed collapsed :on-click #() :badge (str (count projects))}]
    [nav/NavItem {:icon lucide/History :label "Recent" :active false :collapsed collapsed :on-click #()}]]

   (when-not collapsed
     [:div.px-2.mt-2
      [:div.text-xs.font-medium.text-neutral-subdued-content.uppercase.tracking-wide.px-3.py-2
       "All Projects"]])

   [:div.flex-1.overflow-y-auto.p-2.flex.flex-col.gap-0.5
    (for [project projects]
      ^{:key (:id project)}
      [nav/ProjectItem {:project project
                        :active (= (:id project) (:id selected-project))
                        :collapsed collapsed
                        :on-click #(when on-select-project (on-select-project project))}])]

   [:div.p-2.border-t.border-gray-200
    [nav/NavItem {:icon lucide/Settings :label "Settings" :active false :collapsed collapsed :on-click #()}]]])

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
        [nav/SessionItem {:session session
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

(def sample-projects
  [{:id "1" :name "ccboard" :sessions [{:id "s1"} {:id "s2"} {:id "s3"}]}
   {:id "2" :name "my-awesome-project" :sessions [{:id "s4"} {:id "s5"}]}
   {:id "3" :name "empty-project" :sessions []}
   {:id "4" :name "another-project" :sessions [{:id "s6"}]}])

(def sample-sessions
  [{:id "session-1" :sessionId "abc123def456" :createdAt "2024/12/10 14:30"}
   {:id "session-2" :sessionId "xyz789ghi012" :createdAt "2024/12/10 13:15"}
   {:id "session-3" :sessionId "jkl345mno678" :createdAt "2024/12/09 16:45"}
   {:id "session-4" :sessionId "pqr901stu234" :createdAt "2024/12/09 10:00"}])

(defscene sidebar-default
  :title "Sidebar - Default"
  [:div.h-150.flex
   [Sidebar {:projects sample-projects
             :selected-project nil
             :collapsed false
             :on-select-project #(js/console.log "Selected:" %)}]])

(defscene sidebar-with-selection
  :title "Sidebar - With Selection"
  [:div.h-150.flex
   [Sidebar {:projects sample-projects
             :selected-project (first sample-projects)
             :collapsed false
             :on-select-project #(js/console.log "Selected:" %)}]])

(defscene sidebar-collapsed
  :title "Sidebar - Collapsed"
  [:div.h-150.flex
   [Sidebar {:projects sample-projects
             :selected-project (second sample-projects)
             :collapsed true
             :on-select-project #(js/console.log "Selected:" %)}]])

(defscene sidebar-empty
  :title "Sidebar - Empty"
  [:div.h-150.flex
   [Sidebar {:projects []
             :selected-project nil
             :collapsed false
             :on-select-project #(js/console.log "Selected:" %)}]])

(defscene sessions-panel-default
  :title "SessionsPanel - Default"
  [:div.h-150.flex
   [SessionsPanel {:project {:id "1" :name "ccboard"}
                   :sessions sample-sessions
                   :selected-session nil
                   :on-select-session #(js/console.log "Selected:" %)}]])

(defscene sessions-panel-with-selection
  :title "SessionsPanel - With Selection"
  [:div.h-150.flex
   [SessionsPanel {:project {:id "1" :name "ccboard"}
                   :sessions sample-sessions
                   :selected-session (second sample-sessions)
                   :on-select-session #(js/console.log "Selected:" %)}]])

(defscene sessions-panel-no-project
  :title "SessionsPanel - No Project"
  [:div.h-150.flex
   [SessionsPanel {:project nil
                   :sessions []
                   :selected-session nil
                   :on-select-session #(js/console.log "Selected:" %)}]])

(defscene sessions-panel-empty
  :title "SessionsPanel - Empty Sessions"
  [:div.h-150.flex
   [SessionsPanel {:project {:id "2" :name "empty-project"}
                   :sessions []
                   :selected-session nil
                   :on-select-session #(js/console.log "Selected:" %)}]])

(defscene messages-panel-default
  :title "MessagesPanel - Default"
  [:div.h-150.flex.flex-1
   [MessagesPanel {:session {:id "session-1"
                             :sessionId "abc123def456"
                             :createdAt "2024/12/10 14:30"}}]])

(defscene messages-panel-no-session
  :title "MessagesPanel - No Session"
  [:div.h-150.flex.flex-1
   [MessagesPanel {:session nil}]])
