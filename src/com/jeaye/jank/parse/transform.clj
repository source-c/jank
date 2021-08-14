(ns com.jeaye.jank.parse.transform
  (:refer-clojure :exclude [keyword map vector set])
  (:require [clojure.edn :as edn]
            [clojure.walk :refer [postwalk]]
            [orchestra.core :refer [defn-spec]]
            [com.jeaye.jank
             [log :refer [pprint]]
             [assert :refer [parse-assert!]]]
            [com.jeaye.jank.parse.binding :as parse.binding]
            [com.jeaye.jank.parse.spec :as parse.spec]))

(defn merge-meta [obj new-meta]
  (with-meta obj (merge (meta obj) new-meta)))

(defn with-transform-meta [fun]
  (-> (binding [parse.binding/*current-form* (merge-meta parse.binding/*current-form*
                                                         {:file parse.binding/*input-file*})]
        (fun))
      (merge-meta {:file parse.binding/*input-file*})))

(defn constant [transformer & args]
  (with-transform-meta
    #(let [transformed (apply transformer args)]
       (assoc transformed
              ::parse.spec/kind :constant
              ::parse.spec/type (::parse.spec/kind transformed)))))

(defn none [kind]
  (with-transform-meta
    #(do
       {::parse.spec/kind kind})))

; TODO: Rename value to node? the whole thing is the node though
(defn single [kind value]
  (with-transform-meta
    #(do
       {::parse.spec/kind kind
        ::parse.spec/value value})))

(defn single-values [kind values]
  (with-transform-meta
    #(do
       {::parse.spec/kind kind
        ::parse.spec/values values})))

(defn read-single [kind value]
  (with-transform-meta
    #(do
       {::parse.spec/kind kind
        ::parse.spec/value (edn/read-string value)})))

(defn keyword [qualified & more]
  (with-transform-meta
    #(let [qualified? (= qualified :qualified)]
       (merge {::parse.spec/kind :keyword}
              (cond
                qualified?
                {::parse.spec/ns (second more)
                 ::parse.spec/name (nth more 3)}
                (= (first more) "::")
                {::parse.spec/ns ::parse.spec/current
                 ::parse.spec/name (second more)}
                :else
                {::parse.spec/name (second more)})))))

(defn map [& more]
  (with-transform-meta
    (fn []
      (let [kvs (partition-all 2 more)
            _ (parse-assert! (every? #(= 2 (count %)) kvs)
                             parse.binding/*current-form*
                             "maps require an even number of forms")
            values (mapv (fn [[k v]]
                           {::parse.spec/key k
                            ::parse.spec/value v})
                         kvs)]
        (single-values :map values)))))

(defn set [& more]
  (with-transform-meta
    ; Doesn't go into a set yet, since it needs to be evaluated before it's deduped.
    #(single-values :set (vec more))))

(defn vector [& more]
  (with-transform-meta
    #(single-values :vector (vec more))))

(defn identifier [qualification & more]
  (with-transform-meta
    #(let [qualified? (= qualification :qualified)]
       (merge {::parse.spec/kind :identifier}
              (if qualified?
                {::parse.spec/ns (first more)
                 ::parse.spec/name (second more)}
                {::parse.spec/name (first more)})))))

(defn def-expression [& more]
  (with-transform-meta
    #(do
       {::parse.spec/kind :binding
        ::parse.spec/identifier (first more)
        ::parse.spec/value (second more)
        ::parse.spec/scope ::parse.spec/global})))

(defn argument-list [& more]
  (with-transform-meta
    #(vec more)))

(defn do-expression [& more]
  (with-transform-meta
    #(let [ret (last more)]
       {::parse.spec/kind :do
        ::parse.spec/body (into [] (butlast more))
        ::parse.spec/return (if (some? ret)
                              ret
                              (constant none :nil))})))

(defn fn-expression [& more]
  (with-transform-meta
    #(let [has-name? (= :identifier (-> more first :kind))
           ; TODO: Add parse support for variadic fns
           params (mapv (fn [ident]
                          {::parse.spec/kind :binding
                           ::parse.spec/identifier ident
                           ::parse.spec/scope ::parse.spec/parameter})
                        (if has-name?
                          (second more)
                          (first more)))
           body (if has-name?
                  (drop 2 more)
                  (rest more))]
       (merge {::parse.spec/kind :fn
               ::parse.spec/parameters params
               ::parse.spec/body (apply do-expression body)}
              (when has-name?
                {::parse.spec/name {::parse.spec/kind :binding
                                    ::parse.spec/identifier (first more)
                                    ::parse.spec/scope ::parse.spec/fn}})))))

(defn if-expression [& [condition then else]]
  (with-transform-meta
    #(merge {::parse.spec/kind :if
             ::parse.spec/condition condition
             ::parse.spec/then then}
            (when (some? else)
              {::parse.spec/else else}))))

(defn let-bindings [& bindings]
  (with-transform-meta
    #(->> (partition-all 2 bindings)
          (mapv (fn [[ident value]]
                  {::parse.spec/kind :binding
                   ::parse.spec/identifier ident
                   ::parse.spec/value value
                   ::parse.spec/scope ::parse.spec/let})))))

(defn let-expression [bindings & body]
  (with-transform-meta
    #(do
       {::parse.spec/kind :let
        ::parse.spec/bindings bindings
        ::parse.spec/body (apply do-expression body)})))

(defn application [& more]
  (with-transform-meta
    #(do
       {::parse.spec/kind :application
        ::parse.spec/value (first more)
        ::parse.spec/arguments (vec (rest more))})))

(def transformer {:nil (partial constant none :nil)
                  :integer (partial constant read-single :integer)
                  :real (partial constant read-single :real)
                  :boolean (partial constant read-single :boolean)
                  :keyword (partial constant keyword :unqualified)
                  :qualified-keyword (partial constant keyword :qualified)
                  :string (partial constant single :string)
                  :regex (partial constant single :regex)
                  :map (partial constant map)
                  :vector (partial constant vector)
                  :set (partial constant set)
                  :identifier (partial identifier :unqualified)
                  :qualified-identifier (partial identifier :qualified)
                  :symbol (partial constant single :symbol)
                  :def def-expression
                  :argument-list argument-list
                  :fn fn-expression
                  :do do-expression
                  :if if-expression
                  :let let-expression
                  :let-bindings let-bindings
                  :application application})

(defn-spec walk ::parse.spec/tree
  [parsed any?]
  (postwalk (fn [item]
              ;(pprint "walk item" [item (meta item)])
              (if-some [trans (when (and (map? item) (contains? item :tag))
                                (transformer (:tag item)))]
                (let [r (binding [parse.binding/*current-form* item]
                          (apply trans (:content item)))]
                  ;(pprint [r (meta r)])
                  r)
                item))
                 parsed))
