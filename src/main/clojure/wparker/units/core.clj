(ns wparker.units.core
  (:require [clojure.string])
  (:import [clojure.lang
            ExceptionInfo]
           [org.apache.commons.collections.map
            ReferenceIdentityMap]
           [clojure.lang
            ExceptionInfo
            Ratio]))

;; Stores the map of number objects to units.
(def ^:private units-map (java.util.Collections/synchronizedMap
                          (ReferenceIdentityMap.)))

(defn ^{:doc "Returns true if its argument corresponds to a quantity and false otherwise."}
  quantity? [x]
  (.containsKey ^java.util.Map units-map x))

(defn ^{:doc "Given a quantity, returns its units map."}
  quantity->units [x]
  (.get ^java.util.Map units-map x))

(defprotocol ^{:doc "Every type that can be considered a quantity needs to implement the clone-number function.  The contract is that given an argument
               x, x.equals(y), but x and y are different object instances."}
  CloneableNumber
  (clone-number [x]))

(defn ^{:doc "Checks if two numbers are identical objects.  If they are, an exception is thrown.  If they are not, the second one is returned."}
  number-instance-check [x y]
  (if (identical? x y)
    (throw (ExceptionInfo. "The units library failed to create a new instance of a number when creating a quantity." {:values [x y]}))
    y))

(defn ^{:doc "Takes a function that copies a number in accordance with the clone-number contract and wraps it with a test that the number was
        successfully copied."}
  ->checked-copy-fn [f]
  (fn [x]
    (number-instance-check x (f x))))

(extend-protocol CloneableNumber
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
  (clone-number [_] (throw (java.lang.IllegalArgumentException. "Type not supported by CloneableNumber."))))

(defn ^{:doc "Given a number, returns a new number instance that is associated with the given units."}
  ->quantity* [magnitude units]
  (let [copied-num (clone-number magnitude)]
    (.put ^java.util.Map units-map copied-num units)
    copied-num))

(defn ^{:doc "Returns a string representing a quantity with units.  Note that the standard str function just returns
        the magnitude as a string, while this function includes units.  If unit checking is only turned on in testing this function
        should also only be used in testing."}
  quantity->str [quantity]
  (str quantity " "
       (apply str (clojure.string/join " "
                                       (map #(str (name (key %))
                                                  "^"
                                                  (val %))
                                            (quantity->units quantity))))))

(defn ^{:private true :doc "Filters units with a power of 0 from a units map."}
  filter-zeroes [units]
  (into {}
        (filter #(not (== 0 (val %))) units)))

(defn units-equal?
  "Tests if two quantities have the same units."
  [quantity-1 quantity-2]
  (assert (every? quantity? [quantity-1 quantity-2]))
  (let [units-1 (quantity->units quantity-1)
        units-2 (quantity->units quantity-2)]
    (boolean (and (= (set (keys units-1))
                     (set (keys units-2)))
                  (every? #(== (units-1 %) ;; TODO: Update to allow user to specify an equality tolerance for floating-point powers of units.
                               (units-2 %))
                          (keys units-1))))))

(defn ->quantity-operation-fn
  "Returns a function that takes two quantities and returns a new quantity, with a magnitude determined by math-fn
  and powers determined by unit-fn.  Both should be 2-arity functions.  For example, in the case of multiplication,
  the math-fn is * and the unit-fn is +, since the units of multiplied quantities are added when multiplied.  The first argument
  to the unit-fn is a power of a unit in the first quantity and the second argument is the power of the same unit in the second quantity.
  The unit-fn can assume that it will receive numeric arguments; if a unit is not present in a quantity its' power is 0."
  [math-fn unit-fn]
  (fn [quantity-1 quantity-2]
    (assert (every? quantity? [quantity-1 quantity-2]))
    (->quantity* (math-fn quantity-1 quantity-2)
                 (filter-zeroes (let [units-1 (quantity->units quantity-1)
                                      units-2 (quantity->units quantity-2)
                                      all-keys (distinct (concat (keys units-1)
                                                                 (keys units-2)))
                                      units-power-fn (fn [k] ((fnil unit-fn 0 0)
                                                              (units-1 k)
                                                              (units-2 k)))]
                                  (zipmap all-keys (map units-power-fn all-keys)))))))

(defn ->quantities-equal-fn
  "Returns a function that operates on two quantities with the same units and returns a new quantity with the same units.  If the two quantities
  have different units an exception is thrown."
  ([math-fn]
   (fn [quantity-1 quantity-2]
     (assert (every? quantity? [quantity-1 quantity-2]))
     (if (units-equal? quantity-1 quantity-2)
       (->quantity* (math-fn quantity-1 quantity-2)
                    (quantity->units quantity-1))
       (throw (ExceptionInfo. "Quantities do not have the same units" {:quantity-1 quantity-1
                                                                       :quantity-2 quantity-2}))))))

(defn quantities-equal?*
  "Tests if two quantities are equal.  The arguments should always be quantities."
  [quantity-1 quantity-2]
  (assert (every? quantity? [quantity-1 quantity-2]))
  (let [units-1 (quantity->units quantity-1)
        units-2 (quantity->units quantity-2)]
    (if (not (units-equal? quantity-1 quantity-2))
      (throw (ExceptionInfo. "Two quantities that are compared should have equal units." {:quantity-1 quantity-1
                                                                                          :quantity-2 quantity-2})))
    (boolean (== quantity-1 quantity-2))))

(def ^{:doc "Function that multiplies two quantities."}
  quantities-multiply* (->quantity-operation-fn * +))

(def ^{:doc "Function that divides two quantities."}
  quantities-divide* (->quantity-operation-fn / -))

(def ^{:doc "Function that adds two quantities"}
  quantities-add* (->quantities-equal-fn +))

(def ^{:doc "Function that subtracts two quantities."}
  quantities-subtract* (->quantities-equal-fn -))

(defmacro ->quantity
  "Macro that expands to create a quantity from the given numeric magnitude and unit map if *assert* is true
  and simply expands to the magnitude otherwise."
  [q u]
  (if *assert*
    `(wparker.units.core/->quantity* ~q ~u)
    q))

(defmacro def-quantities-macro
  "Defines a macro that inserts a quantity-checking function if *assert* is true and a plain math function otherwise.  Takes four arguments:
  1. The name of the macro to define.
  2. The docstring for the macro macro to define.
  3. The quoted fully-qualified symbol of the checked function.  This does need to a symbol, not an inline function.
  4. The quoted fully-qualified symbol of the equivalent unchecked function.  This does need to be a symbol."
  [macro-name macro-doc checked-fn unchecked-fn]
  `(defmacro ~macro-name ~macro-doc [q1# q2#]
     (if *assert*
       `(~(symbol ~checked-fn) ~q1# ~q2#) ;; The symbol call avoids placing the function in code, which causes problems when the functions are closures.
       `(~(symbol ~unchecked-fn) ~q1# ~q2#)))) ;; See discussion at http://stackoverflow.com/questions/11191992/functions-with-closures-and-eval-in-clojure

(def-quantities-macro quantities-equal? "Macro that expands to check quantity equality if the global flag *assert* is true and expands to a simple equality check otherwise."
  'wparker.units.core/quantities-equal?* 'clojure.core/==)

(def-quantities-macro quantities-add "Macro that expands to add quantities if the global flag *assert* is true and expands to simple addition otherwise."
  'wparker.units.core/quantities-add* 'clojure.core/+)

(def-quantities-macro quantities-subtract "Macro that expands to subtract quantities if the global flag *assert* is true and expands to simple subtraction otherwise."
  'wparker.units.core/quantities-subtract* 'clojure.core/-)

(def-quantities-macro quantities-multiply "Macro that expands to multiply quantities if the global flag *assert* is true and expands to simple multiplication otherwise."
  'wparker.units.core/quantities-multiply* 'clojure.core/*)

(def-quantities-macro quantities-divide "Macro that expands to divide quantities if the global flag *assert* is true and expands to simple division otherwise."
  'wparker.units.core/quantities-divide* 'clojure.core//)
