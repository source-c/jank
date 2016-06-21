(ns jank.type.scope.type
  (:require [jank.type.scope.util :as util])
  (:use clojure.walk
        clojure.pprint
        jank.assert))

(defn function?
  "Returns whether or not the provided type is that of a function."
  [decl-type]
  (= "ƒ" (:name (:value decl-type))))

(defn auto?
  "Returns whether or not the provided type is to be deduced."
  [decl-type]
  (let [type-name (:name (:value decl-type))]
    (or (= "∀" type-name) (= "auto" type-name))))

(defn strip
  "Removes additional information from types which isn't
   needed during comparison."
  [decl-type]
  (dissoc decl-type :external?))

(defmulti lookup
  "Recursively looks through the hierarchy of scopes for the declaration."
  (fn [decl-type scope]
    (cond
      (function? decl-type)
      :function
      (auto? decl-type)
      :auto
      :else
      :default)))

(defmethod lookup :function
  [decl-type scope]
  ; Function types always "exist" as long as they're well-formed
  (let [generics (:values (:generics (:value decl-type)))]
    ; TODO: Add more tests for this
    (type-assert (= (count generics) 2) "invalid function type format")
    (when (> (count (:values (first generics))) 0)
      (type-assert (every? (comp some?
                                 #(lookup % scope))
                           (-> generics first :values))
                   "invalid function parameter type"))
    (when (> (count (:values (second generics))) 0)
      (type-assert (every? (comp some?
                                 #(lookup % scope))
                           (-> generics second :values))
                   "invalid function return type"))
    decl-type))

(defmethod lookup :auto
  [decl-type scope]
  {:kind :type
   :value {:kind :identifier
           :value "auto"}})

; Recursively looks up a type by name.
; Returns the type, if found, or nil.
(defmethod lookup :default
  [decl-type scope]
  (loop [current-scope scope]
    ; TODO: Handle generic types properly
    ; TODO: Merge this with util/lookup?
    (when current-scope
      (if-let [found ((:type-declarations current-scope) decl-type)]
        found
        (recur (:parent current-scope))))))

(defmulti add-to-scope
  (fn [item scope]
    (let [valid-kind (contains? item :type)]
      (type-assert valid-kind (str "invalid type declaration " item))
      ; TODO: type-definition
      (cond
        :else
        :type-declaration))))

; Adds the opaque type declaration to the scope.
; Returns the updated scope.
(defmethod add-to-scope :type-declaration
  [item scope]
  ; TODO: Validate the type is correct
  ; TODO: Add some tests for this
  (update scope :type-declarations conj (:type item)))