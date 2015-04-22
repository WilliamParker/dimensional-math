## dimensional-math

A Clojure library for math with units.

## API Docs

http://williamparker.github.io/units/

## Continuous integration

https://travis-ci.org/WilliamParker/units

![Travis CI status](https://travis-ci.org/WilliamParker/units.svg?branch=master)

## Motivation

There are already some Clojure libraries for math with units, such as Meajure at https://github.com/arrdem/meajure.  However, I wanted something that would solely serve as extra validation during testing and have no impact outside of testing.  This library has the benefit of not creating any new types; all quantities are also their original numeric classes and can be seamlessly used with all functions that take the relevant numeric type.  Unit validation can also easily be turned off at compile time.  I believe the effect is to provide some added safety without imposing undue burdens on the programmer to manage more types and without imposing any performance costs after compilation when unit checking is turned off.  In other words, the objective of this library is to provide more lightweight unit validation of numeric operations.  Note that
only the boxed numeric types are supported.

## Key features

1. There are no actual changes to the numeric types.  Behind the scenes, the library maintains a mapping from numeric object instances to a mapping from units to powers. This mapping uses weak references for the keys to avoid introducing a memory leak. The mapping is based on object identities, not object equality.  In effect, the library "cheats" and adds a new field to the final Java numeric classes.  The quantity builder functions create a new instance of the number provided and associate it with the given units.  Note that constructors are used instead of .valueOf methods to ensure that a new instance is created, and the quantity builder always verifies that a new object instance is created in the quantity builder. The benefit of this is that all Clojure and Java code for processing numbers can be used without any additional wrapping.  The units verification is purely an additional verification step.  The intention is to avoid dependence on any behavior of quantity-aware code except in testing.  Code with quantities can seamlessly interface with non-quantity-aware code.  Note, however, that after a quantity is passed to non-quantity aware code no guarantees are made regarding any possible units on the output.

2. The library includes macros that expand to quantity-aware functions if the \*compile-with-unit-checks\* var is bound to true and to ordinary function calls otherwise.  Thus, quantity checking can be turned on and off at compile time.

3. The library includes utility functions for generating quantity-aware functions.  The resulting functions take the same number of arguments as the original functions.  One of these function builders generates a function that expects quantities with equal units; the other expects quantities with differents units and accepts an argument of a function for merging units.  The library also includes functionality to generate macros that expand to an arbitrary function call if \*compile-with-unit-checks\* is true and to an arbitrary alternative function call otherwise.

4. Users can easily extend the library to support any numeric type by extending the protocol CloneableNumber onto that type.  The only requirement is that it be possible to create a new instance of the numeric type (and to guarantee that a new instance will be created).


To be clear, this is highly experimental.  API changes are likely, and major ones are possible.  There is reasonably good test coverage, but realistically there are probably edge cases that I haven't tested.

## Usage
Define quantities

```
(def length (->quantity 3 {:meters 1}))
```

```
(def width (->quantity 4 {:meters 1}))
```

Then operate on the quantities

````
(def area (quantities-multiply length width))
area
=> 12

(quantity->units area)
=> {:meters 2}
````

Note that area is bound to a native numeric type, not a type defined in this library.

```
(type area)
=> java.lang.Long
```

The library stores information on units for each numeric instance, and creates a new instance every time a new quantity is created.  Note that the library assumes that all numeric types are immutable.
This information is stored in a reference-based (not equality-based) hash map.  This is necessary since the Java numeric classes are final; the effect of this approach is to add information and functions to these classes
without the need to inherit from them.  Also note that the map uses weak references to the numbers to avoid memory leaks.


Quantities that are compared should have equal units.

````
(quantities-equal? (->quantity 1 {:ft 1}) (->quantity 1 {:m 1}))
=>
ExceptionInfo Two quantities that are compared should have equal units.
````

To extend quantity checking to a new numeric type, implement the CloneableNumber protocol on it.  The protocol consists of one function, clone-number, that takes an instance of a number and returns a different but equal instance of the same numeric type.  It is recommended to use the ->checked-copy-fn wrapper, which wraps the copy function and throws an exception if it breaks the library's contract of creating a new object instance.  For example, if the CloneableNumber was not extended onto the Integer class, we could do so with:

```
(extend-protocol CloneableNumber
  java.lang.Integer
  (clone-number [x] ((->checked-copy-fn #(java.lang.Integer. ^java.lang.Integer %)) x)))
```

->checked-copy-fn takes a single argument - a 1-argument function that takes a numeric argument and copies it.  The resulting function is also a 1-argument function that copies the number, but with verification that the result is a new object instance.

The library also allows the easy creation of functions that check or propogate units from preexisting mathematical functions.  The new functions will take the same number of arguments as the underlying mathematical functions. The key library functions are ->quantity-equal-fn, which wraps a function and throws an exception if the quantities of the arguments are not equal, ->quantity-operation-fn, which takes a function and a two-arity function for merging units, and ->quantity-comparison-fn, which generates a function that takes at least one quantity and returns a boolean.  The units merging function receives the magnitude of the unit on the first quantity as its first argument and the magnitude of the same unit on the second quantity as its second argument; it returns the magnitude of that unit on the result.  If the unit in question does not exist on one of the arguments that value passed is 0.  For example, addition, multiplication, and the less-than comparison are defined with:

```
(def ^{:doc "Function that adds quantities.  It must be provided at least 1 argument.
       Unlike clojure.core/+, this will throw an exception if no arguments are provided."}
  quantities-add* (->quantities-equal-fn + "addition"))
```

```
(def ^{:doc "Function that multiplies an arbitrary number of quantities."}
  quantities-multiply* (->quantity-operation-fn * + "multiplication"))
```

```
(def ^{:doc "Tests if an arbitrary number of quantities are less than each other."}
  quantities-less-than?* (->quantity-comparison-fn < "less than"))
```

Note the three arguments to quantities-multiply\*. The first argument is the actual mathematical function.  The second argument states that the magnitudes of the units of the quantities should be subtracted. For example, when a quantity with units {:meters 2} is divided by a quantity with units {:meters 1}, we get a quantity with units {:meters 1}.  The third argument is a string
describing the operation that can be used in any exception messages thrown by
the resulting function.

We can now use def-quantities-macro to define a macro that expands to a call to a quantity-checking function if \*compile-with-unit-checks\* is true and to an ordinary function call otherwise.  For example, for addition:

```
(def-quantities-macro quantities-add "Macro that expands to add quantities if the global flag
*assert* is true and expands to simple addition otherwise."
 wparker.dimensional-math.core/quantities-add* clojure.core/+)
```
  This var can be set for all compilation by globally setting it with a Leiningen injection.

```
:injections [(require 'wparker.dimensional-math.core)
                                (intern 'wparker.dimensional-math.core '*compile-with-unit-checks* true)]
```

  It is possible that unit checking might be desirable in most cases in a project, but not in all.  The unit-checking macros will typically expand to either unit-checking functions or the base math functions in the entire project.  However, the library provides a fixture (without-unit-checks) to execute a zero-arity function with unit checking disabled at runtime, even if it was enabled at compile time.

 ```
wparker.dimensional-math.core=> (without-unit-checks (fn [] (quantities-add* (->quantity* 7 {:ft 1}) (->quantity* 3 {:meters 1}))))
=>10
```

When unit powers are floating-point numbers errors may be introduced in unit propagation as in any floating-point calculation.
When the var \*unit-power-error\* is bound to a number (instead of the default value of nil) the given amount of difference between unit powers will be tolerated in unit checking.

## Performance notes

It is probably obvious that turning on quantity checking will have performance consequences.  Where large numerical calculations are used this may be significant.  Note, however, that this slowdown may not be linear relative to the unchecked speed.  The quantities are stored in a synchronized mutable map.  Thus, the use of quantity checking may limit the effectiveness of parallelization.  Secondly, the quantity builder functions coerce primitive types into boxed values.

## Known defects/limitations/todos:

First note that the library does not support adding units to Java numeric primitives.  In general, this library does use the numeric classes in somewhat strange ways, but I have (hopefully) avoided any reliance on non-guaranteed behavior in the Java numerical classes.  In the case of the Clojure numerical classes, there is no formal specification that I know of, but the library uses obvious method calls, not hacks.  The quantity generator functions verify that a new object instance is created, so a change that altered this behavior would immediately become obvious.

I'm open to naming suggestions, at least unless and until this is used enough to make breaking changes problematic.  Even then something like https://github.com/ztellman/potemkin#import-vars could be used.  Using a variant on my name in the namespace names is less than descriptive or creative, but it does lessen the likelihood of namespace name clashes.

## License

Copyright © 2015 William Parker

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
