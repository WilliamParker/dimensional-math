(ns wparker.units.core-test
  (:require [clojure.test :refer :all]
            [wparker.units.core :refer :all]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.clojure-test :as check-test]
            [clojure.math.combinatorics :as combo])
  (:import [wparker.units
            IQuantity]))

;;; Basic sanity tests of addition, subtraction, multiplication, and division.
(deftest quantities-multiply-test
  (let [a (->quantity* 7 {:kg 7})
        b (->quantity* 3 {:kg 3 :m 2})
        multiplied (quantities-multiply* a b)]
    (is (= multiplied 21))
    (is (= (.getUnits ^IQuantity multiplied)
           {:kg 10 :m 2}))
    (is (quantities-equal?* multiplied
                           (->quantity* 21 {:kg 10 :m 2})))))

(deftest quantities-divide-test
  (let [a (->quantity* 6 {:kg 7})
        b (->quantity* 3 {:kg 3 :m 2})
        divided (quantities-divide* a b)]
    (is (= divided 2))
    (is (= (.getUnits ^IQuantity divided)
           {:kg 4 :m -2}))
    (is (quantities-equal?* divided
                            (->quantity* 2 {:kg 4 :m -2})))))

(deftest quantities-add-test
  (let [a (->quantity* 11 {:ft 2})
        b (->quantity* 6 {:ft 2})
        added (quantities-add* a b)]
    (is (= added 17))
    (is (= (.getUnits ^IQuantity added)
           {:ft 2}))))

(deftest quantities-subtract-test
  (let [a (->quantity* 11 {:ft 2})
        b (->quantity* 5 {:ft 2})
        subtracted (quantities-subtract* a b)]
    (is (= subtracted 6))
    (is (= (.getUnits ^IQuantity subtracted)
           {:ft 2}))))

(deftest ^{:doc "Verify that units which cancel out to zero are removed."}
  zero-out-units-test
  (let [a (->quantity* 10 {:m 1 :s 1})
        b (->quantity* 5 {:m -1 :s 1})
        multiplied (quantities-multiply* a b)]
    (is (= (.getUnits ^IQuantity multiplied) {:s 2}))))

;;; Verify that the various macros behave as expected for both true and false values of *assert*.  Note that
;;; *assert* is set to true in project.clj.  It is necessary to use eval in order to delay compilation of test data
;;; until *assert* has been rebound to false.

(deftest quantities-equivalent-macro-test
  (let [a `(->quantity* 15 {:lbs 10})
        b `(->quantity* 15 {:lbs 9})]
    (is (binding [*assert* false]
          (eval `(quantities-equal? ~a ~b))))
    (is (not (quantities-equal?
              (eval a)
              (eval b))))))

(deftest quantity-build-macro-test
  (let [a `(->quantity 5 {:kg 2})]
    (binding [*assert* false]
      (let [test-q (eval a)]
        (is (= test-q 5))
        (is (not (instance? IQuantity test-q)))))
    (let [test-q (eval a)]
      (is (= test-q 5))
      (is (= (.getUnits ^IQuantity test-q)
             {:kg 2})))))

(deftest quantities-added-macro-test
  (let [a `(->quantity* 15 {:lbs 10})
        b `(->quantity* 15 {:lbs 10})]
    (let [added (binding [*assert* false]
                  (eval `(quantities-add ~a ~b)))]
      (is (= added 30))
      (is (not (instance? IQuantity added))))
    (let [added (quantities-add (eval a) (eval b))]
      (is (= added 30))
      (is (= (.getUnits ^IQuantity added) {:lbs 10})))))

(deftest quantities-subtracted-macro-test
  (let [a `(->quantity* 15 {:lbs 10})
        b `(->quantity* 5 {:lbs 10})]
    (let [subtracted (binding [*assert* false]
                       (eval `(quantities-subtract ~a ~b)))]
      (is (= subtracted 10))
      (is (not (instance? IQuantity subtracted))))
    (let [subtracted (quantities-subtract (eval a) (eval b))]
      (is (= subtracted 10))
      (is (= (.getUnits ^IQuantity subtracted) {:lbs 10})))))

(deftest quantities-multiplied-macro-test
  (let [a `(->quantity* 10 {:ft 5})
        b `(->quantity* 5 {:lbs 10})]
    (let [multiplied (binding [*assert* false]
                       (eval `(quantities-multiply ~a ~b)))]
      (is (= multiplied 50))
      (is (not (instance? IQuantity multiplied))))
    (let [multiplied (quantities-multiply (eval a) (eval b))]
      (is (= multiplied 50))
      (is (= (.getUnits ^IQuantity multiplied) {:lbs 10 :ft 5})))))

(deftest quantities-divided-macro-test
  (let [a `(->quantity* 10 {:ft 5})
        b `(->quantity* 5 {:lbs 10})]
    (let [divided (binding [*assert* false]
                    (eval `(quantities-divide ~a ~b)))]
      (is (= divided 2))
      (is (not (instance? IQuantity divided))))
    (let [divided (quantities-divide (eval a) (eval b))]
      (is (= divided 2))
      (is (= (.getUnits ^IQuantity divided) {:ft 5 :lbs -10})))))

(deftest ^{:doc "Test of toString on the IQuantity implementation."}
  quantity-toString-test
  (let [a (->quantity* 5 {:m 1 :s -1})]
    (is (some #{(str a)} ["5 m^1 s^-1"
                          "5 s^-1 m^1"]))))

;;; Parameterized tests using test.check of multiplication and addition.  This effectively tests
;;; ->quantity-operation-fn and ->quantities-equal-fn.  The effect is to have some transitive test coverage of
;;; division and subtraction.
;;;
;;; TODO: Write actual parameterized tests for subtraction and division.
;;; TODO: Investigate stronger assertions and less restricted generators.

(check-test/defspec parameterized-multiplication-test
  100
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
  100
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
