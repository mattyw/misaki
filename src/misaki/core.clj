(ns misaki.core
  "Misaki Controll Core"
  (:use misaki.config
        [misaki.util file string sequence notify]
        [text-decoration.core :only [green]]
        [pretty-error.core    :only [print-pretty-stack-trace]])
  (:import [java.io File]))

(declare get-watch-file-extensions)

;; ## Util
(defn key->sym [k] (symbol (name k)))

;; ## Compiler Utilities

; =stop-compile?
(defn- stop-compile? [compile-result]
  (and (map?   compile-result)
       (true? (:stop-compile? compile-result))))

(defn- all-compile? [compile-result]
  (and (map?   compile-result)
       (true? (:all-compile? compile-result))))

; =call-compiler-fn
(defn- call-compiler-fn
  "Call compiler's function.

      (call-compiler-fn :-compile file)
      (let [compiler {'-extension #(list :clj %)}]
        (call-compiler-fn compiler :-extension :cljs))
  "
  [& params]
  (let [[compiler fn-key & args] (if (-> params first map?)
                                   params
                                   (cons (:compiler *config*) params))
        fn-sym (key->sym fn-key)]
    (if (sequential? compiler)
      (mapcat #(apply call-compiler-fn % fn-sym args) compiler)
      (if-let [f (get compiler fn-sym)] (apply f args)))))

; =get-template-files
(defn get-template-files
  "Get all template files. Find specified directory with `:dir` option."
  [& {:keys [dir] :or {dir (:template-dir *config*)}}]
  (let [exts (get-watch-file-extensions)]
    (filter
      (fn [file]
        (some #(has-extension? % file) exts))
      (find-files dir))))

; =get-post-files
(defn get-post-files
  "Get all post files.
  Sort file list with `:sort?` option(default setting is FALSE)."
  [& {:keys [sort?] :or {sort? false}}]

  (let [files (get-template-files :dir (:post-dir *config*))]
    (if sort?
      ((sort-type->sort-fn) files)
      files)))

;; ## Compiler Functions

; =get-watch-file-extensions
(defn get-watch-file-extensions
  "Get extensions list to watch."
  []

;  (let [c (:compiler *config*)]
    ;(if (sequential? c)
      

      (call-compiler-fn :-extension)

    ;  )
;    )

  )


; =update-config
(defn update-config
  "Update basic config with plugin's -config function."
  []
  (let [config (make-basic-config-map)
        res    (call-compiler-fn :-config config)]
    (if res res config)))

; =process-compile-result
(defn process-compile-result
  "Process compile result and return process result.

  * string?
    * Write file with default filename.
  * true? or false? or symbol?
    * Do nothing.
      * `true`  : Success
      * `false` : Fail
      * `symbol`: Skip
  * map?
    * Write file with detailed setting.
      * `:status`  : True(success), false(fail) or something else(skip)
      * `:filename`: Filename to write
      * `:body`    : Compiled body text. if body is nil, only status is checked
  "
  [result default-filename]
  (cond
    ; output with default filename
    (string? result)
    (if default-filename
      (do (write-file (add-public-dir default-filename) result)
          true)
      false)

    ; only check status
    (or (true? result) (false? result))
    result

    ; output with detailed data
    (map? result)
    (let [{:keys [status filename body]
           :or   {status false
                  filename default-filename}} result]
      (if (and filename body)
        (do (write-file (add-public-dir filename) body)
            status)
        status))

    ; error
    :else false))


; =compile*
(defn compile*
  "Common function to compile java.io.File with config."
  [config file]
  (try
    (let [default-filename (make-output-filename file)
          compile-result   (call-compiler-fn :-compile config file)
          process-result   (process-compile-result
                             compile-result default-filename)]

      (if (:notify? config) (notify-result file process-result))

      [process-result compile-result])
    (catch Exception e
      (print-pretty-stack-trace
        e :filter #(str-contains? (:str %) "misaki"))
      ; notify error
      (if (:notify? config) (notify-result file false e))
      [false {:stop-compile? true}])))

; =call-all-compile
(defn call-all-compile
  "Call plugin's -compile function for all template files."
  []
  (let [config (assoc (update-config) :-compiling :all)
        files  (get-template-files)]
    (loop [[file & rest-files] files, success? true]
      (if file
        (let [[process-result compile-result] (compile* config file)
              result (and success? process-result)]
          (if (stop-compile? compile-result)
            result
            (recur rest-files result)))
        success?))))

; =call-compile
(defn call-compile
  "Call plugin's -compile function."
  [file]
  (let [config (assoc (update-config) :-compiling :single)
        [process-result compile-result] (compile* config file)]
    (cond
      ; all compile
      (all-compile? compile-result)
        (do (println (green "   Switch to all compiling"))
            (call-all-compile))

      ; compile with post
      (post-file? file)
        (every? #(true? (first %))
                (for [file (map template-name->file (:compile-with-post *config*))]
                  (compile* config file)))

      :else process-result)))


