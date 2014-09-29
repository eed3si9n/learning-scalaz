
### Composing monadic functions

LYAHFGG:

> When we were learning about the monad laws, we said that the `<=<` function is just like composition, only instead of working for normal functions like `a -> b`, it works for monadic functions like `a -> m b`.

Looks like I missed this one too.

### Kleisli

In Scalaz there's a special wrapper for function of type `A => M[B]` called [Kleisli]($scalazBaseUrl$/core/src/main/scala/scalaz/Kleisli.scala):

```scala
sealed trait Kleisli[M[+_], -A, +B] { self =>
  def run(a: A): M[B]
  ...
  /** alias for `andThen` */
  def >=>[C](k: Kleisli[M, B, C])(implicit b: Bind[M]): Kleisli[M, A, C] =  kleisli((a: A) => b.bind(this(a))(k(_)))
  def andThen[C](k: Kleisli[M, B, C])(implicit b: Bind[M]): Kleisli[M, A, C] = this >=> k
  /** alias for `compose` */
  def <=<[C](k: Kleisli[M, C, A])(implicit b: Bind[M]): Kleisli[M, C, B] = k >=> this
  def compose[C](k: Kleisli[M, C, A])(implicit b: Bind[M]): Kleisli[M, C, B] = k >=> this
  ...
}

object Kleisli extends KleisliFunctions with KleisliInstances {
  def apply[M[+_], A, B](f: A => M[B]): Kleisli[M, A, B] = kleisli(f)
}
```

We can use `Kleisli` object to construct it:

```scala
scala> val f = Kleisli { (x: Int) => (x + 1).some }
f: scalaz.Kleisli[Option,Int,Int] = scalaz.KleisliFunctions\$\$anon\$18@7da2734e

scala> val g = Kleisli { (x: Int) => (x * 100).some }
g: scalaz.Kleisli[Option,Int,Int] = scalaz.KleisliFunctions\$\$anon\$18@49e07991
```

We can then compose the functions using `<=<`, which runs rhs first like `f compose g`:

```scala
scala> 4.some >>= (f <=< g)
res59: Option[Int] = Some(401)
```

There's also `>=>`, which runs lhs first like `f andThen g`:

```scala
scala> 4.some >>= (f >=> g)
res60: Option[Int] = Some(500)
```

### Reader again

As a bonus, Scalaz defines `Reader` as a special case of `Kleisli` as follows:

```scala
  type ReaderT[F[+_], E, A] = Kleisli[F, E, A]
  type Reader[E, A] = ReaderT[Id, E, A]
  object Reader {
    def apply[E, A](f: E => A): Reader[E, A] = Kleisli[Id, E, A](f)
  }
```

We can rewrite the reader example from day 6 as follows:

```scala
scala> val addStuff: Reader[Int, Int] = for {
         a <- Reader { (_: Int) * 2 }
         b <- Reader { (_: Int) + 10 }
       } yield a + b
addStuff: scalaz.Reader[Int,Int] = scalaz.KleisliFunctions\$\$anon\$18@343bd3ae

scala> addStuff(3)
res76: scalaz.Id.Id[Int] = 19
```

The fact that we are using function as a monad becomes somewhat clearer here.
