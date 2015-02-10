# units

A Clojure library for math with units.

#Motivation

There are already some Clojure libraries for math with units, such as Meajure at https://github.com/arrdem/meajure.  However, I wanted something that would solely serve as extra validation during testing and have no impact outside of testing.  This library has the benefit of not creating any new types; all quantities are also their original numeric classes and can be seamlessly used with all functions that take the relevant numeric type.  Unit validation can also easily be turned off at compile time.  I believe the effect is to provide some added safety without imposing undue burdens on the programmer to manage more types and without imposing any performance costs after compilation when unit checking is turned off.  In other words, the objective of this library is to provide more lightweight unit validation of numeric operations.

#Key features

1. There are no actual changes to the numeric types.  Behind the scenes, the library maintains a mapping from numeric object instances to units mappings.  This mapping is based on object identities, not object equality.  The quantity builder functions create a new instance and associate it with the given units.  Note that constructors are used instead of .valueOf methods to ensure that a new instance is created, and the quantity builder always verifies that a new object instance is created in the quantity builder. The benefit of this is that all Clojure and Java code for processing numbers can be used without any additional wrapping.  The units verification is purely an additional verification step.  The intention is not to rely on any behavior of quantity-aware code other than in testing; the objective is simply to document code and squash otherwise difficult to detect bugs.  Code with quantities can seamlessly interface with non-quantity-aware code.  Note, however, that after a quantity is passed to non-quantity aware code no guarantees are made regarding any possible units on the output.

2. The library includes macros that expand to quantity-aware functions if the \*assert\* var is bound to true and to ordinary function calls otherwise.  Thus, quantity checking can be turned on and off at compile time.

3. The library includes utility functions for generating quantity-aware functions.  Currently only 2-arity functions can be generated.  One of these function builders generates a function that expects quantities with equal units; the other expects quantities with differents units and accepts an argument of a function for merging units.

4. Users can easily extend the library to support any numeric type by extending the protocol CloneableNumber onto that type.  The only requirement is that it be possible to create a new instance of the numeric type.


To be clear, this is highly experimental.  API changes are entirely possible and indeed likely, so usage in anything other than throwaway hobby projects is discouraged at this time.  There is significant test coverage, but it isn't complete coverage, and everyone writes bugs.

## Usage
Define quantities

```(def length (->quantity 3 {:meters 1}))```

```(def width (->quantity 4 {:meters 1}))```

Then operate on the quantities

```(quantities-multiply length width)
=> 12 meters^2
```

Quantities that are compared should have equal units.

```
(quantities-equal? (->quantity 1 {:ft 1}) (->quantity 1 {:m 1}))
=>
ExceptionInfo Two quantities that are compared should have equal units.

```

## Performance notes

It is probably obvious that turning on quantity checking will have performance consequences.  Where large numerical calculations are used this may be significant.  Note, however, that this slowdown may not be linear relative to the unchecked speed.  Firstly, the quantities are stored in a synchronized mutable map.  Thus, the use of quantity checking may limit code parallelization.  Secondly, the quantity builder functions coerce primitive types into boxed values.  A few quantities could potentially force a much larger number of operations to be done with boxed arithmetic rather than primitive math.

## Known defects/limitations/todos:
-This readme could use some improvement.
-Add ability to specify an equality range instead of just using ==.  This is necessary for floating-point math.  Note that this does not just apply to magnitudes; unit equality with non-integer powers could have problems as well.
-Investigate giving users some sort of helper to build the macros that expand to quantity-based or raw numeric functions.
-Add more functions.
-Investigate using a compiler flag other than \*assert\*.  This may not be viable; \*assert\* has the useful property of existing independent of Clojure compilation since it is defined in clojure.lang.RT.java.  See https://github.com/clojure/clojure/blob/master/src/jvm/clojure/lang/RT.java#L197 for details.


## License

Copyright © 2015 William Parker

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
