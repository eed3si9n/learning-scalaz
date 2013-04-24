---
out: Monad-laws.html
---

### Monad則

#### 左単位元

LYAHFGG:

> 第一のモナド則が言っているのは、`return` を使って値をデフォルトの文脈に入れたものを `>>=` を使って関数に食わせた結果は、単にその値にその関数を適用した結果と等しくなりなさい、ということです。

これを Scala で表現すると、

```scala
// (Monad[F].point(x) flatMap {f}) assert_=== f(x)

scala> (Monad[Option].point(3) >>= { x => (x + 100000).some }) assert_=== 3 |> { x => (x + 100000).some }
```

#### 右単位元

> モナドの第二法則は、`>>=` を使ってモナド値を `return` に食わせた結果は、元のモナド値と不変であると言っています。

```scala
// (m forMap {Monad[F].point(_)}) assert_=== m

scala> ("move on up".some flatMap {Monad[Option].point(_)}) assert_=== "move on up".some
```

#### 結合律

> 最後のモナド則は、`>>=` を使ったモナド関数適用の連鎖があるときに、どの順序で評価しても結果は同じであるべき、というものです。

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

Scalaz 7 はモナド則を以下のように表現する:

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

以下が `Option` がモナド則に従うかを検証する方法だ。 4日目の `build.sbt` を用いて `sbt test:console` を実行する:

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

`Option` よくできました。続きはここから。
