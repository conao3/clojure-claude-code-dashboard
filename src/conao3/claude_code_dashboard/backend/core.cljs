(ns conao3.claude-code-dashboard.backend.core
  (:require
   ["@apollo/server" :as apollo]
   ["@apollo/server/plugin/disabled" :as apollo.plugin.disabled]
   ["@apollo/server/plugin/landingPage/default" :as apollo.landing]
   ["@as-integrations/express5" :as apollo.express]
   ["cors" :as cors]
   ["express" :as express]
   ["fs" :as fs]
   ["path" :as path]))

(defonce server-state (atom nil))

(defn load-type-defs []
  (fs/readFileSync (path/join js/__dirname ".." ".." "resources" "schema.graphql") "utf-8"))

(def resolvers
  #js {:Query #js {:hello (fn [] "Hello from Apollo Server!")}})

(defn start-server []
  (let [api-server (apollo/ApolloServer. #js {:typeDefs (load-type-defs)
                                              :resolvers resolvers
                                              :plugins #js [(apollo.plugin.disabled/ApolloServerPluginLandingPageDisabled)]})
        admin-server (apollo/ApolloServer. #js {:typeDefs (load-type-defs)
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
