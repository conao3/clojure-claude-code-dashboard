(ns conao3.ccboard.portfolio.navigation
  (:require
   ["lucide-react" :as lucide]
   ["react-aria-components" :as rac]
   [portfolio.reagent-18 :refer-macros [defscene]]))

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
        [:span.5.5.min-w-5.rounded-full.bg-accent-background.px-1.py-0.text-center.text-xs.text-white badge])])])

(defn ProjectItem [{:keys [project is-selected collapsed on-press]}]
  (let [session-count (count (:sessions project))]
    [:> rac/Button
     {:className (str "flex items-center gap-2 w-full rounded transition-all outline-none "
                      (if collapsed "p-2 justify-center" "px-3 py-2 justify-start")
                      (if is-selected " bg-accent-background/15 text-neutral-content" " text-neutral-subdued-content hover:text-neutral-content"))
      :onPress on-press
      :isDisabled (zero? session-count)}
     [:> lucide/GitBranch {:size 14 :className "flex-shrink-0 text-neutral-subdued-content"}]
     (when-not collapsed
       [:span.flex-1.truncate.text-left.text-sm (:name project)])
     (when (and (not collapsed) (pos? session-count))
       [:span.text-xs.text-neutral-subdued-content session-count])]))

(defn SessionItem [{:keys [session active on-click]}]
  [:> rac/Button
   {:className (str "w-full text-left p-3 border-b border-gray-200 outline-none transition-all "
                    (if active
                      "bg-accent-background/10 border-l-3 border-l-accent-background"
                      "border-l-3 border-l-transparent hover:bg-background-layer-1"))
    :onPress on-click}
   [:div.mb-1.flex.items-center.gap-2
    [:span.h-2.w-2.flex-shrink-0.rounded-full.bg-positive-visual]
    [:span.flex-1.truncate.text-sm.font-medium.text-neutral-content
     (:sessionId session)]]
   [:div.text-xs.text-neutral-subdued-content
    (:createdAt session)]])

(defscene nav-item-default
  :title "NavItem - Default"
  [:div.w-60.bg-background-layer-1.p-4
   [NavItem {:icon lucide/Home
             :label "Dashboard"
             :active false
             :collapsed false
             :on-click #(js/console.log "clicked")}]])

(defscene nav-item-active
  :title "NavItem - Active"
  [:div.w-60.bg-background-layer-1.p-4
   [NavItem {:icon lucide/Folder
             :label "Projects"
             :active true
             :collapsed false
             :on-click #(js/console.log "clicked")}]])

(defscene nav-item-with-badge
  :title "NavItem - With Badge"
  [:div.w-60.bg-background-layer-1.p-4
   [NavItem {:icon lucide/Folder
             :label "Projects"
             :active true
             :collapsed false
             :badge "12"
             :on-click #(js/console.log "clicked")}]])

(defscene nav-item-collapsed
  :title "NavItem - Collapsed"
  [:div.w-16.bg-background-layer-1.p-4
   [NavItem {:icon lucide/Settings
             :label "Settings"
             :active false
             :collapsed true
             :on-click #(js/console.log "clicked")}]])

(defscene nav-item-collapsed-active
  :title "NavItem - Collapsed Active"
  [:div.w-16.bg-background-layer-1.p-4
   [NavItem {:icon lucide/Folder
             :label "Projects"
             :active true
             :collapsed true
             :on-click #(js/console.log "clicked")}]])

(defscene project-item-default
  :title "ProjectItem - Default"
  [:div.w-60.bg-background-layer-1.p-4
   [ProjectItem {:project {:id "1"
                           :name "ccboard"
                           :sessions [{:id "s1"} {:id "s2"} {:id "s3"}]}
                 :is-selected false
                 :collapsed false
                 :on-press #(js/console.log "clicked")}]])

(defscene project-item-active
  :title "ProjectItem - Active"
  [:div.w-60.bg-background-layer-1.p-4
   [ProjectItem {:project {:id "1"
                           :name "ccboard"
                           :sessions [{:id "s1"} {:id "s2"}]}
                 :is-selected true
                 :collapsed false
                 :on-press #(js/console.log "clicked")}]])

(defscene project-item-no-sessions
  :title "ProjectItem - No Sessions (Disabled)"
  [:div.w-60.bg-background-layer-1.p-4
   [ProjectItem {:project {:id "2"
                           :name "empty-project"
                           :sessions []}
                 :is-selected false
                 :collapsed false
                 :on-press #(js/console.log "clicked")}]])

(defscene project-item-long-name
  :title "ProjectItem - Long Name"
  [:div.w-60.bg-background-layer-1.p-4
   [ProjectItem {:project {:id "3"
                           :name "very-long-project-name-that-should-be-truncated"
                           :sessions [{:id "s1"} {:id "s2"} {:id "s3"} {:id "s4"} {:id "s5"}]}
                 :is-selected false
                 :collapsed false
                 :on-press #(js/console.log "clicked")}]])

(defscene session-item-default
  :title "SessionItem - Default"
  [:div.w-72.bg-background-base
   [SessionItem {:session {:id "session-1"
                           :sessionId "abc123def456"
                           :createdAt "2024/12/10 14:30"}
                 :active false
                 :on-press #(js/console.log "clicked")}]])

(defscene session-item-active
  :title "SessionItem - Active"
  [:div.w-72.bg-background-base
   [SessionItem {:session {:id "session-2"
                           :sessionId "xyz789ghi012"
                           :createdAt "2024/12/10 15:45"}
                 :active true
                 :on-press #(js/console.log "clicked")}]])

(defscene session-item-long-id
  :title "SessionItem - Long ID"
  [:div.w-72.bg-background-base
   [SessionItem {:session {:id "session-3"
                           :sessionId "very-long-session-id-that-should-be-truncated-properly"
                           :createdAt "2024/12/09 09:15"}
                 :active false
                 :on-press #(js/console.log "clicked")}]])
