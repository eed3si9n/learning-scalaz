---
out: Making-monads.html
---

### モナドを作る

LYAHFGG:

> この節では、型が生まれてモナドであると確認され、適切な `Monad` インスタンスが与えられるまでの過程を、例題を通して学ぼうと思います。
> ...
> `[3,5,9]` のような非決定的値を表現したいのだけど、さらに `3` である確率は 50パーセント、`5` と `9` である確率はそれぞれ 25パーセントである、ということを表したくなったらどうしましょう？

Scala に有理数が標準で入っていないので、`Double` を使う。以下が case class:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

case class Prob[A](list: List[(A, Double)])

trait ProbInstances {
  implicit def probShow[A]: Show[Prob[A]] = Show.showA
}

case object Prob extends ProbInstances

// Exiting paste mode, now interpreting.

defined class Prob
defined trait ProbInstances
defined module Prob
```

> これってファンクターでしょうか？ええ、リストはファンクターですから、リストに何かを足したものである `Prob` もたぶんファンクターでしょう。

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

case class Prob[A](list: List[(A, Double)])

trait ProbInstances {
  implicit val probInstance = new Functor[Prob] {
    def map[A, B](fa: Prob[A])(f: A => B): Prob[B] =
      Prob(fa.list map { case (x, p) => (f(x), p) })
  }
  implicit def probShow[A]: Show[Prob[A]] = Show.showA
}

case object Prob extends ProbInstances

scala> Prob((3, 0.5) :: (5, 0.25) :: (9, 0.25) :: Nil) map {-_} 
res77: Prob[Int] = Prob(List((-3,0.5), (-5,0.25), (-9,0.25)))
```

本と同様に `flatten` をまず実装する。

```scala
case class Prob[A](list: List[(A, Double)])

trait ProbInstances {
  def flatten[B](xs: Prob[Prob[B]]): Prob[B] = {
    def multall(innerxs: Prob[B], p: Double) =
      innerxs.list map { case (x, r) => (x, p * r) }
    Prob((xs.list map { case (innerxs, p) => multall(innerxs, p) }).flatten)
  }

  implicit val probInstance = new Functor[Prob] {
    def map[A, B](fa: Prob[A])(f: A => B): Prob[B] =
      Prob(fa.list map { case (x, p) => (f(x), p) })
  }
  implicit def probShow[A]: Show[Prob[A]] = Show.showA
}

case object Prob extends ProbInstances
```

これでモナドのための準備は整ったはずだ:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

case class Prob[A](list: List[(A, Double)])

trait ProbInstances {
  def flatten[B](xs: Prob[Prob[B]]): Prob[B] = {
    def multall(innerxs: Prob[B], p: Double) =
      innerxs.list map { case (x, r) => (x, p * r) }
    Prob((xs.list map { case (innerxs, p) => multall(innerxs, p) }).flatten)
  }

  implicit val probInstance = new Functor[Prob] with Monad[Prob] {
    def point[A](a: => A): Prob[A] = Prob((a, 1.0) :: Nil)
    def bind[A, B](fa: Prob[A])(f: A => Prob[B]): Prob[B] = flatten(map(fa)(f)) 
    override def map[A, B](fa: Prob[A])(f: A => B): Prob[B] =
      Prob(fa.list map { case (x, p) => (f(x), p) })
  }
  implicit def probShow[A]: Show[Prob[A]] = Show.showA
}

case object Prob extends ProbInstances

// Exiting paste mode, now interpreting.

defined class Prob
defined trait ProbInstances
defined module Prob
```

本によるとモナド則は満たしているらしい。`Coin` の例題も実装してみよう:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait Coin
case object Heads extends Coin
case object Tails extends Coin
implicit val coinEqual: Equal[Coin] = Equal.equalA

def coin: Prob[Coin] = Prob(Heads -> 0.5 :: Tails -> 0.5 :: Nil)
def loadedCoin: Prob[Coin] = Prob(Heads -> 0.1 :: Tails -> 0.9 :: Nil)

def flipThree: Prob[Boolean] = for {
  a <- coin
  b <- coin
  c <- loadedCoin
} yield { List(a, b, c) all {_ === Tails} }

// Exiting paste mode, now interpreting.

defined trait Coin
defined module Heads
defined module Tails
coin: Prob[Coin]
loadedCoin: Prob[Coin]
flipThree: Prob[Boolean]

scala> flipThree
res81: Prob[Boolean] = Prob(List((false,0.025), (false,0.225), (false,0.025), (false,0.225), (false,0.025), (false,0.225), (false,0.025), (true,0.225)))
```

イカサマのコインを 1つ使っても 3回とも裏が出る確率はかなり低いことが分かった。

続きはまた後で。
