---
out: State.html
---

### 計算の状態の正体

[すごいHaskellたのしく学ぼう](http://www.amazon.co.jp/dp/4274068854) 曰く:

> そこで Haskell には `State` モナドが用意されています。これさえあれば、状態付きの計算などいとも簡単。しかもすべてを純粋に保ったまま扱えるんです。

スタックの例題を実装してみよう。今回は case class を作らずに Haskell を Scala に直訳してみる:

```scala
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
```

### State and StateT

LYAHFGG:

> 状態付きの計算とは、ある状態を取って、更新された状態と一緒に計算結果を返す関数として表現できるでしょう。そんな関数の型は、こうなるはずです。

```haskell
s -> (a, s)
```

ここで大切なのは、今まで見てきた汎用のモナドと違って `State` は関数をラッピングすることに特化していることだ。Scalaz での `Scala` の定義をみてみよう:

```scala
  type State[S, +A] = StateT[Id, S, A]

  // important to define here, rather than at the top-level, to avoid Scala 2.9.2 bug
  object State extends StateFunctions {
    def apply[S, A](f: S => (S, A)): State[S, A] = new StateT[Id, S, A] {
      def apply(s: S) = f(s)
    }
  }
```

`Writer` 同様に、`State[S, +A]` は `StateT[Id, S, A]` の型エイリアスだ。以下が [`StateT`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/StateT.scala) の簡易版だ:

```scala
trait StateT[F[+_], S, +A] { self =>
  /** Run and return the final value and state in the context of `F` */
  def apply(initial: S): F[(S, A)]

  /** An alias for `apply` */
  def run(initial: S): F[(S, A)] = apply(initial)

  /** Calls `run` using `Monoid[S].zero` as the initial state */
  def runZero(implicit S: Monoid[S]): F[(S, A)] =
    run(S.zero)
}
```

新しい状態は `State` シングルトンを使って構築する:

```scala
scala> State[List[Int], Int] { case x :: xs => (xs, x) }
res1: scalaz.State[List[Int],Int] = scalaz.package\$State\$\$anon\$1@19f58949
```

スタックを `State` を使って実装してみよう:

```scala
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
```

`State[List[Int], Int] {...}` を用いて「状態を抽出して、値と状態を返す」というコードの部分を抽象化することができた。強力なのは `for` 構文を使ってぞれぞれの演算を `State` を引き回さずにモナディックに連鎖できることだ。上の `stackManip` がそのいい例だ。

### 状態の取得と設定

LYAHFGG:

> `Control.Monad.State` モジュールは、2つの便利な関数 `get` と `put` を備えた、`MonadState` という型クラスを提供しています。

`State` object は `StateFunctions` trait を継承して、いくつかのヘルパー関数を定義する:

```scala
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
```

ちょっと最初は分かりづらかった。だけど、`State` モナドは「状態を受け取り値と状態を返す」関数をカプセル化していることを思い出してほしい。そのため、状態というコンテキストでの `get` は状態から値を取得するというだけの話だ:

```scala
  def init[S]: State[S, S] = State(s => (s, s))
  def get[S]: State[S, S] = init
```

そして、このコンテキストでの `put` は何からの値を状態に設定するということを指す:

```scala
  def put[S](s: S): State[S, Unit] = State(_ => (s, ()))
```

`stackStack` 関数を実装して具体例でみてみよう。

```scala
scala> def stackyStack: State[Stack, Unit] = for {
         stackNow <- get
         r <- if (stackNow === List(1, 2, 3)) put(List(8, 3, 1))
              else put(List(9, 2, 1))
       } yield r
stackyStack: scalaz.State[Stack,Unit]

scala> stackyStack(List(1, 2, 3))
res4: (Stack, Unit) = (List(8, 3, 1),())
```

`pop` と `push` も `get` と `put` を使って実装できる:

```scala
scala> val pop: State[Stack, Int] = for {
         s <- get[Stack]
         val (x :: xs) = s
         _ <- put(xs)
       } yield x
pop: scalaz.State[Stack,Int] = scalaz.StateT\$\$anon\$7@40014da3

scala> def push(x: Int): State[Stack, Unit] = for {
         xs <- get[Stack]
         r <- put(x :: xs)
       } yield r
push: (x: Int)scalaz.State[Stack,Unit]
```

見ての通りモナドそのものはあんまり大したこと無い (タプルを返す関数のカプセル化) けど、連鎖することでボイラープレートを省くことができた。
