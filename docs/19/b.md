
### Point

CM:

> One very useful sort of set is a 'singleton' set, a set with exactly one element. Fix one of these say `{me}`, and call this set '*1*'.

> **Definition**: A *point* of a set X is an arrows *1 => X*.
> ...
> (If *A* is some familiar set, an arrow from *A* to *X* is called an '*A*-element' of *X*; thus '*1*-elements' are points.) Since a point is an arrow, we can compose it with another arrow, and get a point again.

If I understand what's going on, it seems like CM is redefining the concept of the element as a special case of arrow. Another name for singleton is unit set, and in Scala it is `(): Unit`. So it's analogous to saying that values are sugar for `Unit => X`.

```scala
scala> val johnPoint: Unit => Person = { case () => John } 
johnPoint: Unit => Person = <function1>

scala> favoriteBreakfast compose johnPoint
res1: Unit => Breakfast = <function1>

scala> res1(())
res2: Breakfast = Eggs
```

First-class functions in programming languages that support fp treat functions as values, which allows higher-order functions. Category theory unifies on the other direction by treating values as functions.

Session 2 and 3 contain nice review of Article I, so you should read them if you own the book. 

### Equality of arrows of sets

One part in the sessions that I thought was interesting was about the equality of arrows. Many of the discussions in category theory involves around equality of arrows, but how we test if an arrow *f* is equal to *g*?

Two maps are equal when they have the same three ingredients:

- domain *A*
- codomain *B*
- a rule that assigns *f ∘ a*

Because of *1*, we can test for equality of arrows of sets *f: A => B* and *g: A => B* using this test:

> If for each point *a: 1 => A*, *f ∘ a = g ∘ a*, then *f = g*.

This reminds me of scalacheck. Let's try implementing a check for `f: Person => Breakfast`:


```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait Person {}
case object John extends Person {}
case object Mary extends Person {}
case object Sam extends Person {}

sealed trait Breakfast {}
case object Eggs extends Breakfast {}
case object Oatmeal extends Breakfast {}
case object Toast extends Breakfast {}
case object Coffee extends Breakfast {}

val favoriteBreakfast: Person => Breakfast = {
  case John => Eggs
  case Mary => Coffee
  case Sam  => Coffee
}

val favoritePerson: Person => Person = {
  case John => Mary
  case Mary => John
  case Sam  => Mary
}

val favoritePersonsBreakfast = favoriteBreakfast compose favoritePerson

// Exiting paste mode, now interpreting.

scala> import org.scalacheck.{Prop, Arbitrary, Gen}
import org.scalacheck.{Prop, Arbitrary, Gen}

scala> def arrowEqualsProp(f: Person => Breakfast, g: Person => Breakfast)
         (implicit ev1: Equal[Breakfast], ev2: Arbitrary[Person]): Prop =
         Prop.forAll { a: Person =>
           f(a) === g(a)
         } 
arrowEqualsProp: (f: Person => Breakfast, g: Person => Breakfast)
(implicit ev1: scalaz.Equal[Breakfast], implicit ev2: org.scalacheck.Arbitrary[Person])org.scalacheck.Prop

scala> implicit val arbPerson: Arbitrary[Person] = Arbitrary {
         Gen.oneOf(John, Mary, Sam)
       }
arbPerson: org.scalacheck.Arbitrary[Person] = org.scalacheck.Arbitrary\$\$anon\$2@41ec9951

scala> implicit val breakfastEqual: Equal[Breakfast] = Equal.equalA[Breakfast]
breakfastEqual: scalaz.Equal[Breakfast] = scalaz.Equal\$\$anon\$4@783babde

scala> arrowEqualsProp(favoriteBreakfast, favoritePersonsBreakfast)
res0: org.scalacheck.Prop = Prop

scala> res0.check
! Falsified after 1 passed tests.
> ARG_0: John

scala> arrowEqualsProp(favoriteBreakfast, favoriteBreakfast)
res2: org.scalacheck.Prop = Prop

scala> res2.check
+ OK, passed 100 tests.
```

We can generalize `arrowEuqualsProp` a bit:

```scala
scala> def arrowEqualsProp[A, B](f: A => B, g: A => B)
         (implicit ev1: Equal[B], ev2: Arbitrary[A]): Prop =
         Prop.forAll { a: A =>
           f(a) === g(a)
         } 
arrowEqualsProp: [A, B](f: A => B, g: A => B)
(implicit ev1: scalaz.Equal[B], implicit ev2: org.scalacheck.Arbitrary[A])org.scalacheck.Prop

scala> arrowEqualsProp(favoriteBreakfast, favoriteBreakfast)
res4: org.scalacheck.Prop = Prop

scala> res4.check
+ OK, passed 100 tests.
```
