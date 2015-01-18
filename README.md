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

FIXME

## License

Copyright © 2015 William Parker

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
