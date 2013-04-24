---
out: Writer.html
---

### Writer? 中の人なんていません!

[すごいHaskellたのしく学ぼう](http://www.amazon.co.jp/dp/4274068854) 曰く:

> `Maybe` モナドが失敗の可能性という文脈付きの値を表し、リストモナドが非決定性が付いた値を表しているのに対し、`Writer` モナドは、もう1つの値がくっついた値を表し、付加された値はログのように振る舞います。

本に従って `applyLog` 関数を実装してみよう:

```scala
scala> def isBigGang(x: Int): (Boolean, String) =
         (x > 9, "Compared gang size to 9.")
isBigGang: (x: Int)(Boolean, String)

scala> implicit class PairOps[A](pair: (A, String)) {
         def applyLog[B](f: A => (B, String)): (B, String) = {
           val (x, log) = pair
           val (y, newlog) = f(x)
           (y, log ++ newlog)
         }
       }
defined class PairOps

scala> (3, "Smallish gang.") applyLog isBigGang
res30: (Boolean, String) = (false,Smallish gang.Compared gang size to 9.)
```

メソッドの注入が implicit のユースケースとしては多いため、Scala 2.10 に implicit class という糖衣構文が登場して、クラスから強化クラスに昇進させるのが簡単になった。ログを `Monoid` として一般化する:

```scala
scala> implicit class PairOps[A, B: Monoid](pair: (A, B)) {
         def applyLog[C](f: A => (C, B)): (C, B) = {
           val (x, log) = pair
           val (y, newlog) = f(x)
           (y, log |+| newlog)
         }
       }
defined class PairOps

scala> (3, "Smallish gang.") applyLog isBigGang
res31: (Boolean, String) = (false,Smallish gang.Compared gang size to 9.)
```

### Writer

LYAHFGG:

> 値にモノイドのおまけを付けるには、タプルに入れるだけです。`Writer w a` 型の実体は、そんなタプルの `newtype` ラッパーにすぎず、定義はとてもシンプルです。

Scalaz でこれに対応するのは [`Writer`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/package.scala) だ:

```scala
type Writer[+W, +A] = WriterT[Id, W, A]
```

`Writer[+W, +A]` は、`WriterT[Id, W, A]` の型エイリアスだ。

### WriterT

以下が [`WriterT`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/WriterT.scala) を単純化したものだ:

```scala
sealed trait WriterT[F[+_], +W, +A] { self =>
  val run: F[(W, A)]

  def written(implicit F: Functor[F]): F[W] =
    F.map(run)(_._1)
  def value(implicit F: Functor[F]): F[A] =
    F.map(run)(_._2)
}
```

Writer が実際にどうやって作られるのかは直ぐには分からなかったけど、見つけることができた:

```scala
scala> 3.set("Smallish gang.")
res46: scalaz.Writer[String,Int] = scalaz.WriterTFunctions$$anon$26@477a0c05
```

`import Scalaz._` によって全てのデータ型に対して以下の演算子が導入される:

```scala
trait ToDataOps extends ToIdOps with ToTreeOps with ToWriterOps with ToValidationOps with ToReducerOps with ToKleisliOps
```

件の演算子は [`WriterV`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/ToWriterOps.scala) の一部だ:

```scala
trait WriterV[A] extends Ops[A] {
  def set[W](w: W): Writer[W, A] = WriterT.writer(w -> self)

  def tell: Writer[A, Unit] = WriterT.tell(self)
}
```

上のメソッドは全ての型に注入されるため、以下のように Writer を作ることができる:

```scala
scala> 3.set("something")
res57: scalaz.Writer[String,Int] = scalaz.WriterTFunctions$$anon$26@159663c3

scala> "something".tell
res58: scalaz.Writer[String,Unit] = scalaz.WriterTFunctions$$anon$26@374de9cf
```

`return 3 :: Writer String Int` のように単位元が欲しい場合はどうすればいいだろう? `Monad[F[_]]` は型パラメータが 1つの型コンストラクタを期待するけど、`Writer[+W, +A]` は 2つある。Scalaz にある `MonadWriter` というヘルパー型を使うと簡単にモナドが得られる:

```scala
scala> MonadWriter[Writer, String]
res62: scalaz.MonadWriter[scalaz.Writer,String] = scalaz.WriterTInstances$$anon$1@6b8501fa

scala> MonadWriter[Writer, String].point(3).run
res64: (String, Int) = ("",3)
```

### Writer に for 構文を使う

LYAHFGG:

> こうして `Monad` インスタンスができたので、`Writer` を `do` 記法で自由に扱えます。

例題を Scala で実装してみよう:

```scala
scala> def logNumber(x: Int): Writer[List[String], Int] =
         x.set(List("Got number: " + x.shows))
logNumber: (x: Int)scalaz.Writer[List[String],Int]

scala> def multWithLog: Writer[List[String], Int] = for {
         a <- logNumber(3)
         b <- logNumber(5)
       } yield a * b
multWithLog: scalaz.Writer[List[String],Int]

scala> multWithLog.run
res67: (List[String], Int) = (List(Got number: 3, Got number: 5),15)
```

### プログラムにログを追加する

以下が例題の `gcd` だ:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

def gcd(a: Int, b: Int): Writer[List[String], Int] =
  if (b == 0) for {
      _ <- List("Finished with " + a.shows).tell
    } yield a
  else
    List(a.shows + " mod " + b.shows + " = " + (a % b).shows).tell >>= { _ =>
      gcd(b, a % b)
    }

// Exiting paste mode, now interpreting.

gcd: (a: Int, b: Int)scalaz.Writer[List[String],Int]

scala> gcd(8, 3).run
res71: (List[String], Int) = (List(8 mod 3 = 2, 3 mod 2 = 1, 2 mod 1 = 0, Finished with 1),1)
```

### 非効率な List の構築

LYAHFGG:

> `Writer` モナドを使うときは、使うモナドに気をつけてください。リストを使うととても遅くなる場合があるからです。リストは `mappend` に `++` を使っていますが、`++` を使ってリストの最後にものを追加する操作は、そのリストがとても長いと遅くなってしまいます。

[主なコレクションの性能特性をまとめた表](http://scalajp.github.com/scala-collections-doc-ja/collections_40.html)があるので見てみよう。不変コレクションで目立っているのが全ての演算を実質定数でこなす `Vector` だ。`Vector` は分岐度が 32 の木構造で、構造共有を行うことで高速な更新を実現している。

何故か Scalaz 7 は `Vector` の型クラスを `import Scalaz._` に含めていない。手動で import する:

```scala
scala> import std.vector._
import std.vector._

scala> Monoid[Vector[String]]
res73: scalaz.Monoid[Vector[String]] = scalaz.std.IndexedSeqSubInstances$$anon$4@6f82f06f
```

Vector を使った `gcd`:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

def gcd(a: Int, b: Int): Writer[Vector[String], Int] =
  if (b == 0) for {
      _ <- Vector("Finished with " + a.shows).tell
    } yield a
  else for {
      result <- gcd(b, a % b)
      _ <- Vector(a.shows + " mod " + b.shows + " = " + (a % b).shows).tell
    } yield result

// Exiting paste mode, now interpreting.

gcd: (a: Int, b: Int)scalaz.Writer[Vector[String],Int]

scala> gcd(8, 3).run
res74: (Vector[String], Int) = (Vector(Finished with 1, 2 mod 1 = 0, 3 mod 2 = 1, 8 mod 3 = 2),1)
```

### 性能の比較

本のように性能を比較するマイクロベンチマークを書いてみよう:

```scala
import std.vector._

def vectorFinalCountDown(x: Int): Writer[Vector[String], Unit] = {
  import annotation.tailrec
  @tailrec def doFinalCountDown(x: Int, w: Writer[Vector[String], Unit]): Writer[Vector[String], Unit] = x match {
    case 0 => w >>= { _ => Vector("0").tell }
    case x => doFinalCountDown(x - 1, w >>= { _ =>
      Vector(x.shows).tell
    })
  }
  val t0 = System.currentTimeMillis
  val r = doFinalCountDown(x, Vector[String]().tell)
  val t1 = System.currentTimeMillis
  r >>= { _ => Vector((t1 - t0).shows + " msec").tell }
}

def listFinalCountDown(x: Int): Writer[List[String], Unit] = {
  import annotation.tailrec
  @tailrec def doFinalCountDown(x: Int, w: Writer[List[String], Unit]): Writer[List[String], Unit] = x match {
    case 0 => w >>= { _ => List("0").tell }
    case x => doFinalCountDown(x - 1, w >>= { _ =>
      List(x.shows).tell
    })
  }
  val t0 = System.currentTimeMillis
  val r = doFinalCountDown(x, List[String]().tell)
  val t1 = System.currentTimeMillis
  r >>= { _ => List((t1 - t0).shows + " msec").tell }
}
```

以下のように実行できる:

```scala
scala> vectorFinalCountDown(10000).run
res18: (Vector[String], Unit) = (Vector(10000, 9999, 9998, 9997, 9996, 9995, 9994, 9993, 9992, 9991, 9990, 9989, 9988, 9987, 9986, 9985, 9984, ...

scala> res18._1.last
res19: String = 1206 msec

scala> listFinalCountDown(10000).run
res20: (List[String], Unit) = (List(10000, 9999, 9998, 9997, 9996, 9995, 9994, 9993, 9992, 9991, 9990, 9989, 9988, 9987, 9986, 9985, 9984, ...
scala> res20._1.last

res21: String = 2050 msec
```

`List` に倍近くの時間がかかっているのが分かる。
