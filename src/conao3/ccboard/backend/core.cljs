(ns conao3.ccboard.backend.core
  (:require
   ["@apollo/server" :as apollo]
   ["@apollo/server/plugin/disabled" :as apollo.plugin.disabled]
   ["@apollo/server/plugin/landingPage/default" :as apollo.landing]
   ["@as-integrations/express5" :as apollo.express]
   ["cors" :as cors]
   ["express" :as express]
   ["node:fs" :as fs]
   ["node:os" :as os]
   ["node:path" :as path]
   [clojure.string :as str]
   [conao3.ccboard.lib :as c.lib]
   [conao3.ccboard.util :as c.util]
   [schema.core :as s]
   [shadow.resource :as shadow.resource]))

(defonce server-state (atom nil))

(when goog.DEBUG
  (s/set-fn-validation! true))

(defn ^:private stringify [x]
  (js/JSON.stringify (clj->js x)))

(defn ^:private projects-dir []
  (.join path (.homedir os) ".claude" "projects"))

(defn ^:private read-claude-json []
  (let [claude-json-path (.join path (.homedir os) ".claude.json")]
    (-> (.readFileSync fs claude-json-path "utf-8")
        (js/JSON.parse)
        (js->clj :keywordize-keys true))))

(defn ^:private list-sessions [project-id]
  (let [project-dir (.join path (projects-dir) project-id)
        files (try (js->clj (.readdirSync fs project-dir)) (catch :default _ []))]
    (->> files
         (filter #(and (str/ends-with? % ".jsonl") (not (str/starts-with? % "agent-"))))
         (map (fn [filename]
                (let [session-id (str/replace filename ".jsonl" "")
                      file-path (.join path project-dir filename)
                      stat (.statSync fs file-path)
                      created-at (.toISOString (.-birthtime stat))]
                  (c.lib/make-session project-id session-id created-at))))
         (sort-by :createdAt)
         reverse
         vec)))

(defn ^:private list-projects []
  (c.lib/claude-json->projects (read-claude-json)))

(defn ^:private parse-message-line [project-id session-id idx line]
  (try
    (let [parsed-data (js->clj (js/JSON.parse line) :keywordize-keys true)
          message-id (or (:uuid parsed-data) (:messageId parsed-data) (str idx))]
      (c.lib/parse-message parsed-data project-id session-id message-id idx line stringify))
    (catch :default _e
      (c.lib/parse-broken-message project-id session-id idx line))))

(defn ^:private list-messages [project-id session-id]
  (let [file-path (.join path (projects-dir) project-id (str session-id ".jsonl"))
        content (try (.readFileSync fs file-path "utf-8") (catch :default _ ""))
        lines (->> (str/split content #"\n") (filter #(not= % "")))]
    (->> lines
         (map-indexed (fn [idx line] (parse-message-line project-id session-id idx line)))
         vec)))

(defn ^:private js-args->pagination-args [^js args]
  {:first-n (.-first args)
   :after-cursor (.-after args)
   :last-n (.-last args)
   :before-cursor (.-before args)})

(defn ^:private node-resolver [^js args]
  (let [{:keys [type raw-id]} (c.util/decode-id (.-id args))]
    (case type
      "Project" (c.lib/find-project-by-id (list-projects) raw-id)
      "Session" (let [[project-id session-id] (str/split raw-id #"/")
                      file-path (.join path (projects-dir) project-id (str session-id ".jsonl"))]
                  (when (.existsSync fs file-path)
                    (let [stat (.statSync fs file-path)]
                      (c.lib/make-session project-id session-id (.toISOString (.-birthtime stat))))))
      "Message" (let [[project-id session-id message-id] (str/split raw-id #"/")
                      file-path (.join path (projects-dir) project-id (str session-id ".jsonl"))
                      content (try (.readFileSync fs file-path "utf-8") (catch :default _ ""))
                      lines (->> (str/split content #"\n") (filter #(not= % "")))
                      idx (->> lines
                               (keep-indexed (fn [i l]
                                               (let [parsed-data (js->clj (js/JSON.parse l) :keywordize-keys true)]
                                                 (when (= message-id (or (:uuid parsed-data) (:messageId parsed-data)))
                                                   i))))
                               first)]
                  (when idx
                    (parse-message-line project-id session-id idx (nth lines idx))))
      nil)))

(def resolvers
  {"Query" {"hello" (fn [] "Hello from Apollo Server!")
            "projects" (fn [_ ^js args]
                         (-> (list-projects)
                             (c.util/paginate (js-args->pagination-args args))
                             clj->js))
            "node" (fn [_ args] (clj->js (node-resolver args)))}
   "Node" {"__resolveType" (fn [obj] (aget obj "__typename"))}
   "Project" {"sessions"
              (fn [parent ^js args]
                (-> (list-sessions (aget parent "projectId"))
                    (c.util/paginate (js-args->pagination-args args))
                    clj->js))}
   "Session" {"messages"
              (fn [parent ^js args]
                (-> (list-messages (aget parent "projectId") (aget parent "sessionId"))
                    (c.util/paginate (js-args->pagination-args args))
                    clj->js))}
   "Message" {"__resolveType" (fn [obj] (aget obj "__typename"))}})

(defn ^:private get-public-dir []
  ;; In release mode, static files are at ../public relative to this script
  (.join path js/__dirname ".." "public"))

(s/defn start-server :- (s/eq nil)
  []
  (let [type-defs (shadow.resource/inline "schema.graphql")
        api-server (apollo/ApolloServer. (clj->js {:typeDefs type-defs
                                                   :resolvers resolvers
                                                   :plugins [(apollo.plugin.disabled/ApolloServerPluginLandingPageDisabled)]}))
        admin-server (apollo/ApolloServer. (clj->js {:typeDefs type-defs
                                                     :resolvers resolvers
                                                     :plugins [(apollo.landing/ApolloServerPluginLandingPageLocalDefault)]}))
        app (express)
        port (if goog.DEBUG 4000 3000)]
    (-> (js/Promise.all #js [(.start api-server) (when goog.DEBUG (.start admin-server))])
        (.then (fn []
                 (.use app "/api/graphql" (cors) (express/json) (apollo.express/expressMiddleware api-server))
                 (when goog.DEBUG
                   (.use app "/admin/apollo" (cors) (express/json) (apollo.express/expressMiddleware admin-server)))
                 ;; In release mode, serve static files
                 (when-not goog.DEBUG
                   (let [public-dir (get-public-dir)]
                     (.use app (express/static public-dir))
                     ;; Serve index.html for all non-API routes (SPA support)
                     ;; Express 5 requires named wildcard parameter
                     (.get app "{*path}" (fn [_req ^js res]
                                           (.sendFile res (.join path public-dir "index.html"))))))
                 (let [server (.listen app port)]
                   (reset! server-state {:server server :api-server api-server :admin-server admin-server})
                   (if goog.DEBUG
                     (do
                       (println "Server ready at http://localhost:4000/api/graphql")
                       (println "Apollo Sandbox at http://localhost:4000/admin/apollo"))
                     (println (str "ccboard running at http://localhost:" port)))
                   nil)))))
  nil)

(s/defn stop-server :- (s/eq nil)
  []
  (when-let [{:keys [server api-server admin-server]} @server-state]
    (-> (js/Promise.all #js [(.stop api-server) (when admin-server (.stop admin-server))])
        (.then (fn []
                 (.close server)
                 (reset! server-state nil)
                 (println "Server stopped")
                 nil))))
  nil)

(s/defn reload :- (s/eq nil)
  {:dev/after-load true}
  []
  (println "Reloading...")
  (-> (stop-server)
      (.then start-server))
  nil)

(s/defn main :- (s/eq nil)
  [& _args :- [s/Any]]
  (start-server)
  nil)
