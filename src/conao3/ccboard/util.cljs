(ns conao3.ccboard.util
  (:require
   [clojure.string :as str]))

(defn clsx [& args]
  (->> args
       (mapcat (fn [arg]
                 (cond
                   (string? arg) [arg]
                   (map? arg) (keep (fn [[k v]] (when v (name k))) arg)
                   (sequential? arg) (apply clsx arg)
                   :else nil)))
       (remove str/blank?)
       (str/join " ")))

(defn encode-id [type raw-id]
  (js/btoa (str type ":" raw-id)))

(defn decode-id [id]
  (let [[type raw-id] (str/split (js/atob id) #":")]
    {:type type :raw-id raw-id}))

(defn path->slug [p]
  (str/replace p "/" "-"))

(defn encode-cursor [idx]
  (js/btoa (str "cursor:" idx)))

(defn decode-cursor [cursor]
  (when cursor
    (let [decoded (js/atob cursor)]
      (when (and decoded (.startsWith decoded "cursor:"))
        (js/parseInt (subs decoded 7))))))

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

(defn paginate-lazy [lines parse-fn {:keys [first-n after-cursor last-n before-cursor]}]
  (let [total-count (count lines)
        after-idx (decode-cursor after-cursor)
        before-idx (decode-cursor before-cursor)
        start-idx (cond
                    (and after-idx before-idx) (inc after-idx)
                    after-idx (inc after-idx)
                    :else 0)
        end-idx (cond
                  (and after-idx before-idx) (min before-idx total-count)
                  before-idx (min before-idx total-count)
                  :else total-count)
        filtered-count (- end-idx start-idx)
        [take-start take-count] (cond
                                  first-n [start-idx (min first-n filtered-count)]
                                  last-n [(max start-idx (- end-idx last-n)) (min last-n filtered-count)]
                                  :else [start-idx filtered-count])
        items (->> (subvec (vec lines) take-start (+ take-start take-count))
                   (map-indexed (fn [relative-idx line]
                                  (parse-fn (+ take-start relative-idx) line)))
                   vec)
        has-next-page (< (+ take-start take-count) total-count)
        has-previous-page (> take-start 0)]
    {:edges (map-indexed (fn [relative-idx item]
                           {:cursor (encode-cursor (+ take-start relative-idx))
                            :node item})
                         items)
     :pageInfo {:hasNextPage has-next-page
                :hasPreviousPage has-previous-page
                :startCursor (when (seq items) (encode-cursor take-start))
                :endCursor (when (seq items) (encode-cursor (+ take-start (dec (count items)))))}}))
