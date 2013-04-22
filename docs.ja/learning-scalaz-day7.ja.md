  [day6]: http://eed3si9n.com/ja/learning-scalaz-day6

[6日目][day6]は、`for` 構文をみて、`Writer` モナドと関数をモナドとして扱うリーダーモナドをみた。

### Applicative Builder

実はリーダーモナドの話をしながらこっそり Applicative builder `|@|` を使った。[2日目](http://eed3si9n.com/ja/learning-scalaz-day2) に 7.0.0-M3 から新しく導入された `^(f1, f2) {...}` スタイルを紹介したけど、関数などの 2つの型パラメータを取る型コンストラクタでうまく動作しないみたいことが分かった。

Scalaz のメーリングリストを見ると `|@|` は deprecate 状態から復活するらしいので、これからはこのスタイルを使おう:

<scala>
scala> (3.some |@| 5.some) {_ + _}
res18: Option[Int] = Some(8)

scala> val f = ({(_: Int) * 2} |@| {(_: Int) + 10}) {_ + _}
f: Int => Int = <function1>
</scala>

### 計算の状態の正体

[Learn You a Haskell for Great Good](http://learnyouahaskell.com/for-a-few-monads-more) 曰く:

> Haskell features a thing called the state monad, which makes dealing with stateful problems a breeze while still keeping everything nice and pure.

スタックの例題を実装してみよう。今回は case class を作らずに Haskell を Scala に直訳してみる:

<scala>
scala> type Stack = List[Int]
defined type alias Stack

scala> def pop(stack: Stack): (Int, Stack) = stack match {
         case x :: xs => (x, xs)
       }
pop: (stack: Stack)(Int, Stack)

scala> def push(a: Int, stack: Stack): (Unit, Stack) = ((), a :: stack)
push: (a: Int, stack: Stack)(Unit, Stack)

scala> def stackManip(stack: Stack): (Int, Stack) = {
         val (_, newStack1) = push(3, stack)
         val (a, newStack2) = pop(newStack1)
         pop(newStack2)
       }
stackManip: (stack: Stack)(Int, Stack)

scala> stackManip(List(5, 8, 2, 1))
res0: (Int, Stack) = (5,List(8, 2, 1))
</scala>

### State and StateT

LYAHFGG:

>  We'll say that a stateful computation is a function that takes some state and returns a value along with some new state. That function would have the following type:

<haskell>
s -> (a, s)
</haskell>

ここで大切なのは、今まで見てきた汎用のモナドと違って `State` は関数をラッピングすることに特化していることだ。Scalaz での `Scala` の定義をみてみよう:

<scala>
  type State[S, +A] = StateT[Id, S, A]

  // important to define here, rather than at the top-level, to avoid Scala 2.9.2 bug
  object State extends StateFunctions {
    def apply[S, A](f: S => (S, A)): State[S, A] = new StateT[Id, S, A] {
      def apply(s: S) = f(s)
    }
  }
</scala>

`Writer` 同様に、`State[S, +A]` は `StateT[Id, S, A]` の型エイリアスだ。以下が [`StateT`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/StateT.scala) の簡易版だ:

<scala>
trait StateT[F[+_], S, +A] { self =>
  /** Run and return the final value and state in the context of `F` */
  def apply(initial: S): F[(S, A)]

  /** An alias for `apply` */
  def run(initial: S): F[(S, A)] = apply(initial)

  /** Calls `run` using `Monoid[S].zero` as the initial state */
  def runZero(implicit S: Monoid[S]): F[(S, A)] =
    run(S.zero)
}
</scala>

新しい状態は `State` シングルトンを使って構築する:

<scala>
scala> State[List[Int], Int] { case x :: xs => (xs, x) }
res1: scalaz.State[List[Int],Int] = scalaz.package$State$$anon$1@19f58949
</scala>

スタックを `State` を使って実装してみよう:

<scala>
scala> type Stack = List[Int]
defined type alias Stack

scala> val pop = State[Stack, Int] {
         case x :: xs => (xs, x)
       }
pop: scalaz.State[Stack,Int]

scala> def push(a: Int) = State[Stack, Unit] {
         case xs => (a :: xs, ())
       }
push: (a: Int)scalaz.State[Stack,Unit]

scala> def stackManip: State[Stack, Int] = for {
         _ <- push(3)
         a <- pop
         b <- pop
       } yield(b)
stackManip: scalaz.State[Stack,Int]

scala> stackManip(List(5, 8, 2, 1))
res2: (Stack, Int) = (List(8, 2, 1),5)
</scala>

`State[List[Int], Int] {...}` を用いて「状態を抽出して、値と状態を返す」というコードの部分を抽象化することができた。強力なのは `for` 構文を使ってぞれぞれの演算を `State` を引き回さずにモナディックに連鎖できることだ。上の `stackManip` がそのいい例だ。

### 状態の取得と設定

LYAHFGG:

> The `Control.Monad.State` module provides a type class that's called `MonadState` and it features two pretty useful functions, namely `get` and `put`.

`State` object は `StateFunctions` trait を継承して、いくつかのヘルパー関数を定義する:

<scala>
trait StateFunctions {
  def constantState[S, A](a: A, s: => S): State[S, A] =
    State((_: S) => (s, a))
  def state[S, A](a: A): State[S, A] =
    State((_ : S, a))
  def init[S]: State[S, S] = State(s => (s, s))
  def get[S]: State[S, S] = init
  def gets[S, T](f: S => T): State[S, T] = State(s => (s, f(s)))
  def put[S](s: S): State[S, Unit] = State(_ => (s, ()))
  def modify[S](f: S => S): State[S, Unit] = State(s => {
    val r = f(s);
    (r, ())
  })
  /**
   * Computes the difference between the current and previous values of `a`
   */
  def delta[A](a: A)(implicit A: Group[A]): State[A, A] = State{
    (prevA) =>
      val diff = A.minus(a, prevA)
      (diff, a)
  }
}
</scala>

ちょっと最初は分かりづらかった。だけど、`State` モナドは「状態を受け取り値と状態を返す」関数をカプセル化していることを思い出してほしい。そのため、状態というコンテキストでの `get` は状態から値を取得するというだけの話だ:

<scala>
  def init[S]: State[S, S] = State(s => (s, s))
  def get[S]: State[S, S] = init
</scala>

そして、このコンテキストでの `put` は何からの値を状態に設定するということを指す:

<scala>
  def put[S](s: S): State[S, Unit] = State(_ => (s, ()))
</scala>

`stackStack` 関数を実装して具体例でみてみよう。

<scala>
scala> def stackyStack: State[Stack, Unit] = for {
         stackNow <- get
         r <- if (stackNow === List(1, 2, 3)) put(List(8, 3, 1))
              else put(List(9, 2, 1))
       } yield r
stackyStack: scalaz.State[Stack,Unit]

scala> stackyStack(List(1, 2, 3))
res4: (Stack, Unit) = (List(8, 3, 1),())
</scala>

`pop` と `push` も `get` と `put` を使って実装できる:

<scala>
scala> val pop: State[Stack, Int] = for {
         s <- get[Stack]
         val (x :: xs) = s
         _ <- put(xs)
       } yield x
pop: scalaz.State[Stack,Int] = scalaz.StateT$$anon$7@40014da3

scala> def push(x: Int): State[Stack, Unit] = for {
         xs <- get[Stack]
         r <- put(x :: xs)
       } yield r
push: (x: Int)scalaz.State[Stack,Unit]
</scala>

見ての通りモナドそのものはあんまり大したこと無い (タプルを返す関数のカプセル化) けど、連鎖することでボイラープレートを省くことができた。

### エラーよエラー

LYAHFGG:

> The `Either e a` type on the other hand, allows us to incorporate a context of possible failure to our values while also being able to attach values to the failure, so that they can describe what went wrong or provide some other useful info regarding the failure.

### \/

標準ライブラリの `Either[A, B]` は知ってるけど、Scalaz 7 は `Either` に対応する独自のデータ構造 [`\/`](https://github.com/eed3si9n/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Either.scala) を提供する:

<scala>
sealed trait \/[+A, +B] {
  ...
  /** Return `true` if this disjunction is left. */
  def isLeft: Boolean =
    this match {
      case -\/(_) => true
      case \/-(_) => false
    }

  /** Return `true` if this disjunction is right. */
  def isRight: Boolean =
    this match {
      case -\/(_) => false
      case \/-(_) => true
    }
  ...
  /** Flip the left/right values in this disjunction. Alias for `unary_~` */
  def swap: (B \/ A) =
    this match {
      case -\/(a) => \/-(a)
      case \/-(b) => -\/(b)
    }
  /** Flip the left/right values in this disjunction. Alias for `swap` */
  def unary_~ : (B \/ A) = swap
  ...
  /** Return the right value of this disjunction or the given default if left. Alias for `|` */
  def getOrElse[BB >: B](x: => BB): BB =
    toOption getOrElse x
  /** Return the right value of this disjunction or the given default if left. Alias for `getOrElse` */
  def |[BB >: B](x: => BB): BB = getOrElse(x)
  
  /** Return this if it is a right, otherwise, return the given value. Alias for `|||` */
  def orElse[AA >: A, BB >: B](x: => AA \/ BB): AA \/ BB =
    this match {
      case -\/(_) => x
      case \/-(_) => this
    }
  /** Return this if it is a right, otherwise, return the given value. Alias for `orElse` */
  def |||[AA >: A, BB >: B](x: => AA \/ BB): AA \/ BB = orElse(x)
  ...
}

private case class -\/[+A](a: A) extends (A \/ Nothing)
private case class \/-[+B](b: B) extends (Nothing \/ B)
</scala>

これらの値は `IdOps` 経由で全てのデータ型に注入された `right` メソッドと `left` メソッドによって作られる:

<scala>
scala> 1.right[String]
res12: scalaz.\/[String,Int] = \/-(1)

scala> "error".left[Int]
res13: scalaz.\/[String,Int] = -\/(error)
</scala>

Scala 標準ライブラリの `Either` 型はそれ単体ではモナドではないため、Scalaz を使っても使わなくても `flatMap` メソッドを実装しない:

<scala>
scala> Left[String, Int]("boom") flatMap { x => Right[String, Int](x + 1) }
<console>:8: error: value flatMap is not a member of scala.util.Left[String,Int]
              Left[String, Int]("boom") flatMap { x => Right[String, Int](x + 1) }
                                        ^
</scala>

`right` メソッドを呼んで `RightProjection` に変える必要がある:

<scala>
scala> Left[String, Int]("boom").right flatMap { x => Right[String, Int](x + 1)}
res15: scala.util.Either[String,Int] = Left(boom)
</scala>

`Either` がそもそも存在する理由は左のエラーを報告するためにあるのだから、いちいち `right` を呼ぶのは手間だ。Scalaz の `\/` はだいたいにおいて右投射が欲しいだろうと決めてかかってくれる:

<scala>
scala> "boom".left[Int] >>= { x => (x + 1).right }
res18: scalaz.Unapply[scalaz.Bind,scalaz.\/[String,Int]]{type M[X] = scalaz.\/[String,X]; type A = Int}#M[Int] = -\/(boom)
</scala>

これは便利だ。`for` 構文から使ってみよう:

<scala>
scala> for {
         e1 <- "event 1 ok".right
         e2 <- "event 2 failed!".left[String]
         e3 <- "event 3 failed!".left[String]
       } yield (e1 |+| e2 |+| e3)
res24: scalaz.\/[String,String] = -\/(event 2 failed!)
</scala>

見ての通り、最初の失敗が最終結果に繰り上がった。`\/` からどうやって値を取り出せばいい? まず、`isRight` と `isLeft` でどっち側にいるか確かめる:

<scala>
scala> "event 1 ok".right.isRight
res25: Boolean = true

scala> "event 1 ok".right.isLeft
res26: Boolean = false
</scala>

右値なら `getOrElse` もしくはそのシンボルを使ったエイリアス `|` を使う:

<scala>
scala> "event 1 ok".right | "something bad"
res27: String = event 1 ok
</scala>

左値なら `swap` メソッドもしくはそのシンボルを使ったエイリアス `unary_~` を使う:

<scala>
scala> ~"event 2 failed!".left[String] | "something good"
res28: String = event 2 failed!
</scala>

`map` を使って右の値を変更できる:

<scala>
scala> "event 1 ok".right map {_ + "!"}
res31: scalaz.\/[Nothing,String] = \/-(event 1 ok!)
</scala>

左側で連鎖させるには、`=> AA \/ BB` (ただし `[AA >: A, BB >: B]`) を受け取る `orElse` がある。`orElse` のシンボルを使ったエイリアスは `|||` だ:

<scala>
scala> "event 1 failed!".left ||| "retry event 1 ok".right 
res32: scalaz.\/[String,String] = \/-(retry event 1 ok)
</scala>

### Validation

Scalaz のデータ構造で `Either` と比較されるものにもう1つ [`Validation`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Validation.scala) がある:

<scala>
sealed trait Validation[+E, +A] {
  /** Return `true` if this validation is success. */
  def isSuccess: Boolean = this match {
    case Success(_) => true
    case Failure(_) => false
  }
  /** Return `true` if this validation is failure. */
  def isFailure: Boolean = !isSuccess

  ...
}

final case class Success[E, A](a: A) extends Validation[E, A]
final case class Failure[E, A](e: E) extends Validation[E, A]
</scala>

一見すると `Validation` は `\/` に似ている。お互い `validation` メソッドと `disjunction` メソッドを使って変換することまでできる。

[`ValidationV`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/ValidationV.scala) によって全てのデータ型に `success[X]`、 `successNel[X]`、`failure[X]`、`failureNel[X]` メソッドが導入されている (今のところ `Nel` に関しては心配しなくていい):

<scala>
scala> "event 1 ok".success[String]
res36: scalaz.Validation[String,String] = Success(event 1 ok)

scala> "event 1 failed!".failure[String]
res38: scalaz.Validation[String,String] = Failure(event 1 failed!)
</scala>

`Validation` の違いはこれがモナドではなく、Applicative functor であることだ。最初のイベントの結果を次へと連鎖するのでは無く、`Validation` は全イベントを検証する:

<scala>
scala> ("event 1 ok".success[String] |@| "event 2 failed!".failure[String] |@| "event 3 failed!".failure[String]) {_ + _ + _}
res44: scalaz.Unapply[scalaz.Apply,scalaz.Validation[String,String]]{type M[X] = scalaz.Validation[String,X]; type A = String}#M[String] = Failure(event 2 failed!event 3 failed!)
</scala>

ちょっと読みづらいけど、最終結果は `Failure(event 2 failed!event 3 failed!)` だ。計算途中でショートさせた `\/` と違って、`Validation` は計算を続行して全ての失敗を報告する。これはおそらくオンラインのベーコンショップでユーザのインプットを検証するのに役立つと思う。

だけど、問題はエラーメッセージが 1つの文字列にゴチャっと一塊になってしまっていることだ。リストでも使うべきじゃないか?

### NonEmptyList

ここで [`NonEmptyList`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/NonEmptyList.scala) (略して `Nel`) が登場する:

<scala>
/** A singly-linked list that is guaranteed to be non-empty. */
sealed trait NonEmptyList[+A] {
  val head: A
  val tail: List[A]
  def <::[AA >: A](b: AA): NonEmptyList[AA] = nel(b, head :: tail)
  ...
}
</scala>

これは素の `List` のラッパーで、空じゃないことを保証する。必ず 1つのアイテムがあることで `head` は常に成功する。`IdOps` は `Nel` を作るために `wrapNel` を全てのデータ型に導入する。

<scala>
scala> 1.wrapNel
res47: scalaz.NonEmptyList[Int] = NonEmptyList(1)
</scala>

これで `successNel[X]` と `failureNel[X]` が分かったかな?

<scala>
scala> "event 1 ok".successNel[String]
res48: scalaz.ValidationNEL[String,String] = Success(event 1 ok)

scala> "event 1 failed!".failureNel[String]
res49: scalaz.ValidationNEL[String,String] = Failure(NonEmptyList(event 1 failed!))

scala> ("event 1 ok".successNel[String] |@| "event 2 failed!".failureNel[String] |@| "event 3 failed!".failureNel[String]) {_ + _ + _}
res50: scalaz.Unapply[scalaz.Apply,scalaz.ValidationNEL[String,String]]{type M[X] = scalaz.ValidationNEL[String,X]; type A = String}#M[String] = Failure(NonEmptyList(event 2 failed!, event 3 failed!))
</scala>

`Failure` の中に全ての失敗メッセージを集約することができた。

続きはまたあとで。
