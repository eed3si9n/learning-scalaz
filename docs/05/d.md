---
out: MonadPlus.html
---

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

Here's [the typeclass contract for `MonadPlus`]($scalazBaseUrl$/core/src/main/scala/scalaz/MonadPlus.scala):

```scala
trait MonadPlus[F[_]] extends Monad[F] with ApplicativePlus[F] { self =>
  ...
}
```

### Plus, PlusEmpty, and ApplicativePlus

It extends [`ApplicativePlus`]($scalazBaseUrl$/core/src/main/scala/scalaz/ApplicativePlus.scala):

```scala
trait ApplicativePlus[F[_]] extends Applicative[F] with PlusEmpty[F] { self =>
  ...
}
```

And that extends [`PlusEmpty`]($scalazBaseUrl$/core/src/main/scala/scalaz/PlusEmpty.scala):

```scala
trait PlusEmpty[F[_]] extends Plus[F] { self =>
  ////
  def empty[A]: F[A]
}
```

And that extends [`Plus`]($scalazBaseUrl$/core/src/main/scala/scalaz/PlusEmpty.scala):

```scala
trait Plus[F[_]]  { self =>
  def plus[A](a: F[A], b: => F[A]): F[A]
}
```

Similar to `Semigroup[A]` and `Monoid[A]`, `Plus[F[_]]` and `PlusEmpty[F[_]]` requires their instances to implement `plus` and `empty`, but at the type constructor ( `F[_]`) level. 

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
