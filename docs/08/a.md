
### Some useful monadic functions

[Learn You a Haskell for Great Good](http://learnyouahaskell.com/for-a-few-monads-more) says:

> In this section, we're going to explore a few functions that either operate on monadic values or return monadic values as their results (or both!). Such functions are usually referred to as *monadic functions*.

In Scalaz `Monad` extends `Applicative`, so there's no question that all monads are functors. This means we can use `map` or `<*>` operator.

#### join method

LYAHFGG:

> It turns out that any nested monadic value can be flattened and that this is actually a property unique to monads. For this, the `join` function exists.

In Scalaz `join` (and its symbolic alias `μ`) is a method introduced by `Bind`:

```scala
trait BindOps[F[_],A] extends Ops[F[A]] {
  ...
  def join[B](implicit ev: A <~< F[B]): F[B] = F.bind(self)(ev(_))
  def μ[B](implicit ev: A <~< F[B]): F[B] = F.bind(self)(ev(_))
  ...
}
```

Let's try it out:

```scala
scala> (Some(9.some): Option[Option[Int]]).join
res9: Option[Int] = Some(9)

scala> (Some(none): Option[Option[Int]]).join
res10: Option[Int] = None

scala> List(List(1, 2, 3), List(4, 5, 6)).join
res12: List[Int] = List(1, 2, 3, 4, 5, 6)

scala> 9.right[String].right[String].join
res15: scalaz.Unapply[scalaz.Bind,scalaz.\/[String,scalaz.\/[String,Int]]]{type M[X] = scalaz.\/[String,X]; type A = scalaz.\/[String,Int]}#M[Int] = \/-(9)

scala> "boom".left[Int].right[String].join
res16: scalaz.Unapply[scalaz.Bind,scalaz.\/[String,scalaz.\/[String,Int]]]{type M[X] = scalaz.\/[String,X]; type A = scalaz.\/[String,Int]}#M[Int] = -\/(boom)
```

#### filterM method

LYAHFGG:

> The `filterM` function from `Control.Monad` does just what we want!
> ...
> The predicate returns a monadic value whose result is a `Bool`.

In Scalaz `filterM` is implemented in several places.

```scala
trait ListOps[A] extends Ops[List[A]] {
  ...
  final def filterM[M[_] : Monad](p: A => M[Boolean]): M[List[A]] = l.filterM(self)(p)
  ...
}
```

```scala
scala> List(1, 2, 3) filterM { x => List(true, false) }
res19: List[List[Int]] = List(List(1, 2, 3), List(1, 2), List(1, 3), List(1), List(2, 3), List(2), List(3), List())

scala> Vector(1, 2, 3) filterM { x => Vector(true, false) }
res20: scala.collection.immutable.Vector[Vector[Int]] = Vector(Vector(1, 2, 3), Vector(1, 2), Vector(1, 3), Vector(1), Vector(2, 3), Vector(2), Vector(3), Vector())
```

#### foldLeftM method

LYAHFGG:

> The monadic counterpart to `foldl` is `foldM`.

In Scalaz, this is implemented in `Foldable` as `foldLeftM`. There's also `foldRightM` too.

```scala
scala> def binSmalls(acc: Int, x: Int): Option[Int] = {
         if (x > 9) (none: Option[Int])
         else (acc + x).some
       }
binSmalls: (acc: Int, x: Int)Option[Int]

scala> List(2, 8, 3, 1).foldLeftM(0) {binSmalls}
res25: Option[Int] = Some(14)

scala> List(2, 11, 3, 1).foldLeftM(0) {binSmalls}
res26: Option[Int] = None
```
