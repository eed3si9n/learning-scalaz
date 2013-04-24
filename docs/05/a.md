---
out: Monad.html
---

### A fist full of Monads

We get to start a new chapter today on [Learn You a Haskell for Great Good](http://learnyouahaskell.com/a-fistful-of-monads).

> Monads are a natural extension applicative functors, and they provide a solution to the following problem: If we have a value with context, `m a`, how do we apply it to a function that takes a normal `a` and returns a value with a context.

The equivalent is called `Monad` in Scalaz. Here's [the typeclass contract](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Monad.scala):

```scala
trait Monad[F[_]] extends Applicative[F] with Bind[F] { self =>
  ////
}
```

It extends `Applicative` and `Bind`. So let's look at `Bind`.

### Bind

Here's [`Bind`'s contract](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Bind.scala):

```scala
trait Bind[F[_]] extends Apply[F] { self =>
  /** Equivalent to `join(map(fa)(f))`. */
  def bind[A, B](fa: F[A])(f: A => F[B]): F[B]
}
```

And here are [the operators](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/BindSyntax.scala):

```scala
/** Wraps a value `self` and provides methods related to `Bind` */
trait BindOps[F[_],A] extends Ops[F[A]] {
  implicit def F: Bind[F]
  ////
  import Liskov.<~<

  def flatMap[B](f: A => F[B]) = F.bind(self)(f)
  def >>=[B](f: A => F[B]) = F.bind(self)(f)
  def ∗[B](f: A => F[B]) = F.bind(self)(f)
  def join[B](implicit ev: A <~< F[B]): F[B] = F.bind(self)(ev(_))
  def μ[B](implicit ev: A <~< F[B]): F[B] = F.bind(self)(ev(_))
  def >>[B](b: F[B]): F[B] = F.bind(self)(_ => b)
  def ifM[B](ifTrue: => F[B], ifFalse: => F[B])(implicit ev: A <~< Boolean): F[B] = {
    val value: F[Boolean] = Liskov.co[F, A, Boolean](ev)(self)
    F.ifM(value, ifTrue, ifFalse)
  }
  ////
}
```

It introduces `flatMap` operator and its symbolic aliases `>>=` and `∗`. We'll worry about the other operators later. We are use to `flapMap` from the standard library:

```scala
scala> 3.some flatMap { x => (x + 1).some }
res2: Option[Int] = Some(4)

scala> (none: Option[Int]) flatMap { x => (x + 1).some }
res3: Option[Int] = None
```

### Monad

Back to `Monad`:

```scala
trait Monad[F[_]] extends Applicative[F] with Bind[F] { self =>
  ////
}
```

Unlike Haskell, `Monad[F[_]]` exntends `Applicative[F[_]]` so there's no `return` vs `pure` issues. They both use `point`.

```scala
scala> Monad[Option].point("WHAT")
res5: Option[String] = Some(WHAT)

scala> 9.some flatMap { x => Monad[Option].point(x * 10) }
res6: Option[Int] = Some(90)

scala> (none: Option[Int]) flatMap { x => Monad[Option].point(x * 10) }
res7: Option[Int] = None
```
