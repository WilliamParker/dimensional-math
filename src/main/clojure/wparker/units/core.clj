(ns wparker.units.core
  (:require [clojure.string])
  (:import [java.lang
            Number]
           [wparker.units
            IQuantity]
           [clojure.lang
            ExceptionInfo]))

(defn ->quantity*
  "Builds a quantity.  The magnitude is a number and units is a persistent Clojure map of keyword to powers of units.
  For example, if we want 5 square feet, we would call (->quantity* 5 {:ft 2})."
  [magnitude units]
  (proxy [Number IQuantity] []  ;;Uses proxy since reify does not support abstract classes.
    (byteValue [] (byte magnitude))
    (doubleValue [] (double magnitude))
    (floatValue [] (float magnitude))
    (intValue [] (int magnitude))
    (longValue [] (long magnitude))
    (shortValue [] (long magnitude))
    ;; Object method implementations
    (equals [other] (= magnitude other))
    (hashCode [] (hash magnitude))
    (toString [] (str magnitude " "
                      (apply str (clojure.string/join " "
                                                      (map #(str (name (key %))
                                                                 "^"
                                                                 (val %))
                                                           units)))))
    ;; IUnit implementations
    (getUnits [] units)))

(defn ^{:private true :doc "Filters units with a power of 0 from a units map"}
  filter-zeroes [units]
  (into {}
        (filter #(not (== 0 (val %))) units)))

(defn units-equal?
  "Tests if two quantities have the same units."
  [quantity-1 quantity-2]
  (assert (every? (partial instance? IQuantity) [quantity-1 quantity-2]))
  (let [units-1 (.getUnits ^IQuantity quantity-1)
        units-2 (.getUnits ^IQuantity quantity-2)]
    (boolean (and (= (set (keys units-1))
                     (set (keys units-2)))
                  (every? #(== (units-1 %) ;; TODO: Update to allow user to specify an equality tolerance for floating-point powers.
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
    (assert (every? (partial instance? IQuantity) [quantity-1 quantity-2]))
    (->quantity* (math-fn quantity-1 quantity-2)
                 (filter-zeroes (let [units-1 (.getUnits ^IQuantity quantity-1)
                                      units-2 (.getUnits ^IQuantity quantity-2)
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
     (assert (every? (partial instance? IQuantity) [quantity-1 quantity-2]))
     (if (units-equal? quantity-1 quantity-2)
       (->quantity* (math-fn quantity-1 quantity-2)
                   (.getUnits ^IQuantity quantity-1))
       (throw (ExceptionInfo. "Quantities do not have the same units" {:quantity-1 quantity-1
                                                                       :quantity-2 quantity-2}))))))

(defn quantities-equal?*
  "Tests if two quantities are equal.  The arguments should always be quantities."
  [quantity-1 quantity-2]
  (assert (every? (partial instance? IQuantity) [quantity-1 quantity-2]))
  (let [units-1 (.getUnits ^IQuantity quantity-1)
        units-2 (.getUnits ^IQuantity quantity-2)]
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

(defmacro quantities-equal?
  "Macro that expands to check quantity equality if the global flag *assert* is true and expands to a simple equality check otherwise."
  [q1 q2]
  (if *assert*
    `(wparker.units.core/quantities-equal?* ~q1 ~q2)
    `(== ~q1 ~q2)))

(defmacro quantities-add [q1 q2]
  "Macro that expands to add quantities if the global flag *assert* is true and expands to simple addition otherwise."
  (if *assert*
    `(wparker.units.core/quantities-add* ~q1 ~q2)
    `(+ ~q1 ~q2)))

(defmacro quantities-subtract [q1 q2]
  "Macro that expands to subtract quantities if the global flag *assert* is true and expands to simple subtraction otherwise."
  (if *assert*
    `(wparker.units.core/quantities-subtract* ~q1 ~q2)
    `(- ~q1 ~q2)))

(defmacro quantities-multiply [q1 q2]
  "Macro that expands to multiply quantities if the global flag *assert* is true and expands to simple multiplication otherwise."
  (if *assert*
    `(wparker.units.core/quantities-multiply* ~q1 ~q2)
    `(* ~q1 ~q2)))

(defmacro quantities-divide [q1 q2]
  "Macro that expands to divide quantities if the global flag *assert* is true and expands to simple division otherwise."
  (if *assert*
    `(wparker.units.core/quantities-divide* ~q1 ~q2)
    `(/ ~q1 ~q2)))
