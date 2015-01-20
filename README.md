# units

A Clojure library for math with units.  The key features are

1. All numbers with units extend java.lang.Number.  The result is that these quantities can be used with any code that expects a Number as an argument without any conversion.  Note that the result of any such function call will not be a quantity with units, but the original quantity will be unchanged.
2. A user can use easily use arbitrary units; there is no unit definition step.  Each number simply has a map of keywords, each of which is a unit, to numbers, each of which is the power of that unit.
3. The user can choose to use unit-checked arithmetic or to skip all unit checking at compile time.  Each function that operates on quantities with units has a macro version that can expand into the checked version or the raw numeric version.  At the moment this depends on the \*assert\* global var in clojure.core.
4. Code that builds a quantity with units that receives a quantity when it expects a number will simply treat that quantity as a number and build a new quantity with a new set of units.  The result is to prevent any unanticipated clashes between usage of units in different parts of code.
5. Scalars are defined as quantities with an empty units map, not as numbers without units.  Calling quantity operations with any non-quantity argument is invalid.
6. Function constructors to easily build new functions that operate on quantities with units are provided.  Constructors for macro versions of these functions are not provided at the moment, although I would like to do so if possible.  I'm looking into it.  In the meantime, copying the pattern of the existing macros is fairly simple.

To be clear, this is highly experimental.  API changes are entirely possible and indeed likely, so usage in anything other than throwaway hobby projects is discouraged at this time.  There is significant test coverage, but it isn't complete coverage, and everyone writes bugs.

## Usage
Define quantities

```(def length (->quantity 3 {:meters 1}))```

```(def width (->quantity 4 {:meters 1}))```

Then operate on the quantities

```(quantities-multiply length width)
=> 12 meters^2```

Quantities that are compared should have equal units.

```
(quantities-equal? (->quantity 1 {:ft 1}) (->quantity 1 {:m 1}))
=>
ExceptionInfo Two quantities that are compared should have equal units.

```

## Known defects/limitations/todos:

-Add ability to build quantities that extend clojure.lang.Ratio (which itself extends java.lang.Number).  Currently Ratio types that are passed to the quantity builder
are coerced to decimals.
-Add ability to specify an equality range instead of just using ==.  This is necessary for floating-point math.  Note that this does not just apply to magnitudes; unit equality with non-integer powers
could have problems as well.
-Investigate giving users some sort of helper to build the macros that expand to quantity-based or raw numeric functions.  I need to determine if this is possible; macros don't have as many options for manipulation as functions do.
-Add more functions.
-Add ability to switch between quantities and unchecked math; the only switching is currently between quantity-based and boxed math.
-Use a compiler flag other than \*assert\*.


## License

Copyright © 2015 William Parker

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
