(ns wparker.dimensional-math.core-test
  (:require [clojure.test :refer :all]
            [wparker.dimensional-math.core :refer :all]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.clojure-test :as check-test]
            [clojure.math.combinatorics :as combo]
            [clojure.set :as clj-set])
  (:import [clojure.lang
            ArityException
            ExceptionInfo]
           [java.lang
            IllegalArgumentException]))

;; Allows tests against the private units map.
(def units-map (var-get #'wparker.dimensional-math.core/units-map))

(deftest ^{:doc "Test that all numeric types for which the library implemements clone-number implement it correctly.
           Also verifies that exceptions are thrown when invalid types are cloned."}
  number-copy-tests
  (let [number-copy-assertions (fn [a b]
                                 (is (= a b))
                                 (is (not (identical? a b)))
                                 (is (= (class a) (class b))))]
    (let [test-int (int 2)]
      (number-copy-assertions test-int (clone-number test-int)))
    (let [test-long (long 5)]
      (number-copy-assertions test-long (clone-number test-long)))
    (let [test-short (short 15)]
      (number-copy-assertions test-short (clone-number test-short)))
    (let [test-float (float 0.5)]
      (number-copy-assertions test-float (clone-number test-float)))
    (let [test-double (float 0.25)]
      (number-copy-assertions test-double (clone-number test-double)))
    (let [test-byte (byte 1)]
      (number-copy-assertions test-byte (clone-number test-byte)))
    (let [test-bigdecimal (bigdec 17)]
      (number-copy-assertions test-bigdecimal (clone-number test-bigdecimal)))
    (let [test-ratio (/ 1 3)]
      (is (ratio? test-ratio)) ;;Verify that the division is placed in a fraction instead of rounding.
      (number-copy-assertions test-ratio (clone-number test-ratio)))
    (let [test-biginteger (biginteger 25)]
      (is (instance? java.math.BigInteger test-biginteger))
      (number-copy-assertions test-biginteger (clone-number test-biginteger)))
    (let [test-bigint (bigint 25)]
      (is (instance? clojure.lang.BigInt test-bigint))
      (number-copy-assertions test-bigint (clone-number test-bigint)))
    (is (thrown? ExceptionInfo (clone-number :a)))
    (is (thrown? ExceptionInfo (clone-number true)))
    (is (thrown? IllegalArgumentException (clone-number nil)))))

(deftest ^{:doc "Basic sanity test that creating a quantity correctly updates the units map."}
  test-integer-quantity-creation
  (let [magnitude-1 (int 4)
        quantity-1 (->quantity* magnitude-1 {:ft 1})]
    (is (= (.get units-map quantity-1)
           {:ft 1}))
    (is (nil? (.get units-map magnitude-1)))
    (is (nil? (.get units-map (int 4))))))

(check-test/defspec quantity-creation-parameterized
  50
  (prop/for-all [n (gen/one-of [gen/int gen/ratio]) ;; TODO: Add other numeric type generators when they are available.
                 units (gen/map gen/keyword gen/int)]
                (let [quantity-1 (->quantity* n units)
                      quantity-units (.get units-map quantity-1)]
                  ;; We can't test that the units map that is retrieved is equal to the initial units map
                  ;; since zero powers are filtered out.  We iterate over the entries in the map and ensure that
                  ;; each was handled correctly instead; we also check that there are no extra keys in the retrieved map.
                  (doseq [entry units]
                    (if (== (val entry) 0)
                      (is (not (contains? (set (keys quantity-units))
                                          (key entry))))
                      (is (= ((key entry) quantity-units)
                             (val entry)))))
                  (is (clj-set/subset? (set (keys quantity-units))
                                       (set (keys units))))
                  (is (nil? (.get units-map n))))))

(deftest units-equal-test
  (let [a (->quantity* 1 {:ft 1 :lbs 1})
        b (->quantity* 7 {:ft 1})
        c (->quantity* 7 {:ft 2})
        d (->quantity* 9 {:m 1})
        different-units [a b c d]
        not-equal-combinations (concat (combo/combinations different-units 2)
                                       (combo/combinations different-units 3))
        e (->quantity* 2 {:ft 1 :lbs 1})
        f (->quantity* 3 {:ft 1 :lbs 1})
        same-units-pairs (combo/combinations [a e f] 2)]
    (testing "Different units"
      (doseq [unit-combination not-equal-combinations]
        (is (false? (apply units-equal? unit-combination)))))
    (testing "Same units"
      (doseq [unit-combination same-units-pairs]
        (is (true? (apply units-equal? unit-combination))))
      (is (true? (units-equal? a e f))))
    (testing "Allowable difference tests"
      (let [g (->quantity* 1 {:ft 1.01 :lbs 1})
            h (->quantity* 1 {:ft 0.99 :lbs 1})
            i (->quantity* 1 {:ft 1 :lbs 1.1})]
        (binding [*unit-power-error* 0.03]
          (is (true? (units-equal? a g)))
          (is (true? (units-equal? a h)))
          (is (true? (units-equal? a g h)))
          (is (false? (units-equal? a i))))))))

;;; Basic sanity tests of addition, subtraction, multiplication, and division.
(deftest quantities-multiply-test
  (let [a (->quantity* 7 {:kg 7})
        b (->quantity* 3 {:kg 3 :m 2})
        c (->quantity 2 {:kg -10 :m 1})
        multiplied-ab (quantities-multiply* a b)
        multiplied-abc (quantities-multiply* a b c)
        multiplied-acb (quantities-multiply* a c b)]
    (testing "Multiplication of two quantities"
      (is (= multiplied-ab 21))
      (is (= (quantity->units multiplied-ab)
             {:kg 10 :m 2}))
      (is (quantities-equal?* multiplied-ab
                              (->quantity* 21 {:kg 10 :m 2}))))
    (testing "Multiplication of three quantities"
      (is (= multiplied-abc
             multiplied-acb
             42))
      (is (= (quantity->units multiplied-abc)
             (quantity->units multiplied-acb)
             {:m 3}))
      (is (quantities-equal?* multiplied-abc
                              multiplied-acb
                              (->quantity 42 {:m 3}))))))

(deftest quantities-divide-test
  (let [a (->quantity* 6 {:kg 7})
        b (->quantity* 3 {:kg 3 :m 2})
        c (->quantity* 2 {:m -2})
        divided-ab (quantities-divide* a b)
        divided-abc (quantities-divide* a b c)]
    (testing "Division of two quantities"
      (is (= divided-ab 2))
      (is (= (quantity->units divided-ab)
             {:kg 4 :m -2}))
      (is (quantities-equal?* divided-ab
                              (->quantity* 2 {:kg 4 :m -2}))))
    (testing "Division of three quantities"
      (is (= divided-abc 1))
      (is (= (quantity->units divided-abc)
             {:kg 4}))
      (is (quantities-equal?* divided-abc
                              (->quantity* 1 {:kg 4}))))))

(deftest quantities-add-test
  (let [a (->quantity* 11 {:ft 2})
        b (->quantity* 6 {:ft 2})
        c (->quantity* 3 {:ft 2})
        added-ab (quantities-add* a b)
        added-abc (quantities-add* a b c)
        added-acb (quantities-add* a c b)]
    (testing "Addition of two quantities"
      (is (= added-ab 17))
      (is (= (quantity->units added-ab)
             {:ft 2}))
      (is (quantities-equal?* added-ab
                              (->quantity* 17 {:ft 2}))))
    (testing "Addition of three quantities"
      (is (= added-abc
             added-acb
             20))
      (is (= (quantity->units added-abc)
             (quantity->units added-acb)
             {:ft 2}))
      (is (quantities-equal?* added-abc
                              added-acb
                              (->quantity* 20 {:ft 2}))))))

(deftest quantities-subtract-test
  (let [a (->quantity* 11 {:ft 2})
        b (->quantity* 5 {:ft 2})
        c (->quantity* 2 {:ft 2})
        subtracted-ab (quantities-subtract* a b)
        subtracted-abc (quantities-subtract* a b c)]
    (testing "Subtraction of two quantities"
      (is (= subtracted-ab 6))
      (is (= (quantity->units subtracted-ab)
             {:ft 2}))
      (is (quantities-equal?* subtracted-ab
                              (->quantity* 6 {:ft 2}))))
    (testing "Subtraction of three quantities"
      (is (= subtracted-abc 4))
      (is (= (quantity->units subtracted-abc)
             {:ft 2}))
      (is (quantities-equal?* subtracted-abc)
          (->quantity* 4 {:ft 2})))))

(deftest ^{:doc "Verify that units which cancel out to zero are removed."}
  zero-out-units-test
  (let [a (->quantity* 10 {:m 1 :s 1})
        b (->quantity* 5 {:m -1 :s 1})
        multiplied (quantities-multiply* a b)]
    (is (= (quantity->units multiplied) {:s 2}))))

;;; Tests that different units cannot be incorrectly mixed.

(deftest compare-different-units
  (let [unequal-quantities [(->quantity* 5 {:ft 1})
                            (->quantity* 5 {:ft 2})
                            (->quantity* 5 {:m 2})
                            (->quantity* 5 {:ft -1})]
        unequal-combinations (concat (combo/combinations unequal-quantities 2)
                                     (combo/combinations unequal-quantities 3))]
    (doseq [qs unequal-combinations]
      (is (thrown? ExceptionInfo (apply quantities-equal?* qs)))
      (is (thrown? ExceptionInfo (apply quantities-add* qs)))
      (is (thrown? ExceptionInfo (apply quantities-subtract* qs))))))

(deftest test-quantity-equality-same-units
  (let [a (->quantity* 4 {:m 1})
        b (->quantity* 4M {:m 1})
        c (->quantity* 3 {:m 1})
        d (->quantity* 0.5 {:m 1})
        e (->quantity* (/ 1 2) {:m 1})
        f (->quantity* 0.5M {:m 1})]
    (is (quantities-equal?* a b))
    (is (not (quantities-equal?* b c)))
    (is (not (quantities-equal?* a c)))
    ;; Test of equality of .5 to the Clojure ratio type with value 1/2.  This works
    ;; since .5 has an exact binary representation.
    (is (quantities-equal?* d e))
    (is (quantities-equal?* d e f))
    (is (not (quantities-equal?* e f a)))
    (is (ratio? e))))

(deftest test-quantity-less-than?
  (let [a (->quantity 3 {:J 1})
        b (->quantity 2 {:J 1})
        c (->quantity -1 {:J 1})
        d (->quantity 2 {:J 1})
        e (->quantity 1 {:J 2})]
    (testing "Less-than quantity tests"
      (is (true? (quantities-less-than?* c b a)))
      (is (true? (quantities-less-than?* b a)))
      (is (true? (quantities-less-than?* c b)))
      (is (false? (quantities-less-than?* b d)))
      (is (false? (quantities-less-than?* d b)))
      (is (false? (quantities-less-than?* b a c)))
      (is (thrown? ExceptionInfo (quantities-less-than?* c e)))
      (binding [*check-units* false]
        (is (true? (quantities-less-than?* c e)))))
    (testing "Greater-than quantity tests"
      (is (true? (quantities-greater-than?* a b c)))
      (is (true? (quantities-greater-than?* a b)))
      (is (true? (quantities-greater-than?* b c)))
      (is (false? (quantities-greater-than?* c b a)))
      (is (false? (quantities-greater-than?* b a)))
      (is (false? (quantities-greater-than?* c b)))
      (is (thrown? ExceptionInfo (quantities-greater-than?* e c)))
      (binding [*check-units* false]
        (is (true? (quantities-greater-than?* e c)))))))

(deftest ^{:doc "Verify that the without-unit-checks test hook disables unit checking."}
  test-without-unit-checks
  (without-unit-checks
    (fn []
      (is (not (quantity? (->quantity* 7 {:N 1}))))))
  (let [quantity-1 (->quantity* 1 {:m 1})
        quantity-2 (->quantity* 1 {:m 2})
        quantity-3 (->quantity* 2 {:m 1})]
    (is (thrown? ExceptionInfo (not (quantities-equal?* quantity-1 quantity-2))))
    (is (thrown? ExceptionInfo (quantities-add* quantity-1 quantity-2)))
    (without-unit-checks
      (fn []
        (do
          (is (quantities-equal?* quantity-1 quantity-2))
          (is (not (quantities-equal?* quantity-1 quantity-3)))
          (is (= 2
                 (quantities-add* quantity-1 quantity-2)))
          ;; For multiplication, verify that no quantity is created as a result of the operation.
          (with-redefs [->quantity* (fn []
                                      (throw (RuntimeException. "No quantity should be created when *check-units* is false")))]
            (is (= 1
                   (quantities-multiply* quantity-1 quantity-2)))))))))

(deftest ^{:doc "Tests of unit functions when provided with a single argument."}
  test-single-argument-quantity-operations
  (testing "Addition of a single quantity should yield an equivalent quantity"
    (let [single-quantity (->quantity* 3 {:m 2})
          single-addition (quantities-add* single-quantity)
          single-multiplication (quantities-multiply* single-quantity)]
      (is (== single-addition single-multiplication single-quantity))
      (is (quantities-equal?* single-addition single-multiplication single-quantity))
      (is (= (quantity->units single-addition)
             (quantity->units single-quantity)
             (quantity->units single-multiplication)
             {:m 2}))))
  (testing "Subtraction of a single quantity should yield the previous quantity negated"
    (let [pos-quantity (->quantity 2 {:kg 1})
          neg-quantity (->quantity -2 {:kg 1})
          pos-subtracted (quantities-subtract* pos-quantity)
          neg-subtracted (quantities-subtract* neg-quantity)]
      (is (== pos-subtracted -2))
      (is (== neg-subtracted 2))
      (is (quantities-equal?* pos-quantity neg-subtracted))
      (is (quantities-equal?* neg-quantity pos-subtracted))
      (is (not (quantities-equal?* pos-quantity pos-subtracted)))
      (is (not (quantities-equal?* neg-quantity neg-subtracted)))
      (is (= (quantity->units pos-subtracted)
             (quantity->units neg-subtracted)
             {:kg 1}))))
  (testing "Division of a single quantity should yield the quantity divided by one"
    (let [single-quantity (->quantity 2 {:lbs 1})
          single-division (quantities-divide* single-quantity)
          expected-division (->quantity (/ 1 2) {:lbs -1})]
      (is (== single-division expected-division))
      (is (quantities-equal?* single-division expected-division))))
  (testing "Equality operations on a single quantity should return true"
    (is (true? (quantities-equal?* (->quantity* 1 {:C 3}))))))

(deftest test-zero-argument-quantity-operations
  (is (thrown? IllegalArgumentException (quantities-add*)))
  (is (thrown? IllegalArgumentException (quantities-subtract*)))
  (let [no-arg-multiplication (quantities-multiply*)]
    (== no-arg-multiplication 1)
    (quantities-equal?* no-arg-multiplication (->quantity* 1 {}))
    (= (quantity->units no-arg-multiplication) {}))
  (is (thrown? ArityException (quantities-divide*) #"core////")
      "Quantity division with zero arguments should return the same exception as raw division with zero arguments")
  (is (thrown? IllegalArgumentException (quantities-equal?*)
               #"Quantity comparison functions must take at least one argument")))

;;; Verify that the various macros behave as expected for both true and false values of *compile-with-unit-checks*.  Note that
;;; *compile-with-unit-checks* is set to true in project.clj.  It is necessary to use eval in order to delay compilation of test data
;;; until *compile-with-unit-checks* has been rebound to false.

(deftest quantities-equivalent-macro-test
  (let [a `(->quantity* 15 {:lbs 9})
        b `(->quantity* 15 {:lbs 10})
        c `(->quantity* 15M {:lbs 10})]
    (is (binding [*compile-with-unit-checks* false]
          (and (eval `(quantities-equal? ~a ~b))
               (eval `(quantities-equal? ~b ~c)))))
    (is (thrown? ExceptionInfo (quantities-equal?
                                (eval a)
                                (eval b))))
    (is (quantities-equal? (eval b) (eval c)))))

(deftest quantity-inequality-macro-tests
  (let [a `(->quantity* 5 {:Pa 1})
        b `(->quantity* 4 {:Pa 2})]
    (binding [*compile-with-unit-checks* false]
      (is (true? (eval `(quantities-less-than? ~b ~a))))
      (is (false? (eval `(quantities-less-than? ~a ~b))))
      (is (true? (eval `(quantities-greater-than? ~a ~b))))
      (is (false? (eval `(quantities-greater-than? ~b ~a)))))
    (is (thrown? ExceptionInfo (quantities-less-than? (eval b)
                                                      (eval a))))
    (is (thrown? ExceptionInfo (quantities-greater-than? (eval a)
                                                         (eval b))))))

(deftest quantity-build-macro-test
  (let [a `(->quantity 5 {:kg 2})]
    (binding [*compile-with-unit-checks* false]
      (let [test-q (eval a)]
        (is (= test-q 5))
        (is (not (quantity? test-q)))))
    (let [test-q (eval a)]
      (is (= test-q 5))
      (is (= (quantity->units test-q)
             {:kg 2})))))

(deftest quantities-added-macro-test
  (let [a `(->quantity* 15 {:lbs 10})
        b `(->quantity* 15 {:lbs 10})]
    (let [added (binding [*compile-with-unit-checks* false]
                  (eval `(quantities-add ~a ~b)))]
      (is (= added 30))
      (is (not (quantity? added))))
    (let [added (quantities-add (eval a) (eval b))]
      (is (= added 30))
      (is (= (quantity->units added) {:lbs 10})))))

(deftest quantities-subtracted-macro-test
  (let [a `(->quantity* 15 {:lbs 10})
        b `(->quantity* 5 {:lbs 10})]
    (let [subtracted (binding [*compile-with-unit-checks* false]
                       (eval `(quantities-subtract ~a ~b)))]
      (is (= subtracted 10))
      (is (not (quantity? subtracted))))
    (let [subtracted (quantities-subtract (eval a) (eval b))]
      (is (= subtracted 10))
      (is (= (quantity->units subtracted) {:lbs 10})))))

(deftest quantities-multiplied-macro-test
  (let [a `(->quantity* 10 {:ft 5})
        b `(->quantity* 5 {:lbs 10})]
    (let [multiplied (binding [*compile-with-unit-checks* false]
                       (eval `(quantities-multiply ~a ~b)))]
      (is (= multiplied 50))
      (is (not (quantity? multiplied))))
    (let [multiplied (quantities-multiply (eval a) (eval b))]
      (is (= multiplied 50))
      (is (= (quantity->units multiplied) {:lbs 10 :ft 5})))))

(deftest quantities-divided-macro-test
  (let [a `(->quantity* 10 {:ft 5})
        b `(->quantity* 5 {:lbs 10})]
    (let [divided (binding [*compile-with-unit-checks* false]
                    (eval `(quantities-divide ~a ~b)))]
      (is (= divided 2))
      (is (not (quantity? divided))))
    (let [divided (quantities-divide (eval a) (eval b))]
      (is (= divided 2))
      (is (= (quantity->units divided) {:ft 5 :lbs -10})))))

(deftest quantity->str-test
  (let [a (->quantity* 5 {:m 1 :s -1})]
    (is (some #{(quantity->str a)} ["5 m^1 s^-1"
                                    "5 s^-1 m^1"]))))

;;; Parameterized tests using test.check of addition, multiplication, subtraction, and division.  This effectively tests
;;; ->quantity-operation-fn and ->quantities-equal-fn as well, since these functions build the functions under test.

(check-test/defspec parameterized-multiplication-test
  50
  (prop/for-all [raw-qs (gen/resize 8 (gen/such-that #(> (count %) 2)
                                                     (gen/vector (gen/tuple gen/int (gen/map gen/keyword gen/int)))
                                                     80))]
                ;; Setting the size prevents integer overflow, since the size caps the magnitude of gen/int.
                ;; TODO: Cap gen/int while allowing for variable vector size.
                (let [ms (map first raw-qs)
                      us (map second raw-qs)
                      qs (map ->quantity* ms us)
                      permutations (map shuffle (repeat 5 qs))
                      permutations-multiplied (map (partial reduce quantities-multiply*) permutations)
                      result-pairs (combo/cartesian-product permutations-multiplied permutations-multiplied)]
                  ;; Check that every shuffled ordering is equal to every other shuffled ordering, both in terms of
                  ;; quantity and numeric equality.
                  (and (every? (partial apply quantities-equal?*) result-pairs)
                       (every? (partial apply =) result-pairs)
                       (every? (partial = (apply * ms)) permutations-multiplied)))))

;;; Note that the generators for addition and multiplication are different in that added quantities must all have the same units.

;; TODO: Improve generated tests so large numbers of tries on such-that calls are not needed.  The generators usually succeeded with
;; the default ceiling of 10; using 80 should dramatically reduce the probability of failure.
(check-test/defspec parameterized-addition-test
  50
  (prop/for-all [vs (gen/such-that #(> (count %) 2)
                                   (gen/vector gen/int)
                                   80)
                 units (gen/map gen/keyword gen/int)]
                (let [qs (map #(->quantity* % units) vs)
                      permutations (map shuffle (repeat 5 qs))
                      permutations-added (map (partial reduce quantities-add*) permutations)
                      result-pairs (combo/cartesian-product permutations-added permutations-added)]
                  ;; Check that every shuffled ordering is equal to every other shuffled ordering, both in terms of
                  ;; quantity and numeric equality.
                  (and (every? (partial apply quantities-equal?*) result-pairs)
                       (every? (partial apply =) result-pairs)
                       (every? (partial = (apply + vs)) permutations-added)))))

(check-test/defspec parameterized-subtraction-test
  50
  (prop/for-all [h gen/int
                 ts (gen/such-that #(> (count %) 2)
                                   (gen/vector gen/int)
                                   80)]
                ;; Shuffle the elements after the first, but keep the first element in the vector constant.
                (let [h-built (->quantity* h {:ft 1})
                      ts-built (map #(->quantity* % {:ft 1}) ts)
                      shuffled-ts (map shuffle (repeat 5 ts-built))
                      shuffled-full (map (partial concat [h-built]) shuffled-ts)
                      permutations-subtracted (map (partial reduce quantities-subtract*) shuffled-full)
                      result-pairs (combo/cartesian-product permutations-subtracted permutations-subtracted)]

                  (and (every? (partial apply quantities-equal?*) result-pairs)
                       (every? (partial apply =) result-pairs)
                       (every? (partial = (apply -
                                                 (concat [h] ts)))
                               permutations-subtracted)))))

;;; In order to avoid floating-point problems, take a vector of integers, multiply them all together, and verify that successively dividing the product
;;; by each of the set in any order yields 1.
(check-test/defspec parameterized-division-test
  50
  (prop/for-all [ns (gen/resize 8
                                (gen/such-that
                                 #(> (count %) 2)
                                 (gen/vector gen/s-pos-int)
                                 80))]
                (let [ns-product (->quantity* (reduce * ns) {:ft 10})
                      ns-shuffled (map shuffle (repeat 10 ns))
                      qs-shuffled (map (fn [lyst] (map #(->quantity* % {:ft 1}) lyst)) ns-shuffled)]
                  (every? #(let [divided (reduce quantities-divide* ns-product %)]
                             (and (== 1 divided)
                                  (let [size (count ns)
                                        units (quantity->units divided)]
                                    (if (not (= size 10))
                                      (= units {:ft (- 10 (count ns))})
                                      (= units {})))))
                          qs-shuffled))))
