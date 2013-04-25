
### Func

I wanted to continue exploring a better way to compose applicative functions, and came up with a wrapper called `AppFunc`:

```scala
val f = AppFuncU { (x: Int) => x + 1 }
val g = AppFuncU { (x: Int) => List(x, 5) }
(f @&&& g) traverse List(1, 2, 3)
```

After sending this in as [a pull request](https://github.com/scalaz/scalaz/pull/161) Lars Hupel ([@larsr_h](https://twitter.com/larsr_h)) suggested that I generalize the concept using typelevel module, so I expanded it to `Func`:

```scala
/**
 * Represents a function `A => F[B]` where `[F: TC]`.
 */
trait Func[F[_], TC[F[_]] <: Functor[F], A, B] {
  def runA(a: A): F[B]
  implicit def TC: KTypeClass[TC]
  implicit def F: TC[F]
  ...
}
```

Using this, `AppFunc` becomes `Func` with `Applicative` in the second type parameter. Lars still wants to expand it composition into general `HList`, but I am optimistic that this will be part of Scalaz 7 eventually.
