(ns conao3.claude-code-dashboard.backend.core
  (:require
   ["@anthropic-ai/claude-agent-sdk" :as claude]
   ["@apollo/server" :as apollo]
   ["@apollo/server/plugin/disabled" :as apollo.plugin.disabled]
   ["@apollo/server/plugin/landingPage/default" :as apollo.landing]
   ["@as-integrations/express5" :as apollo.express]
   ["cors" :as cors]
   ["express" :as express]
   ["node:fs" :as fs]
   ["node:os" :as os]
   ["node:path" :as path]
   [shadow.resource :as shadow.resource]))

(defonce server-state (atom nil))

(defn- encode-id [type raw-id]
  (js/btoa (str type ":" raw-id)))

(defn- decode-id [id]
  (let [[type raw-id] (.split (js/atob id) ":")]
    {:type type :raw-id raw-id}))

(defn- read-claude-json []
  (let [claude-json-path (.join path (.homedir os) ".claude.json")]
    (-> (.readFileSync fs claude-json-path "utf-8")
        (js/JSON.parse)
        (js->clj :keywordize-keys true))))

(defn- path->slug [p]
  (.replace p (js/RegExp. "/" "g") "-"))

(defn- projects-dir []
  (.join path (.homedir os) ".claude" "projects"))

(defn- list-sessions [project-id]
  (let [project-dir (.join path (projects-dir) project-id)
        files (try (js->clj (.readdirSync fs project-dir)) (catch :default _ []))]
    (->> files
         (filter #(and (.endsWith % ".jsonl") (not (.startsWith % "agent-"))))
         (map (fn [filename]
                (let [session-id (.replace filename ".jsonl" "")
                      file-path (.join path project-dir filename)
                      stat (.statSync fs file-path)
                      created-at (.toISOString (.-birthtime stat))]
                  {:id (encode-id "Session" (str project-id "/" session-id))
                   :projectId project-id
                   :sessionId session-id
                   :createdAt created-at})))
         (sort-by :createdAt)
         reverse)))

(defn- list-projects []
  (let [claude-json (read-claude-json)]
    (->> (:projects claude-json)
         (map (fn [[k _v]]
                (let [project-path (subs (str k) 1)]
                  {:id (encode-id "Project" project-path)
                   :projectId (path->slug project-path)
                   :name project-path}))))))

(defn- sessions-resolver [parent]
  (let [project-id (aget parent "projectId")
        sessions (list-sessions project-id)]
    #js {:edges (clj->js (map (fn [s] {:cursor (:id s) :node s}) sessions))
         :pageInfo #js {:hasNextPage false
                        :hasPreviousPage false
                        :startCursor (some-> (first sessions) :id)
                        :endCursor (some-> (last sessions) :id)}}))

(defn- list-messages [project-id session-id]
  (let [file-path (.join path (projects-dir) project-id (str session-id ".jsonl"))
        content (try (.readFileSync fs file-path "utf-8") (catch :default _ ""))
        lines (->> (.split content "\n") (filter #(not= % "")))]
    (->> lines
         (map-indexed (fn [idx line]
                        (let [data (js->clj (js/JSON.parse line) :keywordize-keys true)
                              message-id (or (:uuid data) (:messageId data) (str idx))]
                          {:id (encode-id "Message" (str project-id "/" session-id "/" message-id))
                           :projectId project-id
                           :sessionId session-id
                           :messageId message-id
                           :rawMessage line}))))))

(defn- messages-resolver [parent]
  (let [project-id (aget parent "projectId")
        session-id (aget parent "sessionId")
        messages (list-messages project-id session-id)]
    #js {:edges (clj->js (map (fn [m] {:cursor (:id m) :node m}) messages))
         :pageInfo #js {:hasNextPage false
                        :hasPreviousPage false
                        :startCursor (some-> (first messages) :id)
                        :endCursor (some-> (last messages) :id)}}))

(def resolvers
  (clj->js
   {"Query" {"hello" (fn [] "Hello from Apollo Server!")
             "projects" (fn []
                          (let [projects (list-projects)]
                            #js {:edges (clj->js (map (fn [p] {:cursor (:id p) :node p}) projects))
                                 :pageInfo #js {:hasNextPage false
                                                :hasPreviousPage false
                                                :startCursor (some-> (first projects) :id)
                                                :endCursor (some-> (last projects) :id)}}))
             "node" (fn [_ args]
                      (let [{:keys [type raw-id]} (decode-id (.-id args))]
                        (case type
                          "Project" (let [claude-json (read-claude-json)
                                          project-path raw-id]
                                      (when (get-in claude-json [:projects (keyword project-path)])
                                        #js {:__typename "Project"
                                             :id (.-id args)
                                             :projectId (path->slug project-path)
                                             :name project-path}))
                          "Session" (let [[project-id session-id] (.split raw-id "/")
                                          file-path (.join path (projects-dir) project-id (str session-id ".jsonl"))]
                                      (when (.existsSync fs file-path)
                                        (let [stat (.statSync fs file-path)]
                                          #js {:__typename "Session"
                                               :id (.-id args)
                                               :projectId project-id
                                               :sessionId session-id
                                               :createdAt (.toISOString (.-birthtime stat))})))
                          "Message" (let [[project-id session-id message-id] (.split raw-id "/")
                                          file-path (.join path (projects-dir) project-id (str session-id ".jsonl"))
                                          content (try (.readFileSync fs file-path "utf-8") (catch :default _ ""))
                                          lines (->> (.split content "\n") (filter #(not= % "")))
                                          line (->> lines
                                                    (filter (fn [l]
                                                              (let [data (js->clj (js/JSON.parse l) :keywordize-keys true)]
                                                                (= message-id (or (:uuid data) (:messageId data))))))
                                                    first)]
                                      (when line
                                        #js {:__typename "Message"
                                             :id (.-id args)
                                             :projectId project-id
                                             :sessionId session-id
                                             :messageId message-id
                                             :rawMessage line}))
                          nil)))}
    "Project" {"sessions" sessions-resolver}
    "Session" {"messages" messages-resolver}}))

(defn start-server []
  (let [type-defs (shadow.resource/inline "schema.graphql")
        api-server (apollo/ApolloServer. #js {:typeDefs type-defs
                                              :resolvers resolvers
                                              :plugins #js [(apollo.plugin.disabled/ApolloServerPluginLandingPageDisabled)]})
        admin-server (apollo/ApolloServer. #js {:typeDefs type-defs
                                                :resolvers resolvers
                                                :plugins #js [(apollo.landing/ApolloServerPluginLandingPageLocalDefault)]})
        app (express)]
    (-> (js/Promise.all #js [(.start api-server) (when goog.DEBUG (.start admin-server))])
        (.then (fn []
                 (.use app "/api/graphql" (cors) (express/json) (apollo.express/expressMiddleware api-server))
                 (when goog.DEBUG
                   (.use app "/admin/apollo" (cors) (express/json) (apollo.express/expressMiddleware admin-server)))
                 (let [server (.listen app 4000)]
                   (reset! server-state {:server server :api-server api-server :admin-server admin-server})
                   (println "Server ready at http://localhost:4000/api/graphql")
                   (when goog.DEBUG
                     (println "Apollo Sandbox at http://localhost:4000/admin/apollo"))))))))

(defn stop-server []
  (when-let [{:keys [server api-server admin-server]} @server-state]
    (-> (js/Promise.all #js [(.stop api-server) (when admin-server (.stop admin-server))])
        (.then (fn []
                 (.close server)
                 (reset! server-state nil)
                 (println "Server stopped"))))))

(defn ^:dev/after-load reload []
  (println "Reloading...")
  (-> (stop-server)
      (.then start-server)))

(defn main [& _args]
  (start-server))
