
### List Monad

LYAHFGG:

> On the other hand, a value like `[3,8,9]` contains several results, so we can view it as one value that is actually many values at the same time. Using lists as applicative functors showcases this non-determinism nicely.

Let's look at using `List` as Applicatives again (This notation might require Scalaz 7.0.0-M3):

```scala
scala> ^(List(1, 2, 3), List(10, 100, 100)) {_ * _}
res29: List[Int] = List(10, 100, 100, 20, 200, 200, 30, 300, 300)
```

> let's try feeding a non-deterministic value to a function:

```scala
scala> List(3, 4, 5) >>= {x => List(x, -x)}
res30: List[Int] = List(3, -3, 4, -4, 5, -5)
```

So in this monadic view, `List` context represent mathematical value that could have multiple solutions. Other than that manipulating `List`s using `for` notation is just like plain Scala:

```scala
scala> for {
         n <- List(1, 2)
         ch <- List('a', 'b')
       } yield (n, ch)
res33: List[(Int, Char)] = List((1,a), (1,b), (2,a), (2,b))
```

### MonadPlus and the guard function

Scala's `for` notation allows filtering:

```scala
scala> for {
         x <- 1 |-> 50 if x.shows contains '7'
       } yield x
res40: List[Int] = List(7, 17, 27, 37, 47)
```

LYAHFGG:

> The `MonadPlus` type class is for monads that can also act as monoids.

Here's [the typeclass contract for `MonadPlus`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/MonadPlus.scala):

```scala
trait MonadPlus[F[_]] extends Monad[F] with ApplicativePlus[F] { self =>
  ...
}
```

### Plus, PlusEmpty, and ApplicativePlus

It extends [`ApplicativePlus`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/ApplicativePlus.scala):

```scala
trait ApplicativePlus[F[_]] extends Applicative[F] with PlusEmpty[F] { self =>
  ...
}
```

And that extends [`PlusEmpty`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/PlusEmpty.scala):

```scala
trait PlusEmpty[F[_]] extends Plus[F] { self =>
  ////
  def empty[A]: F[A]
}
```

And that extends [`Plus`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/PlusEmpty.scala):

```scala
trait Plus[F[_]]  { self =>
  def plus[A](a: F[A], b: => F[A]): F[A]
}
```

Similar to `Semigroup[A]` and `Monoid[A]`, `Plus[F[_]]` and `PlusEmpty[F[_]]` requires theier instances to implement `plus` and `empty`, but at the type constructor ( `F[_]`) level. 

`Plus` introduces `<+>` operator to append two containers:

```scala
scala> List(1, 2, 3) <+> List(4, 5, 6)
res43: List[Int] = List(1, 2, 3, 4, 5, 6)
```

### MonadPlus again

`MonadPlus` introduces `filter` operation.

```scala
scala> (1 |-> 50) filter { x => x.shows contains '7' }
res46: List[Int] = List(7, 17, 27, 37, 47)
```
