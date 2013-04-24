
### Monad laws

#### Left identity

LYAHFGG:

> The first monad law states that if we take a value, put it in a default context with `return` and then feed it to a function by using `>>=`, it's the same as just taking the value and applying the function to it. 

To put this in Scala,

```scala
// (Monad[F].point(x) flatMap {f}) assert_=== f(x)

scala> (Monad[Option].point(3) >>= { x => (x + 100000).some }) assert_=== 3 |> { x => (x + 100000).some }
```

#### Right identity

> The second law states that if we have a monadic value and we use `>>=` to feed it to `return`, the result is our original monadic value.

```scala
// (m forMap {Monad[F].point(_)}) assert_=== m

scala> ("move on up".some flatMap {Monad[Option].point(_)}) assert_=== "move on up".some
```

#### Associativity

> The final monad law says that when we have a chain of monadic function applications with `>>=`, it shouldn't matter how they're nested. 

```scala
// (m flatMap f) flatMap g assert_=== m flatMap { x => f(x) flatMap {g} }

scala> Monad[Option].point(Pole(0, 0)) >>= {_.landRight(2)} >>= {_.landLeft(2)} >>= {_.landRight(2)}
res76: Option[Pole] = Some(Pole(2,4))

scala> Monad[Option].point(Pole(0, 0)) >>= { x =>
       x.landRight(2) >>= { y =>
       y.landLeft(2) >>= { z =>
       z.landRight(2)
       }}}
res77: Option[Pole] = Some(Pole(2,4))
```

Scalaz 7 expresses these laws as the following:

```scala
  trait MonadLaw extends ApplicativeLaw {
    /** Lifted `point` is a no-op. */
    def rightIdentity[A](a: F[A])(implicit FA: Equal[F[A]]): Boolean = FA.equal(bind(a)(point(_: A)), a)
    /** Lifted `f` applied to pure `a` is just `f(a)`. */
    def leftIdentity[A, B](a: A, f: A => F[B])(implicit FB: Equal[F[B]]): Boolean = FB.equal(bind(point(a))(f), f(a))
    /**
     * As with semigroups, monadic effects only change when their
     * order is changed, not when the order in which they're
     * combined changes.
     */
    def associativeBind[A, B, C](fa: F[A], f: A => F[B], g: B => F[C])(implicit FC: Equal[F[C]]): Boolean =
      FC.equal(bind(bind(fa)(f))(g), bind(fa)((a: A) => bind(f(a))(g)))
  }
```

Here's how to check if `Option` conforms to the Monad laws. Run `sbt test:console` with `build.sbt` we used in day 4:

```scala
scala> monad.laws[Option].check
+ monad.applicative.functor.identity: OK, passed 100 tests.
+ monad.applicative.functor.associative: OK, passed 100 tests.
+ monad.applicative.identity: OK, passed 100 tests.
+ monad.applicative.composition: OK, passed 100 tests.
+ monad.applicative.homomorphism: OK, passed 100 tests.
+ monad.applicative.interchange: OK, passed 100 tests.
+ monad.right identity: OK, passed 100 tests.
+ monad.left identity: OK, passed 100 tests.
+ monad.associativity: OK, passed 100 tests.
```

Looking good, `Option`. We'll pick it up from here.
