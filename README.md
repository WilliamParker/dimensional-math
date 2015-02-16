## units

A Clojure library for math with units.

## Continuous integration

https://travis-ci.org/WilliamParker/units
![Travis CI status](https://travis-ci.org/WilliamParker/units.svg?branch=master)

## Motivation

There are already some Clojure libraries for math with units, such as Meajure at https://github.com/arrdem/meajure.  However, I wanted something that would solely serve as extra validation during testing and have no impact outside of testing.  This library has the benefit of not creating any new types; all quantities are also their original numeric classes and can be seamlessly used with all functions that take the relevant numeric type.  Unit validation can also easily be turned off at compile time.  I believe the effect is to provide some added safety without imposing undue burdens on the programmer to manage more types and without imposing any performance costs after compilation when unit checking is turned off.  In other words, the objective of this library is to provide more lightweight unit validation of numeric operations.

## Key features

1. There are no actual changes to the numeric types.  Behind the scenes, the library maintains a mapping from numeric object instances to units mappings.  This mapping is based on object identities, not object equality.  The quantity builder functions create a new instance and associate it with the given units.  Note that constructors are used instead of .valueOf methods to ensure that a new instance is created, and the quantity builder always verifies that a new object instance is created in the quantity builder. The benefit of this is that all Clojure and Java code for processing numbers can be used without any additional wrapping.  The units verification is purely an additional verification step.  The intention is not to rely on any behavior of quantity-aware code other than in testing; the objective is simply to document code and squash otherwise difficult to detect bugs.  Code with quantities can seamlessly interface with non-quantity-aware code.  Note, however, that after a quantity is passed to non-quantity aware code no guarantees are made regarding any possible units on the output.

2. The library includes macros that expand to quantity-aware functions if the \*assert\* var is bound to true and to ordinary function calls otherwise.  Thus, quantity checking can be turned on and off at compile time.

3. The library includes utility functions for generating quantity-aware functions.  Currently only 2-arity functions can be generated.  One of these function builders generates a function that expects quantities with equal units; the other expects quantities with differents units and accepts an argument of a function for merging units.  The library also includes functionality to generate macros that expand to a quantity-checked function if \*assert\* is true and to an ordinary function otherwise.

4. Users can easily extend the library to support any numeric type by extending the protocol CloneableNumber onto that type.  The only requirement is that it be possible to create a new instance of the numeric type.


To be clear, this is highly experimental.  API changes are entirely possible and indeed likely, so usage in anything other than throwaway hobby projects is discouraged at this time.  There is significant test coverage, but it isn't complete coverage, and everyone writes bugs.

## Usage
Define quantities

```
(def length (->quantity 3 {:meters 1}))
```

```
(def width (->quantity 4 {:meters 1}))
```

Then operate on the quantities

```
(def area (quantities-multiply length width))
=> 12 meters^2

area
=> 12

(quantity->units area)
=> {:meters 2}
```

Note that area is bound to a native numeric type, not a type defined in this library.

```
(type area)
=> java.lang.Long
```

The library stores information on units for each numeric instance, and creates a new instance every time a new quantity is created.  Note that the library assumes that all numeric types are immutable.
This information is stored in a reference-based (not equality-based) hash map.  This is necessary since the Java numeric classes are final; the effect of this approach is to add information and functions to these classes
without the need to inherit from them.  Also note that the map uses weak references to the numbers to avoid memory leaks.


Quantities that are compared should have equal units.

```
(quantities-equal? (->quantity 1 {:ft 1}) (->quantity 1 {:m 1}))
=>
ExceptionInfo Two quantities that are compared should have equal units.

```

To extend quantity checking to a new numeric type, simple implement the CloneableNumber protocol on it.  The protocol consists of one function, clone-number, that takes an instance of a number and returns a different but equal instance of the same numeric type.  It is recommended to use the ->checked-copy-fn wrapper, which wraps the copy function and throws an exception if it breaks the library's contract.  For example, if the CloneableNumber was not extended onto the Integer class, we could do so with:

```
(extend-protocol CloneableNumber
  java.lang.Integer
  (clone-number [x] ((->checked-copy-fn #(java.lang.Integer. ^java.lang.Integer %)) x)))
```

->checked-copy-fn takes a single argument - a 1-arity function that copies the number.  The resulting function is also a 1-arity function that copies the number, but with verification that the result is a new object instance.

The library also allows the easy creation of functions that check or propogate units from preexisting mathematical functions.  At the moment only two-arity functions are supported.  The key functions are ->quantity-equal-fn, which wraps a two-arity function and throws an exception if the quantities of the arguments are not equal, and ->quantity-operation-fn, which takes a two-arity mathematical function and a two-arity function for merging units.  The units merging function receives the magnitude of the unit on the first quantity as its first argument and the magnitude of the same unit on the second quantity as its second argument; it returns the magnitude of that unit on the result.  If the unit in question does not exist on one of the arguments that value passed is 0.  For example, addition and division are defined with:

```
(def ^{:doc "Function that adds two quantities"}
  quantities-add* (->quantities-equal-fn +))

(def ^{:doc "Function that divides two quantities."}
        quantities-divide* (->quantity-operation-fn / -))
```
Note the two arguments to quantities-divide\*. The first argument is the actual mathematical function.  The second argument states that the magnitudes of the units of the quantities should be subtracted. For example, when a quantity with units {:meters 2} is divided by a quantity with units {:meters 1}, we get a quantity with units {:meters 1}.

We can now define a macro that expands to a call to a quantity-checking function at compile time if \*assert\* is true and to an ordinary function otherwise with def-quantities-macro.  For example, for addition:

```
(def-quantities-macro quantities-add "Macro that expands to add quantities if the global flag *assert* is true and expands to simple addition otherwise."
  'wparker.units.core/quantities-add* 'clojure.core/+)
  ```

  Note that the last two arguments are fully-qualified quotes symbols.  These do need to be symbols instead of function objects (like a call to one of the function builders in this library).  The reason is due to problems with embedding functions defined with lexical scope (i.e. closures) in code generated by macros; see the discussion at http://stackoverflow.com/questions/11191992/functions-with-closures-and-eval-in-clojure

## Performance notes

It is probably obvious that turning on quantity checking will have performance consequences.  Where large numerical calculations are used this may be significant.  Note, however, that this slowdown may not be linear relative to the unchecked speed.  Firstly, the quantities are stored in a synchronized mutable map.  Thus, the use of quantity checking may limit the effectiveness of parallelization.  Secondly, the quantity builder functions coerce primitive types into boxed values.  A few quantities could potentially force a much larger number of operations to be done with boxed arithmetic rather than primitive math.

## Known defects/limitations/todos:
-Add ability to specify an equality range instead of just using ==.  This is necessary for floating-point math.  Note that this does not just apply to magnitudes; unit equality with non-integer powers could have problems as well.
-Add more functions.
-Investigate using a compiler flag other than \*assert\*.  This may not be viable; \*assert\* has the useful property of existing independent of Clojure compilation since it is defined in clojure.lang.RT.java.  See https://github.com/clojure/clojure/blob/master/src/jvm/clojure/lang/RT.java#L197 for details.

In general, this library does use the numeric classes in somewhat strange ways.  I have (hopefully) avoided any reliance on non-contractual behavior in the Java numerical classes.  In the case of the Clojure numerical classes, there is no formal specification that I know of, but the library uses obvious method calls, not hacks.  The quantity generator functions verify that a new object instance is created, so a change that altered this behavior would immediately become obvious.

## License

Copyright © 2015 William Parker

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
