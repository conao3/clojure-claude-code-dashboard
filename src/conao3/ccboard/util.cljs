(ns conao3.ccboard.util
  (:require
   [clojure.string :as str]))

(defn encode-id [type raw-id]
  (js/btoa (str type ":" raw-id)))

(defn decode-id [id]
  (let [[type raw-id] (str/split (js/atob id) #":")]
    {:type type :raw-id raw-id}))

(defn path->slug [p]
  (str/replace p "/" "-"))

(defn find-cursor-idx [items cursor]
  (when cursor
    (->> items
         (keep-indexed (fn [idx item] (when (= (:id item) cursor) idx)))
         first)))

(defn paginate [all-items {:keys [first-n after-cursor last-n before-cursor]}]
  (let [all-items (vec all-items)
        after-idx (find-cursor-idx all-items after-cursor)
        before-idx (find-cursor-idx all-items before-cursor)
        filtered-items (cond
                         (and after-idx before-idx)
                         (subvec all-items (inc after-idx) before-idx)

                         after-idx
                         (subvec all-items (inc after-idx))

                         before-idx
                         (subvec all-items 0 before-idx)

                         :else
                         all-items)
        items (cond
                first-n (vec (take first-n filtered-items))
                last-n (vec (take-last last-n filtered-items))
                :else filtered-items)
        has-next-page (boolean (or (some? before-idx)
                                   (and first-n (> (count filtered-items) (count items)))))
        has-previous-page (boolean (or (some? after-idx)
                                       (and last-n (> (count filtered-items) (count items)))))]
    {:edges (mapv (fn [item] {:cursor (:id item) :node item}) items)
     :pageInfo {:hasNextPage has-next-page
                :hasPreviousPage has-previous-page
                :startCursor (some-> (first items) :id)
                :endCursor (some-> (last items) :id)}}))
