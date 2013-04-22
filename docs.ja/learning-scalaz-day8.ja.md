  [day7]: http://eed3si9n.com/ja/learning-scalaz-day7

[7日目][day7]は、Applicative Builder をみて、あと `State` モナド、`\/` モナド、`Validation` もみた。

### 便利なモナディック関数

[Learn You a Haskell for Great Good](http://learnyouahaskell.com/for-a-few-monads-more) 曰く:

> In this section, we're going to explore a few functions that either operate on monadic values or return monadic values as their results (or both!). Such functions are usually referred to as *monadic functions*.

Scalaz の `Monad` は `Applicative` を継承しているため、全てのモナドが Functor であることが保証される。そのため、`map` や `<*>` 演算子も使える。

#### join メソッド

LYAHFGG:

> It turns out that any nested monadic value can be flattened and that this is actually a property unique to monads. For this, the `join` function exists.

Scalaz では `join` メソッド (およびシンボルを使ったエイリアス `μ`) は `Bind` によって導入される:

<scala>
trait BindOps[F[_],A] extends Ops[F[A]] {
  ...
  def join[B](implicit ev: A <~< F[B]): F[B] = F.bind(self)(ev(_))
  def μ[B](implicit ev: A <~< F[B]): F[B] = F.bind(self)(ev(_))
  ...
}
</scala>

使ってみよう:

<scala>
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
</scala>

#### filterM メソッド

LYAHFGG:

> The `filterM` function from `Control.Monad` does just what we want! 
> ...
> The predicate returns a monadic value whose result is a `Bool`. 

Scalaz では `filterM` はいくつかの箇所で実装されている。`List` に関しては `import Scalaz._` で入ってくるみたいだ。

<scala>
trait ListOps[A] extends Ops[List[A]] {
  ...
  final def filterM[M[_] : Monad](p: A => M[Boolean]): M[List[A]] = l.filterM(self)(p)
  ...
}
</scala>

`Vector` のサポートは少し手伝ってやる必要がある:

<scala>
scala> List(1, 2, 3) filterM { x => List(true, false) }
res19: List[List[Int]] = List(List(1, 2, 3), List(1, 2), List(1, 3), List(1), List(2, 3), List(2), List(3), List())

scala> import syntax.std.vector._
import syntax.std.vector._

scala> Vector(1, 2, 3) filterM { x => Vector(true, false) }
res20: scala.collection.immutable.Vector[Vector[Int]] = Vector(Vector(1, 2, 3), Vector(1, 2), Vector(1, 3), Vector(1), Vector(2, 3), Vector(2), Vector(3), Vector())
</scala>

#### foldLeftM メソッド

LYAHFGG:

> The monadic counterpart to `foldl` is `foldM`.

Scalaz でこれは `Foldable` に `foldLeftM` として実装されていて、`foldRightM` もある。

<scala>
scala> def binSmalls(acc: Int, x: Int): Option[Int] = {
         if (x > 9) (none: Option[Int])
         else (acc + x).some
       }
binSmalls: (acc: Int, x: Int)Option[Int]

scala> List(2, 8, 3, 1).foldLeftM(0) {binSmalls}
res25: Option[Int] = Some(14)

scala> List(2, 11, 3, 1).foldLeftM(0) {binSmalls}
res26: Option[Int] = None
</scala>

### 安全な RPN 電卓を作ろう

LYAHFGG:

> When we were solving the problem of implementing a RPN calculator, we noted that it worked fine as long as the input that it got made sense.

最初に RPN 電卓を作った章は飛ばしたけど、コードはここにあるから Scala に訳してみる:

<scala>
scala> def foldingFunction(list: List[Double], next: String): List[Double] = (list, next) match {
         case (x :: y :: ys, "*") => (y * x) :: ys
         case (x :: y :: ys, "+") => (y + x) :: ys
         case (x :: y :: ys, "-") => (y - x) :: ys
         case (xs, numString) => numString.toInt :: xs
       }
foldingFunction: (list: List[Double], next: String)List[Double]

scala> def solveRPN(s: String): Double =
         (s.split(' ').toList.foldLeft(Nil: List[Double]) {foldingFunction}).head
solveRPN: (s: String)Double

scala> solveRPN("10 4 3 + 2 * -")
res27: Double = -4.0
</scala>

動作しているみたいだ。次に畳み込み関数がエラーを処理できるようにする。Scalaz は `String` に `Validation[NumberFormatException, Int]` を返す `parseInt` を導入する。これに対して `toOption` を呼べば本の通り `Option[Int]` が得られる:

<scala>
scala> "1".parseInt.toOption
res31: Option[Int] = Some(1)

scala> "foo".parseInt.toOption
res32: Option[Int] = None
</scala>

以下が更新された畳込み関数:

<scala>
scala> def foldingFunction(list: List[Double], next: String): Option[List[Double]] = (list, next) match {
         case (x :: y :: ys, "*") => ((y * x) :: ys).point[Option]
         case (x :: y :: ys, "+") => ((y + x) :: ys).point[Option]
         case (x :: y :: ys, "-") => ((y - x) :: ys).point[Option]
         case (xs, numString) => numString.parseInt.toOption map {_ :: xs}
       }
foldingFunction: (list: List[Double], next: String)Option[List[Double]]

scala> foldingFunction(List(3, 2), "*")
res33: Option[List[Double]] = Some(List(6.0))

scala> foldingFunction(Nil, "*")
res34: Option[List[Double]] = None

scala> foldingFunction(Nil, "wawa")
res35: Option[List[Double]] = None
</scala>

以下が更新された `solveRPN`:

<scala>
scala> def solveRPN(s: String): Option[Double] = for {
         List(x) <- s.split(' ').toList.foldLeftM(Nil: List[Double]) {foldingFunction}
       } yield x
solveRPN: (s: String)Option[Double]

scala> solveRPN("1 2 * 4 +")
res36: Option[Double] = Some(6.0)

scala> solveRPN("1 2 * 4")
res37: Option[Double] = None

scala> solveRPN("1 8 garbage")
res38: Option[Double] = None
</scala>

### モナディック関数の合成

LYAHFGG:

> When we were learning about the monad laws, we said that the `<=<` function is just like composition, only instead of working for normal functions like `a -> b`, it works for monadic functions like `a -> m b`. 

これも飛ばしてたみたいだ。

### Kleisli

Scalaz には [Kleisli](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Kleisli.scala) と呼ばれる `A => M[B]` という型の関数に対する特殊なラッパーがある:

<scala>
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
</scala>

構築するには `Kleisli` オブジェクトを使う:

<scala>
scala> val f = Kleisli { (x: Int) => (x + 1).some }
f: scalaz.Kleisli[Option,Int,Int] = scalaz.KleisliFunctions$$anon$18@7da2734e

scala> val g = Kleisli { (x: Int) => (x * 100).some }
g: scalaz.Kleisli[Option,Int,Int] = scalaz.KleisliFunctions$$anon$18@49e07991
</scala>

`<=<` を使って関数を合成すると、`f compose g` と同様に右辺項が先に適用される。

<scala>
scala> 4.some >>= (f <=< g)
res59: Option[Int] = Some(401)
</scala>

`>=>` を使うと、`f andThen g` 同様に左辺項が先に適用される:

<scala>
scala> 4.some >>= (f >=> g)
res60: Option[Int] = Some(500)
</scala>

### Reader 再び

ボーナスとして、Scalaz は `Reader` を `Kleisli` の特殊形として以下のように定義する:

<scala>
  type ReaderT[F[+_], E, A] = Kleisli[F, E, A]
  type Reader[E, A] = ReaderT[Id, E, A]
  object Reader {
    def apply[E, A](f: E => A): Reader[E, A] = Kleisli[Id, E, A](f)
  }
</scala>

6日目のリーダーの例題は以下のように書き換えることができる:

<scala>
scala> val addStuff: Reader[Int, Int] = for {
         a <- Reader { (_: Int) * 2 }
         b <- Reader { (_: Int) + 10 }
       } yield a + b
addStuff: scalaz.Reader[Int,Int] = scalaz.KleisliFunctions$$anon$18@343bd3ae

scala> addStuff(3)
res76: scalaz.Id.Id[Int] = 19
</scala>

関数をモナドとして使っていることが少しかは明らかになったと思う。

### モナドを作る

LYAHFGG:

> In this section, we're going to look at an example of how a type gets made, identified as a monad and then given the appropriate `Monad` instance. 
> ...
> What if we wanted to model a non-deterministic value like `[3,5,9]`, but we wanted to express that `3` has a 50% chance of happening and `5` and `9` both have a 25% chance of happening? 

Scala に有理数が標準で入っていないので、`Double` を使う。以下が case class:

<scala>
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
</scala>

> Is this a functor? Well, the list is a functor, so this should probably be a functor as well, because we just added some stuff to the list.

<scala>
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
</scala>

本と同様に `flatten` をまず実装する。

<scala>
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
</scala>

これでモナドのための準備は整ったはずだ:

<scala>
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
</scala>

本によるとモナド則は満たしているらしい。`Coin` の例題も実装してみよう:

<scala>
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
</scala>

イカサマのコインを 1つ使っても 3回とも裏が出る確率はかなり低いことが分かった。

続きはまた後で。
