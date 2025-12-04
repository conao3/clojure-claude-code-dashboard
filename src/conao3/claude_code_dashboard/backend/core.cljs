(ns conao3.claude-code-dashboard.backend.core
  (:require
   ["@apollo/server" :as apollo]
   ["@apollo/server/standalone" :as apollo.standalone]
   ["fs" :as fs]
   ["path" :as path]))

(defonce server-state (atom nil))

(defn load-type-defs []
  (fs/readFileSync (path/join js/__dirname ".." ".." "resources" "schema.graphql") "utf-8"))

(def resolvers
  #js {:Query #js {:hello (fn [] "Hello from Apollo Server!")}})

(defn start-server []
  (let [server (apollo/ApolloServer. #js {:typeDefs (load-type-defs) :resolvers resolvers})]
    (-> (apollo.standalone/startStandaloneServer server #js {:listen #js {:port 4000}})
        (.then (fn [res]
                 (reset! server-state {:server server :url (.-url res)})
                 (println (str "Server ready at " (.-url res))))))))

(defn stop-server []
  (when-let [{:keys [server]} @server-state]
    (-> (.stop server)
        (.then (fn []
                 (reset! server-state nil)
                 (println "Server stopped"))))))

(defn ^:dev/after-load reload []
  (println "Reloading...")
  (-> (stop-server)
      (.then start-server)))

(defn main [& _args]
  (start-server))
