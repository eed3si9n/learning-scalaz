
### sum function

Nick demonstrates an example of ad-hoc polymorphism by gradually making `sum` function more general, starting from a simple function that adds up a list of `Int`s:

```scala
scala> def sum(xs: List[Int]): Int = xs.foldLeft(0) { _ + _ }
sum: (xs: List[Int])Int

scala> sum(List(1, 2, 3, 4))
res3: Int = 10
```

### Monoid

> If we try to generalize a little bit. I'm going to pull out a thing called `Monoid`. ... It's a type for which there exists a function `mappend`, which produces another type in the same set; and also a function that produces a zero.

```scala
scala> object IntMonoid {
         def mappend(a: Int, b: Int): Int = a + b
         def mzero: Int = 0
       }
defined module IntMonoid
```

> If we pull that in, it sort of generalizes what's going on here:

```scala
scala> def sum(xs: List[Int]): Int = xs.foldLeft(IntMonoid.mzero)(IntMonoid.mappend)
sum: (xs: List[Int])Int

scala> sum(List(1, 2, 3, 4))
res5: Int = 10
```

> Now we'll abstract on the type about `Monoid`, so we can define `Monoid` for any type `A`. So now `IntMonoid` is a monoid on `Int`:

```scala
scala> trait Monoid[A] {
         def mappend(a1: A, a2: A): A
         def mzero: A
       }
defined trait Monoid

scala> object IntMonoid extends Monoid[Int] {
         def mappend(a: Int, b: Int): Int = a + b
         def mzero: Int = 0
       }
defined module IntMonoid
```

What we can do is that `sum` take a `List` of `Int` and a monoid on `Int` to sum it:

```scala
scala> def sum(xs: List[Int], m: Monoid[Int]): Int = xs.foldLeft(m.mzero)(m.mappend)
sum: (xs: List[Int], m: Monoid[Int])Int

scala> sum(List(1, 2, 3, 4), IntMonoid)
res7: Int = 10
```

> We are not using anything to do with `Int` here, so we can replace all `Int` with a general type:

```scala
scala> def sum[A](xs: List[A], m: Monoid[A]): A = xs.foldLeft(m.mzero)(m.mappend)
sum: [A](xs: List[A], m: Monoid[A])A

scala> sum(List(1, 2, 3, 4), IntMonoid)
res8: Int = 10
```

> The final change we have to take is to make the `Monoid` implicit so we don't have to specify it each time.

```scala
scala> def sum[A](xs: List[A])(implicit m: Monoid[A]): A = xs.foldLeft(m.mzero)(m.mappend)
sum: [A](xs: List[A])(implicit m: Monoid[A])A

scala> implicit val intMonoid = IntMonoid
intMonoid: IntMonoid.type = IntMonoid\$@3387dfac

scala> sum(List(1, 2, 3, 4))
res9: Int = 10
```

Nick didn't do this, but the implicit parameter is often written as a context bound:

```scala
scala> def sum[A: Monoid](xs: List[A]): A = {
         val m = implicitly[Monoid[A]]
         xs.foldLeft(m.mzero)(m.mappend)
       }
sum: [A](xs: List[A])(implicit evidence\$1: Monoid[A])A

scala> sum(List(1, 2, 3, 4))
res10: Int = 10
```

> Our `sum` function is pretty general now, appending any monoid in a list. We can test that by writing another `Monoid` for `String`. I'm also going to package these up in an object called `Monoid`. The reason for that is Scala's implicit resolution rules: When it needs an implicit parameter of some type, it'll look for anything in scope. It'll include the companion object of the type that you're looking for.

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

trait Monoid[A] {
  def mappend(a1: A, a2: A): A
  def mzero: A
}
object Monoid {
  implicit val IntMonoid: Monoid[Int] = new Monoid[Int] {
    def mappend(a: Int, b: Int): Int = a + b
    def mzero: Int = 0
  }
  implicit val StringMonoid: Monoid[String] = new Monoid[String] {
    def mappend(a: String, b: String): String = a + b
    def mzero: String = ""
  }
}
def sum[A: Monoid](xs: List[A]): A = {
  val m = implicitly[Monoid[A]]
  xs.foldLeft(m.mzero)(m.mappend)
}

// Exiting paste mode, now interpreting.

defined trait Monoid
defined module Monoid
sum: [A](xs: List[A])(implicit evidence\$1: Monoid[A])A

scala> sum(List("a", "b", "c"))
res12: String = abc
```

> You can still provide different monoid directly to the function. We could provide an instance of monoid for `Int` using multiplications.

```scala
scala> val multiMonoid: Monoid[Int] = new Monoid[Int] {
         def mappend(a: Int, b: Int): Int = a * b
         def mzero: Int = 1
       }
multiMonoid: Monoid[Int] = \$anon\$1@48655fb6

scala> sum(List(1, 2, 3, 4))(multiMonoid)
res14: Int = 24
```
