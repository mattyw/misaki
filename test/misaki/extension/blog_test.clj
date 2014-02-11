(ns misaki.extension.blog-test
  (:require
    [misaki.input.watch-directory :as in]
    [misaki.extension.blog :refer :all]
    [misaki.config :refer [*config*]]
    [misaki.util.file :as file]
    [midje.sweet               :refer :all]
    [misaki.route :as route]
    [clojure.string :as str]
    [clojure.java.io :as io]
    [cuma.core :refer [render]]
    [clj-time.core :refer [date-time]]
    ))

(def ^{:private true} watch-dir
  "test/files/blog")
(def ^{:private true} test-conf
  {;:blog {:filter [:complete-date :frontmatter]}
   :applying-route [:complete-date :frontmatter :remove-last-extension]
   :watch-directory watch-dir})

(fact "layout-file should work fine."
  (binding [*config* (blog-config test-conf)]
    (layout-file "foo") => (io/file (file/join watch-dir "layouts" "foo.html"))))

(fact "post-file? should work fine."
  (binding [*config* (blog-config test-conf)]
    (post-file? (io/file (file/join (:post-dir *config*) "foo.txt")))   => true
    (post-file? (io/file (file/join (:layout-dir *config*) "foo.txt"))) => false))

(fact "layout-file? should work fine."
  (binding [*config* (blog-config test-conf)]
    (layout-file? (io/file (file/join (:layout-dir *config*) "foo.txt")))   => true
    (layout-file? (io/file (file/join (:post-dir *config*) "foo.txt"))) => false))

(fact "get-post-files should work fine."
  (binding [*config* (blog-config test-conf)]
    (count (get-post-files)) => 2))

(facts "get-posts should work fine."
  (fact "default config"
    (binding [*config* (blog-config test-conf)]
      (let [[p2 p1 :as posts] (get-posts)]
        (count posts)            => 2
        (-> p1 :title) => "foo"
        (-> p2 :title) => "bar"

        (-> p1 :date)  => (date-time 2014 1 1 0 11 22)
        (-> p2 :date)  => (date-time 2014 1 2 0 11 22)

        (-> p1 :url) => "/2014-01-01-001122.html"
        (-> p2 :url) => "/2014-01-02-001122.html")))

  (fact "custom watch direcotry"
    (binding [*config* (blog-config (merge test-conf {:local-server {:url-base "/foo"}}))]
      (let [[p2 p1 :as posts] (get-posts)]
        (-> p1 :url) => "/foo/2014-01-01-001122.html"
        (-> p2 :url) => "/foo/2014-01-02-001122.html"
        ))))

(defn- config-for-main
  ([path]
   (config-for-main
     path
     ";; :layout \"default\"\n;; :title \"hello\"\n\n$(title) world"))
  ([path content]
   (let [base-dir (:watch-directory *config*)
         filename (file/join base-dir path)
         m (in/parse-file (io/file filename) base-dir)
         m (assoc m :content (delay content))
         ]
     (route/apply-route m (:applying-route *config*))
     ;(assoc m :content (delay content))
     )))

(fact "get-neighbors should work fine."
  (get-neighbors #(= 1 %) [1 2 3]) => [nil 2]
  (get-neighbors #(= 2 %) [1 2 3]) => [1   3]
  (get-neighbors #(= 3 %) [1 2 3]) => [2   nil]
  (get-neighbors #(= 4 %) [1 2 3]) => [nil nil])

(facts "-main should work fine."
  (binding [*config* test-conf]
    (fact "post template file"
      (let [path (file/join "foo" "2014-01-01-001122.html")
            m    (config-for-main (file/join DEFAULT_POST_DIR (str path ".md")))
            res  (-main (merge *config* m))]

        (contains? res :posts) => true
        (count (:posts res)) => 2
        (:title res) => "hello"
        (:path res)  => path
        (:index-url res) => "/"
        (-> res :content force (.indexOf "<title>hello</title>") (not= -1)) => true
        (-> res :content force (.indexOf "hello world") (not= -1)) => true
        ;; TODO
        ;; * prev/next post
        ))

    (fact "normal template file"
      (let [path "index.html"
            m    (config-for-main (str path ".md"))
            res  (-main (merge *config* m))]

        (contains? res :posts) => true
        (count (:posts res)) => 2
        (:title res) => "hello"
        (:path res)  => path
        (-> res :content force (.indexOf "<title>hello</title>") (not= -1)) => true
        (-> res :content force (.indexOf "hello world") (not= -1)) => true
        ;; TODO
        ;; * pagination
        ))

    (fact "custom url base"
      (binding [*config* (merge *config* {:local-server {:url-base "/foo"}})]
        (let [res  (-main (merge *config* (config-for-main "index.html.md")))]
          (:index-url res) => "/foo/")))

    (fact "custom url base and index filename"
      (binding [*config* (merge *config* {:local-server {:url-base "/foo"}
                                          :blog {:index-filename "bar.html"}})]
        (let [res  (-main (merge *config* (config-for-main "index.html.md")))]
          (:index-url res) => "/foo/bar.html")))
    ))

