(ns conao3.ccboard.util-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [conao3.ccboard.util :as c.util]))

(deftest encode-id-test
  (testing "encodes type and raw-id into base64"
    (is (= "UHJvamVjdDpmb28=" (c.util/encode-id "Project" "foo")))
    (is (= "U2Vzc2lvbjpmb28vYmFy" (c.util/encode-id "Session" "foo/bar")))
    (is (= "TWVzc2FnZTpmb28vYmFyL2Jheg==" (c.util/encode-id "Message" "foo/bar/baz")))))

(deftest decode-id-test
  (testing "decodes base64 into type and raw-id"
    (is (= {:type "Project" :raw-id "foo"} (c.util/decode-id "UHJvamVjdDpmb28=")))
    (is (= {:type "Session" :raw-id "foo/bar"} (c.util/decode-id "U2Vzc2lvbjpmb28vYmFy")))
    (is (= {:type "Message" :raw-id "foo/bar/baz"} (c.util/decode-id "TWVzc2FnZTpmb28vYmFyL2Jheg==")))))

(deftest encode-decode-roundtrip-test
  (testing "encode and decode are inverse operations"
    (let [test-cases [["Project" "my-project"]
                      ["Session" "project-id/session-id"]
                      ["Message" "project/session/uuid-123"]]]
      (doseq [[type raw-id] test-cases]
        (let [encoded (c.util/encode-id type raw-id)
              decoded (c.util/decode-id encoded)]
          (is (= type (:type decoded)))
          (is (= raw-id (:raw-id decoded))))))))

(deftest path->slug-test
  (testing "converts path separators to dashes"
    (is (= "-home-user-project" (c.util/path->slug "/home/user/project")))
    (is (= "foo-bar-baz" (c.util/path->slug "foo/bar/baz")))
    (is (= "no-slash" (c.util/path->slug "no-slash")))))

(deftest find-cursor-idx-test
  (testing "returns nil for nil cursor"
    (is (nil? (c.util/find-cursor-idx [{:id "a"} {:id "b"}] nil))))

  (testing "returns index when cursor matches"
    (is (= 0 (c.util/find-cursor-idx [{:id "a"} {:id "b"} {:id "c"}] "a")))
    (is (= 1 (c.util/find-cursor-idx [{:id "a"} {:id "b"} {:id "c"}] "b")))
    (is (= 2 (c.util/find-cursor-idx [{:id "a"} {:id "b"} {:id "c"}] "c"))))

  (testing "returns nil when cursor not found"
    (is (nil? (c.util/find-cursor-idx [{:id "a"} {:id "b"}] "x")))))

(deftest paginate-test
  (let [items [{:id "a"} {:id "b"} {:id "c"} {:id "d"} {:id "e"}]]

    (testing "returns all items when no pagination args"
      (let [result (c.util/paginate items {})]
        (is (= 5 (count (:edges result))))
        (is (= "a" (-> result :edges first :cursor)))
        (is (= "e" (-> result :edges last :cursor)))
        (is (false? (-> result :pageInfo :hasNextPage)))
        (is (false? (-> result :pageInfo :hasPreviousPage)))
        (is (= "a" (-> result :pageInfo :startCursor)))
        (is (= "e" (-> result :pageInfo :endCursor)))))

    (testing "first-n limits from start"
      (let [result (c.util/paginate items {:first-n 2})]
        (is (= 2 (count (:edges result))))
        (is (= "a" (-> result :edges first :cursor)))
        (is (= "b" (-> result :edges last :cursor)))
        (is (true? (-> result :pageInfo :hasNextPage)))
        (is (false? (-> result :pageInfo :hasPreviousPage)))))

    (testing "last-n limits from end"
      (let [result (c.util/paginate items {:last-n 2})]
        (is (= 2 (count (:edges result))))
        (is (= "d" (-> result :edges first :cursor)))
        (is (= "e" (-> result :edges last :cursor)))
        (is (false? (-> result :pageInfo :hasNextPage)))
        (is (true? (-> result :pageInfo :hasPreviousPage)))))

    (testing "after-cursor starts after specified cursor"
      (let [result (c.util/paginate items {:after-cursor "b"})]
        (is (= 3 (count (:edges result))))
        (is (= "c" (-> result :edges first :cursor)))
        (is (= "e" (-> result :edges last :cursor)))
        (is (false? (-> result :pageInfo :hasNextPage)))
        (is (true? (-> result :pageInfo :hasPreviousPage)))))

    (testing "before-cursor ends before specified cursor"
      (let [result (c.util/paginate items {:before-cursor "d"})]
        (is (= 3 (count (:edges result))))
        (is (= "a" (-> result :edges first :cursor)))
        (is (= "c" (-> result :edges last :cursor)))
        (is (true? (-> result :pageInfo :hasNextPage)))
        (is (false? (-> result :pageInfo :hasPreviousPage)))))

    (testing "after-cursor with first-n"
      (let [result (c.util/paginate items {:after-cursor "a" :first-n 2})]
        (is (= 2 (count (:edges result))))
        (is (= "b" (-> result :edges first :cursor)))
        (is (= "c" (-> result :edges last :cursor)))
        (is (true? (-> result :pageInfo :hasNextPage)))
        (is (true? (-> result :pageInfo :hasPreviousPage)))))

    (testing "before-cursor with last-n"
      (let [result (c.util/paginate items {:before-cursor "e" :last-n 2})]
        (is (= 2 (count (:edges result))))
        (is (= "c" (-> result :edges first :cursor)))
        (is (= "d" (-> result :edges last :cursor)))
        (is (true? (-> result :pageInfo :hasNextPage)))
        (is (true? (-> result :pageInfo :hasPreviousPage)))))

    (testing "after and before cursor together"
      (let [result (c.util/paginate items {:after-cursor "a" :before-cursor "e"})]
        (is (= 3 (count (:edges result))))
        (is (= "b" (-> result :edges first :cursor)))
        (is (= "d" (-> result :edges last :cursor)))
        (is (true? (-> result :pageInfo :hasNextPage)))
        (is (true? (-> result :pageInfo :hasPreviousPage)))))

    (testing "empty items"
      (let [result (c.util/paginate [] {})]
        (is (= 0 (count (:edges result))))
        (is (nil? (-> result :pageInfo :startCursor)))
        (is (nil? (-> result :pageInfo :endCursor)))))))
