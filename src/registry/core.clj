(ns registry.core
  (:require [clojure.tools.logging :as log]
            [zookeeper :as zk]
            [zookeeper.data :as zk-data]
            [zookeeper.util :as zk-util]
            [cheshire.core :refer [parse-string generate-string]]))

(def ^{:dynamic true} *serialize* #(generate-string %))
(def ^{:dynamic true} *deserialize* #(parse-string % true))

(def prefix "/registry")

(defprotocol Registry
  (register [this user-id valid-until cluster-node])
  (deregister [this user-id])
  (extend-timeout [this user-id valid-until])
  (deregister-all [this])
  (get-location [this user-id])
  (local? [this user-id local-node-name])
  (registered? [this user-id])
  (delete-all [this])
  (create-registry-group [this]))

;; in-memory

(deftype InMemoryRegistry [store]
  Registry
  (register [this user-id valid-until cluster-node]
    (log/info "register in-memory")
    (let [user {:user-id user-id :valid-until valid-until :node cluster-node}]
      (swap! store update-in [user-id]
             #(if %
                (throw
                 (Exception. (format "session_already_registered, args=[%s]" user-id)))
                user)))
    (log/info "register in-memory" @store)
    nil)

  (deregister [this user-id]
    (log/info "deregister" user-id)
    (swap! store dissoc user-id)
    nil)

  (extend-timeout [this user-id valid-until]
    (swap! store assoc-in [user-id :valid-until] valid-until)
    nil)

  (deregister-all [this]
    (log/info "deregister-all")
    (reset! store {})
    nil)

  (get-location [this user-id]
    (let [user (clojure.core/get @store user-id)]
      (log/info "get-location" user)
      (:node user)))

  (local? [this user-id local-node-name]
    (let [location (get-location this user-id)]
      (log/info "local?" location local-node-name)
      (= location local-node-name)))

  (registered? [this user-id]
    (log/info "registered?" user-id)
    (let [user (clojure.core/get @store user-id)]
      (not (nil? user))))

  (create-registry-group [this]
    (log/info "create-registry-group")
    nil))

;; zookeeper

(defn to-zk-location
  [user-id & z-nodes]
  (str prefix "/" user-id (clojure.string/join "/" z-nodes)))

(defn get-node-data [client user-id]
  (let [response (zk/data client (to-zk-location user-id))]
    (*deserialize* (:data response))))

(deftype ZooKeeperRegistry [client]
  Registry
  (register [this user-id valid-until cluster-node]
    (log/info "register zookeeper")
    (let [user-znode (to-zk-location user-id)
          lock-znode (str user-znode "/_lock-")]
      (let [create-response (zk/create-all client lock-znode
                                           :persistent? true :sequential? true)
            create-id (zk-util/extract-id create-response)
            user-node-response (zk/exists client user-znode)]
        (if (= 0 create-id)
          (zk/set-data client user-znode
                       (*serialize* {:valid-until valid-until :node cluster-node})
                       (:version user-node-response))
          (throw
           (Exception. (format "session_already_registered, args=[%s]" user-id)))))))

  (deregister [this user-id]
    (log/info "deregister" user-id (to-zk-location user-id))
    (zk/delete-all client (to-zk-location user-id)))

  (extend-timeout [this user-id valid-until]
    (let [zk-location (to-zk-location user-id)
          response (zk/exists client zk-location)
          data (*deserialize* (:data response))
          new-data (assoc-in data :valid-until valid-until)]
      (zk/set-data client zk-location (*serialize* new-data)
                   (:version response))))

  (deregister-all [this]
    (log/info "deregister-all")
    (zk/delete-all client prefix))

  (get-location [this user-id]
    (log/info "get-location")
    (:node (get-node-data client user-id)))

  (local? [this user-id local-node-name]
    (log/info "local?")
    (= (:node (get-node-data this user-id)) local-node-name))

  (registered? [this user-id]
    (log/info "registered?" user-id)
    (not (nil? (get-node-data this user-id))))

  (create-registry-group [this]
    (log/info "create-registry-group")
    (when (nil? (zk/exists client prefix))
      (zk/create client prefix :persistent? true))))

(defmulti start :type)

(defmethod start :in-memory
  [& args]
  (InMemoryRegistry. (atom {})))

(defmethod start :zk
  [host port]
  (ZooKeeperRegistry. (zk/connect (str host ":" port))))
