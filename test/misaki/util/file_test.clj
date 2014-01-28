(ns misaki.util.file-test
  (:require
    [midje.sweet  :refer :all]
    [misaki.util.file :refer :all]))


(fact "file/join should work fine."
  (join "a" "b") => (str "a" separator "b"))

(fact "file/get-last-ext should work fine."
  (get-last-ext "foo.txt")     => "txt"
  (get-last-ext "foo.txt.bar") => "bar"
  (get-last-ext "foo")         => nil)

(fact "file/normalize should work fine."
  (normalize (str "foo" separator)) => "foo"
  (normalize (str "foo" separator "foo.txt")) => (str "foo" separator "foo.txt"))