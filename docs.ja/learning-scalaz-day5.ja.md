  [day4]: http://eed3si9n.com/ja/learning-scalaz-day4

[4日目][day4]は Functor則などのモナドの規則をみて、ScalaCheck を用いて任意の型クラスの例を使って検証した。また、`Option` を `Monoid` として扱う3つの方法や `foldMap` などを行う `Foldable` もみた。

### モナドがいっぱい

今日は [Learn You a Haskell for Great Good](http://learnyouahaskell.com/a-fistful-of-monads) の新しい章を始めることができる。

> Monads are a natural extension applicative functors, and they provide a solution to the following problem: If we have a value with context, `m a`, how do we apply it to a function that takes a normal `a` and returns a value with a context.

Scalaz でもモナドは `Monad` と呼ばれている。[型クラスのコントラクト](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Monad.scala)はこれだ:

<scala>
trait Monad[F[_]] extends Applicative[F] with Bind[F] { self =>
  ////
}
</scala>

これは `Applicative` と `Bind` を拡張する。`Bind` を見てみよう。

### Bind

以下が [`Bind` のコントラクト](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Bind.scala)だ:

<scala>
trait Bind[F[_]] extends Apply[F] { self =>
  /** Equivalent to `join(map(fa)(f))`. */
  def bind[A, B](fa: F[A])(f: A => F[B]): F[B]
}
</scala>

そして、以下が[演算子](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/BindSyntax.scala):

<scala>
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
</scala>

`flatMap` 演算子とシンボルを使ったエイリアス `>>=` と `∗` を導入する。他の演算子に関しては後回しにしよう。とりあえず標準ライブラリで `flatMap` は慣れている:

<scala>
scala> 3.some flatMap { x => (x + 1).some }
res2: Option[Int] = Some(4)

scala> (none: Option[Int]) flatMap { x => (x + 1).some }
res3: Option[Int] = None
</scala>

### Monad

`Monad` に戻ろう:

<scala>
trait Monad[F[_]] extends Applicative[F] with Bind[F] { self =>
  ////
}
</scala>

Haskell と違って `Monad[F[_]]` は `Applicative[F[_]]` を継承するため、`return` と `pure` と名前が異なるという問題が生じていない。両者とも `point` だ。

<scala>
scala> Monad[Option].point("WHAT")
res5: Option[String] = Some(WHAT)

scala> 9.some flatMap { x => Monad[Option].point(x * 10) }
res6: Option[Int] = Some(90)

scala> (none: Option[Int]) flatMap { x => Monad[Option].point(x * 10) }
res7: Option[Int] = None
</scala>

### 綱渡り

LYAHFGG:

> Let's say that [Pierre] keeps his balance if the number of birds on the left side of the pole and on the right side of the pole is within three. So if there's one bird on the right side and four birds on the left side, he's okay. But if a fifth bird lands on the left side, then he loses his balance and takes a dive.

本の `Pole` の例題を実装してみよう。

<scala>
scala> type Birds = Int
defined type alias Birds

scala> case class Pole(left: Birds, right: Birds)
defined class Pole
</scala>

Scala ではこんな風に `Int` に型エイリアスを付けるのは一般的じゃないと思うけど、ものは試しだ。`landLeft` と `landRight` をメソッドをとして実装したいから `Pole` は case class にする。

<scala>
scala> case class Pole(left: Birds, right: Birds) {
         def landLeft(n: Birds): Pole = copy(left = left + n)
         def landRight(n: Birds): Pole = copy(right = right + n) 
       }
defined class Pole
</scala>

OO の方が見栄えが良いと思う:

<scala>
scala> Pole(0, 0).landLeft(2)
res10: Pole = Pole(2,0)

scala> Pole(1, 2).landRight(1)
res11: Pole = Pole(1,3)

scala> Pole(1, 2).landRight(-1)
res12: Pole = Pole(1,1)
</scala>

チェインも可能:

<scala>
scala> Pole(0, 0).landLeft(1).landRight(1).landLeft(2)
res13: Pole = Pole(3,1)

scala> Pole(0, 0).landLeft(1).landRight(4).landLeft(-1).landRight(-2)
res15: Pole = Pole(0,2)
</scala>

本が言うとおり、中間値で失敗しても計算が続行してしまっている。失敗を `Option[Pole]` で表現しよう:

<scala>
scala> case class Pole(left: Birds, right: Birds) {
         def landLeft(n: Birds): Option[Pole] = 
           if (math.abs((left + n) - right) < 4) copy(left = left + n).some
           else none
         def landRight(n: Birds): Option[Pole] =
           if (math.abs(left - (right + n)) < 4) copy(right = right + n).some
           else none
       }
defined class Pole

scala> Pole(0, 0).landLeft(2)
res16: Option[Pole] = Some(Pole(2,0))

scala> Pole(0, 3).landLeft(10)
res17: Option[Pole] = None
</scala>

`flatMap` を使ってチェインする:

<scala>
scala> Pole(0, 0).landRight(1) flatMap {_.landLeft(2)}
res18: Option[Pole] = Some(Pole(2,1))

scala> (none: Option[Pole]) flatMap {_.landLeft(2)}
res19: Option[Pole] = None

scala> Monad[Option].point(Pole(0, 0)) flatMap {_.landRight(2)} flatMap {_.landLeft(2)} flatMap {_.landRight(2)}
res21: Option[Pole] = Some(Pole(2,4))
</scala>

初期値を `Option` コンテキストから始めるために `Monad[Option].point(...)` が使われていることに注意。`>>=` エイリアスも使うと見た目がモナディックになる:

<scala>
scala> Monad[Option].point(Pole(0, 0)) >>= {_.landRight(2)} >>= {_.landLeft(2)} >>= {_.landRight(2)}
res22: Option[Pole] = Some(Pole(2,4))
</scala>

モナディックチェインが綱渡りのシミュレーションを改善したか確かめる:

<scala>
scala> Monad[Option].point(Pole(0, 0)) >>= {_.landLeft(1)} >>= {_.landRight(4)} >>= {_.landLeft(-1)} >>= {_.landRight(-2)}
res23: Option[Pole] = None
</scala>

うまくいった。

### 綱の上のバナナ

LYAHFGG:

> We may also devise a function that ignores the current number of birds on the balancing pole and just makes Pierre slip and fall. We can call it `banana`.

以下が常に失敗する `banana` だ:

<scala>
scala> case class Pole(left: Birds, right: Birds) {
         def landLeft(n: Birds): Option[Pole] = 
           if (math.abs((left + n) - right) < 4) copy(left = left + n).some
           else none
         def landRight(n: Birds): Option[Pole] =
           if (math.abs(left - (right + n)) < 4) copy(right = right + n).some
           else none
         def banana: Option[Pole] = none
       }
defined class Pole

scala> Monad[Option].point(Pole(0, 0)) >>= {_.landLeft(1)} >>= {_.banana} >>= {_.landRight(1)}
res24: Option[Pole] = None
</scala>

LYAHFGG:

> Instead of making functions that ignore their input and just return a predetermined monadic value, we can use the `>>` function.

以下が `>>` の `Option` での振る舞い:

<scala>
scala> (none: Option[Int]) >> 3.some
res25: Option[Int] = None

scala> 3.some >> 4.some
res26: Option[Int] = Some(4)

scala> 3.some >> (none: Option[Int])
res27: Option[Int] = None
</scala>

`banana` を `>> (none: Option[Pole])` に置き換えてみよう:

<scala>
scala> Monad[Option].point(Pole(0, 0)) >>= {_.landLeft(1)} >> (none: Option[Pole]) >>= {_.landRight(1)}
<console>:26: error: missing parameter type for expanded function ((x$1) => x$1.landLeft(1))
              Monad[Option].point(Pole(0, 0)) >>= {_.landLeft(1)} >> (none: Option[Pole]) >>= {_.landRight(1)}
                                                   ^
</scala>

突然型推論が崩れてしまった。問題の原因はおそらく演算子の優先順位にある。 [Programming in Scala](http://www.artima.com/pins1ed/basic-types-and-operations.html) 曰く:

> The one exception to the precedence rule, alluded to above, concerns assignment operators, which end in an equals character. If an operator ends in an equals character (`=`), and the operator is not one of the comparison operators `<=`, `>=`, `==`, or `!=`, then the precedence of the operator is the same as that of simple assignment (`=`). That is, it is lower than the precedence of any other operator.

注意: 上記の記述は不完全だ。代入演算子ルールのもう1つの例外は演算子が `===` のように (`=`) から始まる場合だ。

`>>=` (bind) が等号で終わるため、優先順位は最下位に落とされ、`({_.landLeft(1)} >> (none: Option[Pole]))` が先に評価される。いくつかの気が進まない回避方法がある。まず、普通のメソッド呼び出しのようにドットと括弧の記法を使うことができる:

<scala>
scala> Monad[Option].point(Pole(0, 0)).>>=({_.landLeft(1)}).>>(none: Option[Pole]).>>=({_.landRight(1)})
res9: Option[Pole] = None
</scala>

もしくは優先順位の問題に気付いたなら、適切な場所に括弧を置くことができる:

<scala>
scala> (Monad[Option].point(Pole(0, 0)) >>= {_.landLeft(1)}) >> (none: Option[Pole]) >>= {_.landRight(1)}
res10: Option[Pole] = None
</scala>

両方とも正しい答が得られた。ちなみに、`>>=` を `flatMap` に変えても `>>` の方がまだ優先順位が高いため問題は解決しない。

### for 構文

LYAHFGG:

> Monads in Haskell are so useful that they got their own special syntax called `do` notation.

まずは入れ子のラムダ式を書いてみよう:

<scala>
scala> 3.some >>= { x => "!".some >>= { y => (x.shows + y).some } }
res14: Option[String] = Some(3!)
</scala>

`>>=` が使われたことで計算のどの部分も失敗することができる:

<scala>
scala> 3.some >>= { x => (none: Option[String]) >>= { y => (x.shows + y).some } }
res17: Option[String] = None

scala> (none: Option[Int]) >>= { x => "!".some >>= { y => (x.shows + y).some } }
res16: Option[String] = None

scala> 3.some >>= { x => "!".some >>= { y => (none: Option[String]) } }
res18: Option[String] = None
</scala>

Haskell の `do` 記法のかわりに、Scala には `for` 構文があり、これらは同じものだ:

<scala>
scala> for {
         x <- 3.some
         y <- "!".some
       } yield (x.shows + y)
res19: Option[String] = Some(3!)
</scala>

LYAHFGG:

> In a `do` expression, every line that isn't a `let` line is a monadic value. 

これも Scala の `for` 構文に当てはまると思う。

### ピエール再び

LYAHFGG:

> Our tightwalker's routine can also be expressed with `do` notation.

<scala>
scala> def routine: Option[Pole] =
         for {
           start <- Monad[Option].point(Pole(0, 0))
           first <- start.landLeft(2)
           second <- first.landRight(2)
           third <- second.landLeft(1)
         } yield third
routine: Option[Pole]

scala> routine
res20: Option[Pole] = Some(Pole(3,2))
</scala>

`yield` は `Option[Pole]` じゃなくて `Pole` を受け取るため、`third` も抽出する必要があった。

LYAHFGG:

> If we want to throw the Pierre a banana peel in `do` notation, we can do the following:

<scala>
scala> def routine: Option[Pole] =
         for {
           start <- Monad[Option].point(Pole(0, 0))
           first <- start.landLeft(2)
           _ <- (none: Option[Pole])
           second <- first.landRight(2)
           third <- second.landLeft(1)
         } yield third
routine: Option[Pole]

scala> routine
res23: Option[Pole] = None
</scala>

### パターンマッチングと失敗

LYAHFGG:

> In `do` notation, when we bind monadic values to names, we can utilize pattern matching, just like in let expressions and function parameters.

<scala>
scala> def justH: Option[Char] =
         for {
           (x :: xs) <- "hello".toList.some
         } yield x
justH: Option[Char]

scala> justH
res25: Option[Char] = Some(h)
</scala>

> When pattern matching fails in a do expression, the `fail` function is called. It's part of the `Monad` type class and it enables failed pattern matching to result in a failure in the context of the current monad instead of making our program crash. 

<scala>
scala> def wopwop: Option[Char] =
         for {
           (x :: xs) <- "".toList.some
         } yield x
wopwop: Option[Char]

scala> wopwop
res28: Option[Char] = None
</scala>

失敗したパターンマッチングは `None` を返している。これは `for` 構文の興味深い一面で、今まで考えたことがなかったが、言われるとなるほどと思う。

### List モナド

LYAHFGG:

> On the other hand, a value like `[3,8,9]` contains several results, so we can view it as one value that is actually many values at the same time. Using lists as applicative functors showcases this non-determinism nicely.

まずは Applicative としての `List` を復習する (この記法は Scalaz 7.0.0-M3 が必要かもしれない):

<scala>
scala> ^(List(1, 2, 3), List(10, 100, 100)) {_ * _}
res29: List[Int] = List(10, 100, 100, 20, 200, 200, 30, 300, 300)
</scala>

> let's try feeding a non-deterministic value to a function:

<scala>
scala> List(3, 4, 5) >>= {x => List(x, -x)}
res30: List[Int] = List(3, -3, 4, -4, 5, -5)
</scala>

モナディックな視点に立つと、`List` というコンテキストは複数の解がありうる数学的な値を表す。それ以外は、`for` を使って `List` を操作するなどは素の Scala と変わらない:

<scala>
scala> for {
         n <- List(1, 2)
         ch <- List('a', 'b')
       } yield (n, ch)
res33: List[(Int, Char)] = List((1,a), (1,b), (2,a), (2,b))
</scala>

### MonadPlus と guard 関数

Scala の `for` 構文はフィルタリングができる:

<scala>
scala> for {
         x <- 1 |-> 50 if x.shows contains '7'
       } yield x
res40: List[Int] = List(7, 17, 27, 37, 47)
</scala>

LYAHFGG:

> The `MonadPlus` type class is for monads that can also act as monoids.

以下が [`MonadPlus` の型クラスのコントラクト](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/MonadPlus.scala)だ:

<scala>
trait MonadPlus[F[_]] extends Monad[F] with ApplicativePlus[F] { self =>
  ...
}
</scala>

### Plus、PlusEmpty、と ApplicativePlus

これは [`ApplicativePlus`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/ApplicativePlus.scala) を継承している:

<scala>
trait ApplicativePlus[F[_]] extends Applicative[F] with PlusEmpty[F] { self =>
  ...
}
</scala>

そして、それは [`PlusEmpty`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/PlusEmpty.scala) を継承している:

<scala>
trait PlusEmpty[F[_]] extends Plus[F] { self =>
  ////
  def empty[A]: F[A]
}
</scala>

そして、それは [`Plus`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/PlusEmpty.scala) を継承している:

<scala>
trait Plus[F[_]]  { self =>
  def plus[A](a: F[A], b: => F[A]): F[A]
}
</scala>


`Semigroup[A]` と `Monoid[A]` 同様に、`Plus[F[_]]` と `PlusEmpty[F[_]]` はそれらのインスタンスが `plus` と `empty` を実装することを要請する。違いはこれが型コンストラクタ (`F[_]`) レベルであることだ。

`Plus` は 2つのコンテナを連結する `<+>` 演算子を導入する:

<scala>
scala> List(1, 2, 3) <+> List(4, 5, 6)
res43: List[Int] = List(1, 2, 3, 4, 5, 6)
</scala>

### MonadPlus 再び

`MonadPlus` は `filter` 演算を導入する。

<scala>
scala> (1 |-> 50) filter { x => x.shows contains '7' }
res46: List[Int] = List(7, 17, 27, 37, 47)
</scala>

### 騎士の冒険

LYAHFGG:

> Here's a problem that really lends itself to being solved with non-determinism. Say you have a chess board and only one knight piece on it. We want to find out if the knight can reach a certain position in three moves.

ペアに型エイリアスと付けるかわりにまた case class にしよう:

<scala>
scala> case class KnightPos(c: Int, r: Int)
defined class KnightPos
</scala>

以下がナイトの次に取りうる位置を全て計算する関数だ:

<scala>
scala> case class KnightPos(c: Int, r: Int) {
         def move: List[KnightPos] =
           for {
             KnightPos(c2, r2) <- List(KnightPos(c + 2, r - 1), KnightPos(c + 2, r + 1),
               KnightPos(c - 2, r - 1), KnightPos(c - 2, r + 1),
               KnightPos(c + 1, r - 2), KnightPos(c + 1, r + 2),
               KnightPos(c - 1, r - 2), KnightPos(c - 1, r + 2)) if (
               ((1 |-> 8) contains c2) && ((1 |-> 8) contains r2))
           } yield KnightPos(c2, r2)
       }
defined class KnightPos

scala> KnightPos(6, 2).move
res50: List[KnightPos] = List(KnightPos(8,1), KnightPos(8,3), KnightPos(4,1), KnightPos(4,3), KnightPos(7,4), KnightPos(5,4))

scala> KnightPos(8, 1).move
res51: List[KnightPos] = List(KnightPos(6,2), KnightPos(7,3))
</scala>

答は合ってるみたいだ。次に、3回のチェインを実装する:

<scala>
scala> case class KnightPos(c: Int, r: Int) {
         def move: List[KnightPos] =
           for {
             KnightPos(c2, r2) <- List(KnightPos(c + 2, r - 1), KnightPos(c + 2, r + 1),
             KnightPos(c - 2, r - 1), KnightPos(c - 2, r + 1),
             KnightPos(c + 1, r - 2), KnightPos(c + 1, r + 2),
             KnightPos(c - 1, r - 2), KnightPos(c - 1, r + 2)) if (
             ((1 |-> 8) element c2) && ((1 |-> 8) contains r2))
           } yield KnightPos(c2, r2)
         def in3: List[KnightPos] =
           for {
             first <- move
             second <- first.move
             third <- second.move
           } yield third
         def canReachIn3(end: KnightPos): Boolean = in3 contains end
       }
defined class KnightPos

scala> KnightPos(6, 2) canReachIn3 KnightPos(6, 1)
res56: Boolean = true

scala> KnightPos(6, 2) canReachIn3 KnightPos(7, 3)
res57: Boolean = false
</scala>

### Monad則

#### 左単位元

LYAHFGG:

> The first monad law states that if we take a value, put it in a default context with `return` and then feed it to a function by using `>>=`, it's the same as just taking the value and applying the function to it. 

これを Scala で表現すると、

<scala>
// (Monad[F].point(x) flatMap {f}) assert_=== f(x)

scala> (Monad[Option].point(3) >>= { x => (x + 100000).some }) assert_=== 3 |> { x => (x + 100000).some }
</scala>

#### 右単位元

> The second law states that if we have a monadic value and we use `>>=` to feed it to `return`, the result is our original monadic value.

<scala>
// (m forMap {Monad[F].point(_)}) assert_=== m

scala> ("move on up".some flatMap {Monad[Option].point(_)}) assert_=== "move on up".some
</scala>

#### 結合律

> The final monad law says that when we have a chain of monadic function applications with `>>=`, it shouldn't matter how they're nested. 

<scala>
// (m flatMap f) flatMap g assert_=== m flatMap { x => f(x) flatMap {g} }

scala> Monad[Option].point(Pole(0, 0)) >>= {_.landRight(2)} >>= {_.landLeft(2)} >>= {_.landRight(2)}
res76: Option[Pole] = Some(Pole(2,4))

scala> Monad[Option].point(Pole(0, 0)) >>= { x =>
       x.landRight(2) >>= { y =>
       y.landLeft(2) >>= { z =>
       z.landRight(2)
       }}}
res77: Option[Pole] = Some(Pole(2,4))
</scala>

Scalaz 7 はモナド則を以下のように表現する:

<scala>
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
</scala>

以下が `Option` がモナド則に従うかを検証する方法だ。 4日目の `build.sbt` を用いて `sbt test:console` を実行する:

<scala>
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
</scala>

`Option` よくできました。続きはここから。
