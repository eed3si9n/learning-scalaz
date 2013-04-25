
### Id

Using [Hoogle](http://www.haskell.org/hoogle/?hoogle=Identity) we can look up Haskell typeclasses. For example, let's look at [`Control.Monad.Identity`](http://hackage.haskell.org/packages/archive/mtl/latest/doc/html/Control-Monad-Identity.html):

> The `Identity` monad is a monad that does not embody any computational strategy. It simply applies the bound function to its input without any modification. Computationally, there is no reason to use the `Identity` monad instead of the much simpler act of simply applying functions to their arguments. The purpose of the `Identity` monad is its fundamental role in the theory of monad transformers. Any monad transformer applied to the `Identity` monad yields a non-transformer version of that monad.

Here's the corresponding type in Scalaz:

```scala
  /** The strict identity type constructor. Can be thought of as `Tuple1`, but with no
   *  runtime representation.
   */
  type Id[+X] = X
```

We need to look at monad transformer later, but one thing that's interesting is that all data types can be `Id` of the type.

```scala
scala> (0: Id[Int])
res39: scalaz.Scalaz.Id[Int] = 0
```

Scalaz introduces several useful methods via `Id`:

```scala
trait IdOps[A] extends Ops[A] {
  /**Returns `self` if it is non-null, otherwise returns `d`. */
  final def ??(d: => A)(implicit ev: Null <:< A): A =
    if (self == null) d else self
  /**Applies `self` to the provided function */
  final def |>[B](f: A => B): B = f(self)
  final def squared: (A, A) = (self, self)
  def left[B]: (A \/ B) = \/.left(self)
  def right[B]: (B \/ A) = \/.right(self)
  final def wrapNel: NonEmptyList[A] = NonEmptyList(self)
  /** @return the result of pf(value) if defined, otherwise the the Zero element of type B. */
  def matchOrZero[B: Monoid](pf: PartialFunction[A, B]): B = ...
  /** Repeatedly apply `f`, seeded with `self`, checking after each iteration whether the predicate `p` holds. */
  final def doWhile(f: A => A, p: A => Boolean): A = ...
  /** Repeatedly apply `f`, seeded with `self`, checking before each iteration whether the predicate `p` holds. */
  final def whileDo(f: A => A, p: A => Boolean): A = ...
  /** If the provided partial function is defined for `self` run this,
   * otherwise lift `self` into `F` with the provided [[scalaz.Pointed]]. */
  def visit[F[_] : Pointed](p: PartialFunction[A, F[A]]): F[A] = ...
}
```

`|>` lets you write the function application at the end of an expression:

```scala
scala> 1 + 2 + 3 |> {_.point[List]}
res45: List[Int] = List(6)

scala> 1 + 2 + 3 |> {_ * 6}
res46: Int = 36
```

`visit` is also kind of interesting:

```scala
scala> 1 visit { case x@(2|3) => List(x * 2) }
res55: List[Int] = List(1)

scala> 2 visit { case x@(2|3) => List(x * 2) }
res56: List[Int] = List(4)
```
