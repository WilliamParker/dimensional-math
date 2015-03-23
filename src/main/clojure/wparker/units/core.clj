(ns wparker.units.core
  (:require [clojure.string]
            [clojure.set :refer [union]])
  (:import [clojure.lang
            ExceptionInfo]
           [org.apache.commons.collections.map
            ReferenceIdentityMap]
           [clojure.lang
            ExceptionInfo
            Ratio]))

(def ^{:dynamic true
       :doc "Dynamic var that, when bound to true, causes quantity macro expansions to expand to
       the unit-checked case.  This can be set to true with a Leiningen injection in any dev/testing profiles
       in projects using unit checking to ensure that units are always checked in tests."}
  *compile-with-unit-checks* false)

(def ^{:dynamic true
       :doc "When bound to a false unit checking is disabled at runtime,
       even if enabled at compile time.  Note that this also disables quantity creation."}
  *check-units* true)

(defn ^{:doc "Fixture that executes a zero-arity function with unit checking disabled.
        Note that quantity creation is also disabled, so any quantities created in this scope
        should not be used in unit-checked operations outside it."}
  without-unit-checks [f]
  (binding [*check-units* false]
    (f)))

;; Stores the map of number objects to units.  A reference-based map is used to ensure that storing unit information
;; will not prevent quantities from being garbage collected.
(def ^:private units-map (java.util.Collections/synchronizedMap
                          (ReferenceIdentityMap.)))

(defn ^{:doc "Returns true if its argument corresponds to a quantity and false otherwise."}
  quantity? [x]
  (.containsKey ^java.util.Map units-map x))

(defn ^{:doc "Given a quantity, returns its units map."}
  quantity->units [x]
  (.get ^java.util.Map units-map x))

(defprotocol ^{:doc "Every type that can be considered a quantity needs to
               implement the clone-number function.
               The contract is that given an argument
               x and a result y, x equals y, but x and y are different object instances."}
  CloneableNumber
  (clone-number [x]))

(defn ^{:doc "Checks if two numbers are identical objects.
        If they are, an exception is thrown.
        If they are not, the second one is returned."
        :private true}
  number-instance-check [x y]
  (if (identical? x y)
    (throw (ex-info "The units library failed to create a new instance of a number when creating a quantity." {:values [x y]}))
    y))

(defn ^{:doc "Takes a function that copies a number in accordance with the
        clone-number contract and wraps it with a test that the number was
        successfully copied."}
  ->checked-copy-fn [f]
  (fn [x]
    (number-instance-check x (f x))))

(extend-protocol CloneableNumber
  ;; Since Clojure reverts to object instances across function boundaries
  ;; extensions on primitive types are unneeded.  Also note that for unit
  ;; checking to function the result must be an object, not a primitive.
  java.lang.Integer
  (clone-number [x] ((->checked-copy-fn #(java.lang.Integer. ^java.lang.Integer %)) x))
  java.lang.Long
  (clone-number [x] ((->checked-copy-fn #(java.lang.Long. ^java.lang.Long %)) x))
  java.lang.Short
  (clone-number [x] ((->checked-copy-fn #(java.lang.Short. ^java.lang.Short %)) x))
  java.lang.Float
  (clone-number [x] ((->checked-copy-fn #(java.lang.Float. ^java.lang.Float %)) x))
  java.lang.Double
  (clone-number [x] ((->checked-copy-fn #(java.lang.Double. ^java.lang.Double %)) x))
  java.lang.Byte
  (clone-number [x] ((->checked-copy-fn #(java.lang.Byte. ^java.lang.Byte %)) x))
  java.math.BigDecimal
  (clone-number [x] ((->checked-copy-fn #(java.math.BigDecimal. (.toString ^java.math.BigDecimal %))) x))
  java.math.BigInteger
  (clone-number [x] ((->checked-copy-fn #(java.math.BigInteger. (.toString ^java.math.BigInteger %))) x))

  clojure.lang.BigInt
  (clone-number [x] ((->checked-copy-fn #(clojure.lang.BigInt/fromBigInteger (.toBigInteger ^clojure.lang.BigInt %))) x))
  clojure.lang.Ratio
  (clone-number [x] ((->checked-copy-fn #(clojure.lang.Ratio. (.numerator ^Ratio %) (.denominator ^Ratio %))) x))

  Object
  (clone-number [x] (throw (ex-info "Type not supported by CloneableNumber." {:argument x})))

  nil
  (clone-number [_] (throw (java.lang.IllegalArgumentException. "CloneableNumber does not support nil"))))

(defn ^{:doc "Given a number, returns a new number instance that is associated with the given units."}
  ->quantity* [magnitude units]
  (if *check-units*
    (let [copied-num (clone-number magnitude)]
      (.put ^java.util.Map units-map copied-num units)
      copied-num)
    magnitude))

(defn ^{:doc "Returns a string representing a quantity with units.  Note that the standard str function just returns
        the magnitude as a string, while this function includes units.  This follows because the library associates
        units with a number, but the underlying object instance of the number is unchanged."}
  quantity->str [quantity]
  (str quantity " "
       (apply str (clojure.string/join " "
                                       (map #(str (name (key %))
                                                  "^"
                                                  (val %))
                                            (quantity->units quantity))))))

(defn ^{:private true
        :doc "Filters units with a power of 0 from a units map."}
  filter-zeroes [units]
  (into {}
        (filter #(not (== 0 (val %))) units)))

(def ^{:dynamic true} *unit-power-error* nil)

(defn units-equal?
  "Tests if an arbitrary number of quantities have the same units."
  [& quantities]
  (assert (every? quantity? quantities))
  (let [units (map quantity->units quantities)
        unit-types (map (comp set keys) units)
        ;; Allow a difference in quantity powers if one is set.  This is expected if
        ;; there are non-integer powers used in operations that change the unit powers.
        equality-fn (if *unit-power-error*
                      (fn [& powers]
                        (let [diff (- (apply max powers)
                                      (apply min powers))]
                          (< (max diff (- diff))
                             *unit-power-error*)))
                      ==)]
    (boolean (and
              ;; Validate that all quantities have the same unit types.
              (apply = unit-types)
              ;; Validate that all units have the same powers in each quantity.
              ;; For every unit type, apply equality to the seq of values for that unit
              ;; type in each quantity.
              (every? (fn [unit-type]
                        (apply
                         equality-fn
                         (map unit-type units)))
                      (first unit-types))))))

(defn ->quantity-operation-fn
  "Returns a function that takes an arbitrary number of quantities and returns a new quantity, with a magnitude determined by math-fn
  and powers determined by unit-fn.  For example, in the case of multiplication,
  the math-fn is * and the unit-fn is +, since the units of multiplied quantities are added when multiplied.  The first argument
  to the unit-fn is a power of a unit in the first quantity and the second argument is the power of the same unit in the second quantity.
  The unit-fn can assume that it will receive numeric arguments; if a unit is not present in a quantity its' power is 0.  If the underlying
  unit operation is not commutative the creator may need to wrap the underlying function to ensure that arguments are received in the correct
  order and number to ensure correct unit propagation."
  [math-fn unit-fn]
  (fn [& quantities]
    (let [unit-merge-fn (fn [& args]
                          (apply unit-fn (map #(if (nil? %)
                                                 0
                                                 %)
                                              args)))]
      (if *check-units*
        (do
          (assert (every? quantity? quantities))
          (->quantity* (apply math-fn quantities)
                       (filter-zeroes
                        (let [all-units (map quantity->units quantities)
                              all-types (apply union
                                               (map (comp set keys) all-units))]
                          (apply merge-with unit-merge-fn (merge
                                                           (zipmap all-types (repeat 0))
                                                           (first all-units))
                                 (rest all-units))))))
        (apply math-fn quantities)))))

(defn ->quantities-equal-fn
  "Returns a function that operates on two quantities with the same units and returns a new quantity with the same units.  If the two quantities
  have different units an exception is thrown."
  ([math-fn operation-description]
   (fn [& quantities]
     (if *check-units*
       (do
         (when (empty? quantities)
           (throw (java.lang.IllegalArgumentException.
                   (str "Operations requiring equal units are not supported for the zero-argument case. Called "
                        operation-description))))
         (assert (every? quantity? quantities))
         (if (apply units-equal? quantities)
           (->quantity* (apply math-fn quantities)
                        (quantity->units (first quantities)))
           (throw (ExceptionInfo. "Quantities do not have the same units" {:quantities quantities}))))
       (apply math-fn quantities)))))

(defn ->quantity-comparison-fn
  "Returns a function that compares two quantities that must have equal units.  This function will returns a boolean.
  Takes a function to compare two numbers."
  [compare-fn fn-desc]
  (fn [& compared-quantities]
    (do
      (when *check-units*
        (do
          (when (empty? compared-quantities)
            (throw (java.lang.IllegalArgumentException.
                    (str "Quantity comparison functions must take at least one argument.  Called " fn-desc))))
          (assert (every? quantity? compared-quantities))
          (assert (every? units-equal? compared-quantities))
          (let [units (map quantity->units compared-quantities)]
            (when (not (= (count (distinct units))
                          1))
              (throw (ExceptionInfo. "Two quantities that are compared should have equal units." {}))))))
      (boolean (apply compare-fn compared-quantities)))))

(def ^{:doc "Tests if an arbitrary number of quantities are equal."}
  quantities-equal?* (->quantity-comparison-fn == "equality"))

(def ^{:doc "Tests if an arbitrary number of quantities are less than each other."}
  quantities-less-than?* (->quantity-comparison-fn < "less than"))

(def ^{:doc "Tests if an arbitrary number of quantities are greater than each other."}
  quantities-greater-than?* (->quantity-comparison-fn > "greater than"))

(def ^{:doc "Function that multiplies an arbitrary number of quantities."}
  quantities-multiply* (->quantity-operation-fn * +))

(def ^{:doc "Function that divides two quantities."}
  quantities-divide*
  ;; Clojure division returns 1/argument when a single argument is provided.  In order to ensure proper unit handling,
  ;; namely that the inverse has inverted units, the quantity-aware function is wrapped so that it receives a scalar
  ;; divided by the argument.
  (fn [& args]
    (let [base-fn (->quantity-operation-fn / -)]
      (if (and *check-units*
               (== (count args) 1))
        (base-fn (->quantity* 1 {}) (first args))
        (apply base-fn args)))))

(def ^{:doc "Function that adds quantities.  It must be provided at least 1 argument.
       Unlike clojure.core/+, this will throw an exception if no arguments are provided."}
  quantities-add* (->quantities-equal-fn + "addition"))

(def ^{:doc "Function that subtracts an arbitrary number of quantities.
       It must be provided at least 1 argument."}
  quantities-subtract* (->quantities-equal-fn - "subtraction"))

(defmacro ->quantity
  "Macro that expands to create a quantity from the given numeric magnitude and unit map if *compile-with-unit-checks* is true
  and simply expands to the magnitude otherwise."
  [q u]
  (if *compile-with-unit-checks*
    `(wparker.units.core/->quantity* ~q ~u)
    q))

(defmacro def-quantities-macro
  "Defines a macro that inserts a quantity-checking function if *compile-with-unit-checks* is true and a plain math function otherwise.  Takes four arguments:
  1. The name of the macro to define.
  2. The docstring for the macro macro to define.
  3. The fully-qualified checked function.
  4. The fully-qualified equivalent unchecked function."
  [macro-name macro-doc checked-fn unchecked-fn]
  `(let [checked# (quote ~checked-fn)
         unchecked# (quote ~unchecked-fn)]
     (defmacro ~macro-name ~macro-doc [& qs#]
       (if *compile-with-unit-checks*
         `(~(symbol checked#) ~@qs#) ;; The symbol call avoids placing the function in code, which causes problems when the functions are closures.
         `(~(symbol unchecked#) ~@qs#))))) ;; See discussion at http://stackoverflow.com/questions/11191992/functions-with-closures-and-eval-in-clojure

(def-quantities-macro quantities-add "Macro that expands to add quantities if the global flag *compile-with-unit-checks* is true and expands to simple addition otherwise."
  wparker.units.core/quantities-add* clojure.core/+)

(def-quantities-macro quantities-subtract "Macro that expands to subtract quantities if the global flag *compile-with-unit-checks* is true and expands to simple subtraction otherwise."
  wparker.units.core/quantities-subtract* clojure.core/-)

(def-quantities-macro quantities-multiply "Macro that expands to multiply quantities if the global flag *compile-with-unit-checks* is true and expands to simple multiplication otherwise."
  wparker.units.core/quantities-multiply* clojure.core/*)

(def-quantities-macro quantities-divide "Macro that expands to divide quantities if the global flag *compile-with-unit-checks* is true and expands to simple division otherwise."
  wparker.units.core/quantities-divide* clojure.core//)

(def-quantities-macro quantities-equal? "Macro that expands to check quantity equality if the global flag *compile-with-unit-checks* is true and expands to a simple equality check otherwise."
  wparker.units.core/quantities-equal?* clojure.core/==)

(def-quantities-macro quantities-less-than? "Macro that expands to check a less-than quantity inequality if *compile-with-unit-checks* is true
  and expands to a simple less-than check otherwise."
  wparker.units.core/quantities-less-than?* clojure.core/<)

(def-quantities-macro quantities-greater-than? "Macro that expands to check a greater-than quantity inequality if *compile-with-unit-checks* is true
  and expands to a simple greater-than check otherwise."
  wparker.units.core/quantities-greater-than?* clojure.core/>)
