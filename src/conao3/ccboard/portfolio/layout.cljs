(ns conao3.ccboard.portfolio.layout
  (:require
   ["lucide-react" :as lucide]
   ["react-aria-components" :as rac]
   [conao3.ccboard.portfolio.navigation :as nav]
   [portfolio.reagent-18 :refer-macros [defscene]]))

(defn Sidebar [{:keys [projects selected-project on-select-project collapsed]}]
  [:div {:class (str "flex flex-col bg-background-layer-1 border-r border-gray-200 transition-all duration-200 "
                     (if collapsed "w-16" "w-60"))}
   [:div.flex.items-center.border-b.border-gray-200.p-4
    {:class (if collapsed "justify-center" "justify-between")}
    [:div.5.flex.items-center.gap-2
     [:div.flex.h-8.w-8.flex-shrink-0.items-center.justify-center.rounded-lg
      {:class "bg-gradient-to-br from-purple-600 to-blue-600"}
      [:> lucide/Zap {:size 18 :className "text-white"}]]
     (when-not collapsed
       [:span.font-semibold.text-neutral-content "Claude Code"])]
    (when-not collapsed
      [:> rac/Button
       {:className "p-1 text-neutral-subdued-content outline-none hover:text-neutral-content"
        :onPress #()}
       [:> lucide/PanelLeftClose {:size 18}]])]

   (when collapsed
     [:> rac/Button
      {:className "mx-auto my-2 p-3 text-neutral-subdued-content outline-none hover:text-neutral-content"
       :onPress #()}
      [:> lucide/PanelLeft {:size 18}]])

   [:div.flex.flex-col.gap-1.p-2
    [nav/NavItem {:icon lucide/Home :label "Dashboard" :active false :collapsed collapsed :on-click #()}]
    [nav/NavItem {:icon lucide/Folder :label "Projects" :active true :collapsed collapsed :on-click #() :badge (str (count projects))}]
    [nav/NavItem {:icon lucide/History :label "Recent" :active false :collapsed collapsed :on-click #()}]]

   (when-not collapsed
     [:div.mt-2.px-2
      [:div.px-3.py-2.text-xs.font-medium.tracking-wide.text-neutral-subdued-content.uppercase
       "All Projects"]])

   [:div.5.flex.flex-1.flex-col.gap-0.overflow-y-auto.p-2
    (for [project projects]
      ^{:key (:id project)}
      [nav/ProjectItem {:project project
                        :active (= (:id project) (:id selected-project))
                        :collapsed collapsed
                        :on-click #(when on-select-project (on-select-project project))}])]

   [:div.border-t.border-gray-200.p-2
    [nav/NavItem {:icon lucide/Settings :label "Settings" :active false :collapsed collapsed :on-click #()}]]])

(defn SessionsPanel [{:keys [project sessions on-select-session selected-session]}]
  [:div.flex.w-72.flex-col.border-r.border-gray-200.bg-background-base
   [:div.flex.items-center.justify-between.border-b.border-gray-200.p-4
    [:h2.truncate.text-base.font-semibold.text-neutral-content
     (if project (:name project) "Sessions")]
    [:> rac/Button
     {:className "p-1 text-neutral-subdued-content outline-none hover:text-neutral-content"}
     [:> lucide/MoreHorizontal {:size 18}]]]

   [:div.p-3
    [:div.relative
     [:> lucide/Search {:size 14 :className "absolute top-1/2 left-2.5 -translate-y-1/2 text-neutral-subdued-content"}]
     [:input
      {:type "text"
       :placeholder "Search sessions..."
       :class "w-full rounded-md border border-gray-300 bg-background-layer-1 py-2 pr-3 pl-8 text-sm text-neutral-content outline-none focus:border-accent-background"}]]]

   [:div.flex-1.overflow-y-auto
    (if (empty? sessions)
      [:p.p-4.text-sm.text-neutral-subdued-content "Select a project"]
      (for [session sessions]
        ^{:key (:id session)}
        [nav/SessionItem {:session session
                          :active (= (:id session) (:id selected-session))
                          :on-click #(when on-select-session (on-select-session session))}]))]])

(defn MessagesPanel [{:keys [session]}]
  [:div.flex.min-h-0.flex-1.flex-col.overflow-hidden.bg-background-base
   [:div.shrink-0.border-b.border-gray-200.p-4
    [:h2.truncate.text-lg.font-semibold.text-neutral-content
     (or (:sessionId session) "Messages")]
    (when session
      [:div.mt-1.text-sm.text-neutral-subdued-content
       (:createdAt session)])]
   [:div.flex.flex-1.items-center.justify-center.text-neutral-subdued-content
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
  [:div.flex.h-150
   [Sidebar {:projects sample-projects
             :selected-project nil
             :collapsed false
             :on-select-project #(js/console.log "Selected:" %)}]])

(defscene sidebar-with-selection
  :title "Sidebar - With Selection"
  [:div.flex.h-150
   [Sidebar {:projects sample-projects
             :selected-project (first sample-projects)
             :collapsed false
             :on-select-project #(js/console.log "Selected:" %)}]])

(defscene sidebar-collapsed
  :title "Sidebar - Collapsed"
  [:div.flex.h-150
   [Sidebar {:projects sample-projects
             :selected-project (second sample-projects)
             :collapsed true
             :on-select-project #(js/console.log "Selected:" %)}]])

(defscene sidebar-empty
  :title "Sidebar - Empty"
  [:div.flex.h-150
   [Sidebar {:projects []
             :selected-project nil
             :collapsed false
             :on-select-project #(js/console.log "Selected:" %)}]])

(defscene sessions-panel-default
  :title "SessionsPanel - Default"
  [:div.flex.h-150
   [SessionsPanel {:project {:id "1" :name "ccboard"}
                   :sessions sample-sessions
                   :selected-session nil
                   :on-select-session #(js/console.log "Selected:" %)}]])

(defscene sessions-panel-with-selection
  :title "SessionsPanel - With Selection"
  [:div.flex.h-150
   [SessionsPanel {:project {:id "1" :name "ccboard"}
                   :sessions sample-sessions
                   :selected-session (second sample-sessions)
                   :on-select-session #(js/console.log "Selected:" %)}]])

(defscene sessions-panel-no-project
  :title "SessionsPanel - No Project"
  [:div.flex.h-150
   [SessionsPanel {:project nil
                   :sessions []
                   :selected-session nil
                   :on-select-session #(js/console.log "Selected:" %)}]])

(defscene sessions-panel-empty
  :title "SessionsPanel - Empty Sessions"
  [:div.flex.h-150
   [SessionsPanel {:project {:id "2" :name "empty-project"}
                   :sessions []
                   :selected-session nil
                   :on-select-session #(js/console.log "Selected:" %)}]])

(defscene messages-panel-default
  :title "MessagesPanel - Default"
  [:div.flex.h-150.flex-1
   [MessagesPanel {:session {:id "session-1"
                             :sessionId "abc123def456"
                             :createdAt "2024/12/10 14:30"}}]])

(defscene messages-panel-no-session
  :title "MessagesPanel - No Session"
  [:div.flex.h-150.flex-1
   [MessagesPanel {:session nil}]])
