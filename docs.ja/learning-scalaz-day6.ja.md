  [day5]: http://eed3si9n.com/ja/learning-scalaz-day5

[昨日][day5]は、`flatMap` を導入する `Monad` 型クラスをみた。また、モナディックなチェインが値にコンテキストを与えることも確認した。`Option` も `List` も標準ライブラリに `flatMap` があるから、新しいコードというよりは今まであったものに対して視点を変えて見るという感じになった。あと、モナディックな演算をチェインする方法としての `for` 構文も確認した。

### for 構文、再び

Haskell の `do` 記法と Scala の `for` 構文には微妙な違いがある。以下が `do` 表記の例:

<haskell>
foo = do
  x <- Just 3
  y <- Just "!"
  Just (show x ++ y)
</haskell>

通常は `return (show x ++ y)` と書くと思うけど、最後の行がモナディックな値であることを強調するために `Just` を書き出した。一方 Scala はこうだ:

<scala>
scala> def foo = for {
         x <- 3.some
         y <- "!".some
       } yield x.shows + y
</scala>

ほぼ同じに見えるけど、Scala の `x.shows + y` は素の `String` で、`yield` が強制的にその値をコンテキストに入れている。これは生の値があればうまくいく。だけど、モナディックな値を返す関数があった場合はどうすればいいだろう?

<haskell>
in3 start = do
  first <- moveKnight start
  second <- moveKnight first
  moveKnight second
</haskell>

これは Scala では `moveKnight second` の値を抽出して `yield` で再包装せずには書くことができない。

<scala>
def in3: List[KnightPos] = for {
  first <- move
  second <- first.move
  third <- second.move
} yield third
</scala>

この違いにより問題が生じることは実際には無いかもしれないけど、一応覚えておいたほうがいいと思う。

### Writer? 中の人なんていません!

[Learn You a Haskell for Great Good](http://learnyouahaskell.com/for-a-few-monads-more) 曰く:

> Whereas the `Maybe` monad is for values with an added context of failure, and the list monad is for nondeterministic values, `Writer` monad is for values that have another value attached that acts as a sort of log value.

本に従って `applyLog` 関数を実装してみよう:

<scala>
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
</scala>

メソッドの注入が implicit のユースケースとしては多いため、Scala 2.10 に implicit class という糖衣構文が登場して、クラスから強化クラスに昇進させるのが簡単になった。ログを `Monoid` として一般化する:

<scala>
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
</scala>

### Writer

LYAHFGG:

> To attach a monoid to a value, we just need to put them together in a tuple. The `Writer w a` type is just a `newtype` wrapper for this.

Scalaz でこれに対応するのは [`Writer`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/package.scala) だ:

<scala>
type Writer[+W, +A] = WriterT[Id, W, A]
</scala>

`Writer[+W, +A]` は、`WriterT[Id, W, A]` の型エイリアスだ。

### WriterT

以下が [`WriterT`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/WriterT.scala) を単純化したものだ:

<scala>
sealed trait WriterT[F[+_], +W, +A] { self =>
  val run: F[(W, A)]

  def written(implicit F: Functor[F]): F[W] =
    F.map(run)(_._1)
  def value(implicit F: Functor[F]): F[A] =
    F.map(run)(_._2)
}
</scala>

Writer が実際にどうやって作られるのかは直ぐには分からなかったけど、見つけることができた:

<scala>
scala> 3.set("Smallish gang.")
res46: scalaz.Writer[String,Int] = scalaz.WriterTFunctions$$anon$26@477a0c05
</scala>

`import Scalaz._` によって全てのデータ型に対して以下の演算子が導入される:

<scala>
trait ToDataOps extends ToIdOps with ToTreeOps with ToWriterOps with ToValidationOps with ToReducerOps with ToKleisliOps
</scala>

件の演算子は [`WriterV`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/ToWriterOps.scala) の一部だ:

<scala>
trait WriterV[A] extends Ops[A] {
  def set[W](w: W): Writer[W, A] = WriterT.writer(w -> self)

  def tell: Writer[A, Unit] = WriterT.tell(self)
}
</scala>

上のメソッドは全ての型に注入されるため、以下のように Writer を作ることができる:

<scala>
scala> 3.set("something")
res57: scalaz.Writer[String,Int] = scalaz.WriterTFunctions$$anon$26@159663c3

scala> "something".tell
res58: scalaz.Writer[String,Unit] = scalaz.WriterTFunctions$$anon$26@374de9cf
</scala>

`return 3 :: Writer String Int` のように単位元が欲しい場合はどうすればいいだろう? `Monad[F[_]]` は型パラメータが 1つの型コンストラクタを期待するけど、`Writer[+W, +A]` は 2つある。Scalaz にある `MonadWriter` というヘルパー型を使うと簡単にモナドが得られる:

<scala>
scala> MonadWriter[Writer, String]
res62: scalaz.MonadWriter[scalaz.Writer,String] = scalaz.WriterTInstances$$anon$1@6b8501fa

scala> MonadWriter[Writer, String].point(3).run
res64: (String, Int) = ("",3)
</scala>

### Writer に for 構文を使う

LYAHFGG:

> Now that we have a `Monad` instance, we're free to use `do` notation for `Writer` values.

例題を Scala で実装してみよう:

<scala>
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
</scala>

### プログラムにログを追加する

以下が例題の `gcd` だ:

<scala>
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
</scala>

### 非効率な List の構築

LYAHFGG:

> When using the `Writer` monad, you have to be careful which monoid to use, because using lists can sometimes turn out to be very slow. That's because lists use `++` for `mappend` and using `++` to add something to the end of a list is slow if that list is really long.

[主なコレクションの性能特性をまとめた表](http://scalajp.github.com/scala-collections-doc-ja/collections_40.html)があるので見てみよう。不変コレクションで目立っているのが全ての演算を実質定数でこなす `Vector` だ。`Vector` は分岐度が 32 の木構造で、構造共有を行うことで高速な更新を実現している。

何故か Scalaz 7 は `Vector` の型クラスを `import Scalaz._` に含めていない。手動で import する:

<scala>
scala> import std.vector._
import std.vector._

scala> Monoid[Vector[String]]
res73: scalaz.Monoid[Vector[String]] = scalaz.std.IndexedSeqSubInstances$$anon$4@6f82f06f
</scala>

Vector を使った `gcd`:

<scala>
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
</scala>

### 性能の比較

本のように性能を比較するマイクロベンチマークを書いてみよう:

<scala>
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
</scala>

以下のように実行できる:

<scala>
scala> vectorFinalCountDown(10000).run
res18: (Vector[String], Unit) = (Vector(10000, 9999, 9998, 9997, 9996, 9995, 9994, 9993, 9992, 9991, 9990, 9989, 9988, 9987, 9986, 9985, 9984, ...

scala> res18._1.last
res19: String = 1206 msec

scala> listFinalCountDown(10000).run
res20: (List[String], Unit) = (List(10000, 9999, 9998, 9997, 9996, 9995, 9994, 9993, 9992, 9991, 9990, 9989, 9988, 9987, 9986, 9985, 9984, ...
scala> res20._1.last

res21: String = 2050 msec
</scala>

`List` に倍近くの時間がかかっているのが分かる。

### Reader

LYAHFGG:

> In the chapter about applicatives, we saw that the function type, `(->) r` is an instance of `Functor`.

<scala>
scala> val f = (_: Int) * 5
f: Int => Int = <function1>

scala> val g = (_: Int) + 3
g: Int => Int = <function1>

scala> (g map f)(8)
res22: Int = 55
</scala>

> We've also seen that functions are applicative functors. They allow us to operate on the eventual results of functions as if we already had their results. 

<scala>
scala> val f = ({(_: Int) * 2} |@| {(_: Int) + 10}) {_ + _}
warning: there were 1 deprecation warnings; re-run with -deprecation for details
f: Int => Int = <function1>

scala> f(3)
res35: Int = 19
</scala>

> Not only is the function type `(->) r a` functor and an applicative functor, but it's also a monad. Just like other monadic values that we've met so far, a function can also be considered a value with a context. The context for functions is that that value is not present yet and that we have to apply that function to something in order to get its result value.

この例題も実装してみよう:

<scala>
scala> val addStuff: Int => Int = for {
         a <- (_: Int) * 2
         b <- (_: Int) + 10
       } yield a + b
addStuff: Int => Int = <function1>

scala> addStuff(3)
res39: Int = 19
</scala>

> Both `(*2)` and `(+10)` get applied to the number `3` in this case. `return (a+b)` does as well, but it ignores it and always presents `a+b` as the result. For this reason, the function monad is also called the *reader* monad. All the functions read from a common source. 

要は、Reader モナドは値が既にあるかのようなフリをさせてくれる。恐らくこれは1つのパラメータを受け取る関数でしか使えないと予想している。`Option` や `List` モナドと違って、`Writer` も Reader モナドも標準ライブラリには入っていないし、便利そうだ。

続きはまたここから。
