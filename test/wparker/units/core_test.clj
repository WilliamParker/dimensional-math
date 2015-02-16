(ns wparker.units.core-test
  (:require [clojure.test :refer :all]
            [wparker.units.core :refer :all]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.clojure-test :as check-test]
            [clojure.math.combinatorics :as combo])
  (:import [clojure.lang
            ExceptionInfo]))

;; Allows tests against the private units map.
(def units-map (var-get #'wparker.units.core/units-map))

(deftest ^{:doc "Test that all numeric types for which the library implemements clone-number implement it correctly."}
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
      (number-copy-assertions test-bigint (clone-number test-bigint)))))

(deftest ^{:doc "Basic sanity test that creating a quantity correctly updates the units map."}
  test-integer-quantity-creation
  (let [magnitude-1 (int 4)
        quantity-1 (->quantity* magnitude-1 {:ft 1})]
    (is (= (.get units-map quantity-1)
           {:ft 1}))
    (is (nil? (.get units-map magnitude-1)))
    (is (nil? (.get units-map (int 4))))))

(check-test/defspec quantity-creation-parameterized
  50 ;; TODO: The time taken by this test seems to be far greater than the time taken when I build a large number of quantities
  ;; in a REPL.  Until this is fixed the test size must be limited to relatively small numbers.
  (prop/for-all [n (gen/one-of [gen/int gen/ratio]) ;; TODO: Add other numeric type generators when they are available.
                 units (gen/map gen/keyword gen/int)]
                (let [quantity-1 (->quantity* n units)]
                  (is (= (.get units-map quantity-1)
                         units))
                  (is (nil? (.get units-map n))))))

;;; Basic sanity tests of addition, subtraction, multiplication, and division.
(deftest quantities-multiply-test
  (let [a (->quantity* 7 {:kg 7})
        b (->quantity* 3 {:kg 3 :m 2})
        multiplied (quantities-multiply* a b)]
    (is (= multiplied 21))
    (is (= (quantity->units multiplied)
           {:kg 10 :m 2}))
    (is (quantities-equal?* multiplied
                            (->quantity* 21 {:kg 10 :m 2})))))

(deftest quantities-divide-test
  (let [a (->quantity* 6 {:kg 7})
        b (->quantity* 3 {:kg 3 :m 2})
        divided (quantities-divide* a b)]
    (is (= divided 2))
    (is (= (quantity->units divided)
           {:kg 4 :m -2}))
    (is (quantities-equal?* divided
                            (->quantity* 2 {:kg 4 :m -2})))))

(deftest quantities-add-test
  (let [a (->quantity* 11 {:ft 2})
        b (->quantity* 6 {:ft 2})
        added (quantities-add* a b)]
    (is (= added 17))
    (is (= (quantity->units added)
           {:ft 2}))))

(deftest quantities-subtract-test
  (let [a (->quantity* 11 {:ft 2})
        b (->quantity* 5 {:ft 2})
        subtracted (quantities-subtract* a b)]
    (is (= subtracted 6))
    (is (= (quantity->units ^IQuantity subtracted)
           {:ft 2}))))

(deftest ^{:doc "Verify that units which cancel out to zero are removed."}
  zero-out-units-test
  (let [a (->quantity* 10 {:m 1 :s 1})
        b (->quantity* 5 {:m -1 :s 1})
        multiplied (quantities-multiply* a b)]
    (is (= (quantity->units multiplied) {:s 2}))))

;;; Tests that different units cannot be incorrectly mixed.

(deftest compare-different-units
  (let [a (->quantity* 5 {:ft 1})
        b (->quantity* 5 {:ft 2})]
    (is (thrown? ExceptionInfo (quantities-equal?* a b)))
    (is (thrown? ExceptionInfo (quantities-add* a b)))
    (is (thrown? ExceptionInfo (quantities-subtract* a b)))))


;;; Test quantity equality

(deftest test-quantity-equality-same-units
  (let [a (->quantity* 4 {:m 1})
        b (->quantity* 4M {:m 1})
        c (->quantity* 3 {:m 1})
        d (->quantity 0.5 {:m 1})
        e (->quantity (/ 1 2) {:m 1})]
    (is (quantities-equal?* a b))
    (is (not (quantities-equal?* b c)))
    (is (not (quantities-equal?* a c)))
    ;; Test of equality of .5 to the Clojure ratio type with value 1/2.  This works
    ;; since .5 has an exact binary representation.
    (is (quantities-equal?* d e))
    (is (ratio? e))))

;;; Verify that the various macros behave as expected for both true and false values of *assert*.  Note that
;;; *assert* is set to true in project.clj.  It is necessary to use eval in order to delay compilation of test data
;;; until *assert* has been rebound to false.

(deftest quantities-equivalent-macro-test
  (let [a `(->quantity* 15 {:lbs 9})
        b `(->quantity* 15 {:lbs 10})
        c `(->quantity* 15M {:lbs 10})]
    (is (binding [*assert* false]
          (and (eval `(quantities-equal? ~a ~b))
               (eval `(quantities-equal? ~b ~c)))))
    (is (thrown? ExceptionInfo (quantities-equal?
                                (eval a)
                                (eval b))))
    (is (quantities-equal? (eval b) (eval c)))))

(deftest quantity-build-macro-test
  (let [a `(->quantity 5 {:kg 2})]
    (binding [*assert* false]
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
    (let [added (binding [*assert* false]
                  (eval `(quantities-add ~a ~b)))]
      (is (= added 30))
      (is (not (quantity? added))))
    (let [added (quantities-add (eval a) (eval b))]
      (is (= added 30))
      (is (= (quantity->units added) {:lbs 10})))))

(deftest quantities-subtracted-macro-test
  (let [a `(->quantity* 15 {:lbs 10})
        b `(->quantity* 5 {:lbs 10})]
    (let [subtracted (binding [*assert* false]
                       (eval `(quantities-subtract ~a ~b)))]
      (is (= subtracted 10))
      (is (not (quantity? subtracted))))
    (let [subtracted (quantities-subtract (eval a) (eval b))]
      (is (= subtracted 10))
      (is (= (quantity->units subtracted) {:lbs 10})))))

(deftest quantities-multiplied-macro-test
  (let [a `(->quantity* 10 {:ft 5})
        b `(->quantity* 5 {:lbs 10})]
    (let [multiplied (binding [*assert* false]
                       (eval `(quantities-multiply ~a ~b)))]
      (is (= multiplied 50))
      (is (not (quantity? multiplied))))
    (let [multiplied (quantities-multiply (eval a) (eval b))]
      (is (= multiplied 50))
      (is (= (quantity->units multiplied) {:lbs 10 :ft 5})))))

(deftest quantities-divided-macro-test
  (let [a `(->quantity* 10 {:ft 5})
        b `(->quantity* 5 {:lbs 10})]
    (let [divided (binding [*assert* false]
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
                                                     (gen/vector (gen/tuple gen/int (gen/map gen/keyword gen/int)))))]
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

(check-test/defspec parameterized-addition-test
  50
  (prop/for-all [vs (gen/such-that #(> (count %) 2)
                                   (gen/vector gen/int))
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
                 ts (gen/such-that #(> (count %) 2) (gen/vector gen/int))]
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
                                 (gen/vector gen/s-pos-int)))]
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
