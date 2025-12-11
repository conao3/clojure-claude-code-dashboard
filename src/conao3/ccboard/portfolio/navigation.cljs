(ns conao3.ccboard.portfolio.navigation
  (:require
   [portfolio.reagent-18 :refer-macros [defscene]]
   ["lucide-react" :as lucide]
   ["react-aria-components" :as rac]))

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

(defscene nav-item-default
  :title "NavItem - Default"
  [:div.bg-background-layer-1.p-4.w-60
   [NavItem {:icon lucide/Home
             :label "Dashboard"
             :active false
             :collapsed false
             :on-click #(js/console.log "clicked")}]])

(defscene nav-item-active
  :title "NavItem - Active"
  [:div.bg-background-layer-1.p-4.w-60
   [NavItem {:icon lucide/Folder
             :label "Projects"
             :active true
             :collapsed false
             :on-click #(js/console.log "clicked")}]])

(defscene nav-item-with-badge
  :title "NavItem - With Badge"
  [:div.bg-background-layer-1.p-4.w-60
   [NavItem {:icon lucide/Folder
             :label "Projects"
             :active true
             :collapsed false
             :badge "12"
             :on-click #(js/console.log "clicked")}]])

(defscene nav-item-collapsed
  :title "NavItem - Collapsed"
  [:div.bg-background-layer-1.p-4.w-16
   [NavItem {:icon lucide/Settings
             :label "Settings"
             :active false
             :collapsed true
             :on-click #(js/console.log "clicked")}]])

(defscene nav-item-collapsed-active
  :title "NavItem - Collapsed Active"
  [:div.bg-background-layer-1.p-4.w-16
   [NavItem {:icon lucide/Folder
             :label "Projects"
             :active true
             :collapsed true
             :on-click #(js/console.log "clicked")}]])

(defscene project-item-default
  :title "ProjectItem - Default"
  [:div.bg-background-layer-1.p-4.w-60
   [ProjectItem {:project {:id "1"
                           :name "ccboard"
                           :sessions [{:id "s1"} {:id "s2"} {:id "s3"}]}
                 :active false
                 :collapsed false
                 :on-click #(js/console.log "clicked")}]])

(defscene project-item-active
  :title "ProjectItem - Active"
  [:div.bg-background-layer-1.p-4.w-60
   [ProjectItem {:project {:id "1"
                           :name "ccboard"
                           :sessions [{:id "s1"} {:id "s2"}]}
                 :active true
                 :collapsed false
                 :on-click #(js/console.log "clicked")}]])

(defscene project-item-no-sessions
  :title "ProjectItem - No Sessions (Disabled)"
  [:div.bg-background-layer-1.p-4.w-60
   [ProjectItem {:project {:id "2"
                           :name "empty-project"
                           :sessions []}
                 :active false
                 :collapsed false
                 :on-click #(js/console.log "clicked")}]])

(defscene project-item-long-name
  :title "ProjectItem - Long Name"
  [:div.bg-background-layer-1.p-4.w-60
   [ProjectItem {:project {:id "3"
                           :name "very-long-project-name-that-should-be-truncated"
                           :sessions [{:id "s1"} {:id "s2"} {:id "s3"} {:id "s4"} {:id "s5"}]}
                 :active false
                 :collapsed false
                 :on-click #(js/console.log "clicked")}]])

(defscene session-item-default
  :title "SessionItem - Default"
  [:div.bg-background-base.w-72
   [SessionItem {:session {:id "session-1"
                           :sessionId "abc123def456"
                           :createdAt "2024/12/10 14:30"}
                 :active false
                 :on-click #(js/console.log "clicked")}]])

(defscene session-item-active
  :title "SessionItem - Active"
  [:div.bg-background-base.w-72
   [SessionItem {:session {:id "session-2"
                           :sessionId "xyz789ghi012"
                           :createdAt "2024/12/10 15:45"}
                 :active true
                 :on-click #(js/console.log "clicked")}]])

(defscene session-item-long-id
  :title "SessionItem - Long ID"
  [:div.bg-background-base.w-72
   [SessionItem {:session {:id "session-3"
                           :sessionId "very-long-session-id-that-should-be-truncated-properly"
                           :createdAt "2024/12/09 09:15"}
                 :active false
                 :on-click #(js/console.log "clicked")}]])
