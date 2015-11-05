(ns jank.type.declaration)

(defn lookup [decl-name scope]
  "Recursively looks through the hierarchy of scopes for the declaration."
  (loop [current-scope scope]
    (when current-scope
      ; TODO: Should be a vector of overloads
      (if-let [found (find (:binding-declarations current-scope) decl-name)]
        found
        (recur (:parent current-scope))))))

(defn validate [decl-name decl-type scope]
  "Looks up a declaration, if any, and verifies that the provided
   declaration has a matching type. Returns the decl or nil, if none is found."
  (let [decl (lookup decl-name scope)]
    (when (some? decl)
      (let [expected-type (:type (second decl))]
        ; TODO: Allow overloads
        (assert (= expected-type decl-type)
                (str "Declaration of "
                     decl-name
                     " doesn't match previous declarations: "
                     expected-type
                     " vs "
                     decl-type))))
    decl))

(defmulti add-to-scope
  (fn [item scope]
    (if (= (count item) 2)
      :type-declaration
      :binding-declaration)))

(defmethod add-to-scope :type-declaration [item scope]
  "Adds the opaque type declaration to the scope.
   Returns the updated scope."
  (let [decl-name (get-in item [1 1])]
    (update scope :type-declarations conj decl-name)))

(defmethod add-to-scope :binding-declaration [item scope]
  "Finds, validates, and adds the provided declaration into the scope.
   Returns the updated scope."
  (let [decl-name (get-in item [1 1])
        decl-type (get-in item [2 1])
        decl (validate decl-name decl-type scope)]
    (if (nil? decl)
      (update scope :binding-declarations assoc decl-name {:type decl-type})
      scope)))
