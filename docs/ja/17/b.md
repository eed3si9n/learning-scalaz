---
out: Iteratees.html
---

### Enumeration-Based I/O with Iteratees

IO を処理するもう 1つの方法に Iteratee と呼ばれるものがあり、最近にわかに注目を浴びている。Scalaz 5 の実装を Rúnar さんが解説した [Scalaz Tutorial: Enumeration-Based I/O with Iteratees](http://apocalisp.wordpress.com/2010/10/17/scalaz-tutorial-enumeration-based-io-with-iteratees/) (EBIOI) があるけど、Scalaz 7 には新しい Iteratee が加わった。

とりあえず EBIOI を読んでみる:

> Most programmers have come across the problem of treating an I/O data source (such as a file or a socket) as a data structure. This is a common thing to want to do.
> ...
> Instead of implementing an interface from which we request Strings by pulling, we’re going to give an implementation of an interface that can receive Strings by pushing. And indeed, this idea is nothing new. This is exactly what we do when we fold a list:

```scala
def foldLeft[B](b: B)(f: (B, A) => B): B
```

Scalaz 7 のインターフェイスをみてみよう。以下が [`Input`]($scalazBaseUrl$/iteratee/src/main/scala/scalaz/iteratee/Input.scala) だ:

```scala
sealed trait Input[E] {
  def fold[Z](empty: => Z, el: (=> E) => Z, eof: => Z): Z
  def apply[Z](empty: => Z, el: (=> E) => Z, eof: => Z) =
    fold(empty, el, eof)
}
```

そしてこれが [`IterateeT`]($scalazBaseUrl$/iteratee/src/main/scala/scalaz/iteratee/IterateeT.scala):

```scala
sealed trait IterateeT[E, F[_], A] {
  def value: F[StepT[E, F, A]]
}
type Iteratee[E, A] = IterateeT[E, Id, A]

object Iteratee
  extends IterateeFunctions
  with IterateeTFunctions
  with EnumeratorTFunctions
  with EnumeratorPFunctions
  with EnumerateeTFunctions
  with StepTFunctions
  with InputFunctions {

  def apply[E, A](s: Step[E, A]): Iteratee[E, A] = iteratee(s)
}

type >@>[E, A] = Iteratee[E, A]
```

`IterateeT` はモナド変換子みたいだ。

EBIOI:

> Let’s see how we would use this to process a List. The following function takes a list and an iteratee and feeds the list’s elements to the iteratee.

`Iteratee` object は `enumerate` その他を実装する `EnumeratorTFunctions` を継承するため、このステップは飛ばすことができる:

```scala
  def enumerate[E](as: Stream[E]): Enumerator[E] = ...
  def enumList[E, F[_] : Monad](xs: List[E]): EnumeratorT[E, F] = ...
  ...
```

これは以下のように定義された <a href="$scalazBaseUrl$/iteratee/src/main/scala/scalaz/iteratee/EnumeratorT.scala"><code>Enumerator[E]</code></a> を返す:

```scala
trait EnumeratorT[E, F[_]] { self =>
  def apply[A]: StepT[E, F, A] => IterateeT[E, F, A]
  ...
}
type Enumerator[E] = EnumeratorT[E, Id]
```

EBIOI のカウンターの例題を実装してみよう。sbt から `iteratee` プロジェクトに切り替える:

```scala
\$ sbt
scalaz> project iteratee
scalaz-iteratee> console
[info] Starting scala interpreter...

scala> import scalaz._, Scalaz._, iteratee._, Iteratee._
import scalaz._
import Scalaz._
import iteratee._
import Iteratee._

scala> def counter[E]: Iteratee[E, Int] = {
         def step(acc: Int)(s: Input[E]): Iteratee[E, Int] =
           s(el = e => cont(step(acc + 1)),
             empty = cont(step(acc)),
             eof = done(acc, eofInput[E])
           )
         cont(step(0))
       }
counter: [E]=> scalaz.iteratee.package.Iteratee[E,Int]

scala> (counter[Int] &= enumerate(Stream(1, 2, 3))).run
res0: scalaz.Id.Id[Int] = 3
```

このようなよく使われる演算は `Iteratee` object 下に畳み込み関数として用意されてある。だけど、`IterateeT` を念頭に書かれているので、`Id` モナドを型パラメータとして渡してやる必要がある:

```scala
scala> (length[Int, Id] &= enumerate(Stream(1, 2, 3))).run
res1: scalaz.Scalaz.Id[Int] = 3
```

`drop` と `head` は [`IterateeTFunctions`]($scalazBaseUrl$/iteratee/src/main/scala/scalaz/iteratee/IterateeT.scala) の実装をみてみる:

```scala
  /**An iteratee that skips the first n elements of the input **/
  def drop[E, F[_] : Pointed](n: Int): IterateeT[E, F, Unit] = {
    def step(s: Input[E]): IterateeT[E, F, Unit] =
      s(el = _ => drop(n - 1),
        empty = cont(step),
        eof = done((), eofInput[E]))
    if (n == 0) done((), emptyInput[E])
    else cont(step)
  }

  /**An iteratee that consumes the head of the input **/
  def head[E, F[_] : Pointed]: IterateeT[E, F, Option[E]] = {
    def step(s: Input[E]): IterateeT[E, F, Option[E]] =
      s(el = e => done(Some(e), emptyInput[E]),
        empty = cont(step),
        eof = done(None, eofInput[E])
      )
    cont(step)
  }
```

### Iteratee の合成

EBIOI:

> In other words, iteratees compose sequentially.

以下が Scalaz 7 を使った `drop1keep1` だ:

```scala
scala> def drop1Keep1[E]: Iteratee[E, Option[E]] = for {
         _ <- drop[E, Id](1)
         x <- head[E, Id]
       } yield x
drop1Keep1: [E]=> scalaz.iteratee.package.Iteratee[E,Option[E]]
```

渡された Monoid に累積する `repeatBuild` という関数があるので、`alternates` の Stream 版は以下のように書ける:

```scala
scala> def alternates[E]: Iteratee[E, Stream[E]] =
         repeatBuild[E, Option[E], Stream](drop1Keep1) map {_.flatten}
alternates: [E](n: Int)scalaz.iteratee.package.Iteratee[E,Stream[E]]

scala> (alternates[Int] &= enumerate(Stream.range(1, 15))).run.force
res7: scala.collection.immutable.Stream[Int] = Stream(2, 4, 6, 8, 10, 12, 14)
```

### Iteratees を用いたファイル入力

EBIOI:

> Using the iteratees to read from file input turns out to be incredibly easy. 

`java.io.Reader` を処理するために Scalaz 7 には `Iteratee.enumReader[F[_]](r: => java.io.Reader)` 関数がついてくる。これで何故 `Iteratee` が `IterateeT` として実装されたのかという謎が解けた。そのまま `IO` を突っ込めるからだ:

```scala
scala> import scalaz._, Scalaz._, iteratee._, Iteratee._, effect._
import scalaz._
import Scalaz._
import iteratee._
import Iteratee._
import effect._

scala> import java.io._
import java.io._

scala> enumReader[IO](new BufferedReader(new FileReader("./README.md")))
res0: scalaz.iteratee.EnumeratorT[scalaz.effect.IoExceptionOr[Char],scalaz.effect.IO] = scalaz.iteratee.EnumeratorTFunctions\$\$anon\$14@548ace66
```

最初の文字を得るには、以下のように `head[IoExceptionOr[Char], IO]` を実行する:

```scala
scala> (head[IoExceptionOr[Char], IO] &= res0).map(_ flatMap {_.toOption}).run.unsafePerformIO
res1: Option[Char] = Some(#)
```

EBIOI:

> We can get the number of lines in two files combined, by composing two enumerations and using our “counter” iteratee from above.

これも試してみよう:

```scala
scala> def lengthOfTwoFiles(f1: File, f2: File) = {
         val l1 = length[IoExceptionOr[Char], IO] &= enumReader[IO](new BufferedReader(new FileReader(f1)))
         val l2 = l1 &= enumReader[IO](new BufferedReader(new FileReader(f2)))
         l2.run
       }

scala> lengthOfTwoFiles(new File("./README.md"), new File("./TODO.txt")).unsafePerformIO
res65: Int = 12731
```

[`IterateeUsage.scala`]($scalazBaseUrl$/example/src/main/scala/scalaz/example/IterateeUsage.scala) には他にも面白そうな例がある:

```scala
scala> val readLn = takeWhile[Char, List](_ != '\n') flatMap (ln => drop[Char, Id](1).map(_ => ln))
readLn: scalaz.iteratee.IterateeT[Char,scalaz.Id.Id,List[Char]] = scalaz.iteratee.IterateeTFunctions\$\$anon\$9@560ff23d

scala> (readLn &= enumStream("Iteratees\nare\ncomposable".toStream)).run
res67: scalaz.Id.Id[List[Char]] = List(I, t, e, r, a, t, e, e, s)

scala> (collect[List[Char], List] %= readLn.sequenceI &= enumStream("Iteratees\nare\ncomposable".toStream)).run
res68: scalaz.Id.Id[List[List[Char]]] = List(List(I, t, e, r, a, t, e, e, s), List(a, r, e), List(c, o, m, p, o, s, a, b, l, e))
```

上では `sequenceI` メソッドは `readLn` を `EnumerateeT` に変換して、`%=` はそれを Iteratee にチェインしている。

EBIOI:

> So what we have here is a uniform and compositional interface for enumerating both pure and effectful data sources.

この文の意義を実感するにはもう少し時間がかかりそうだ。

### リンク

- [Scalaz Tutorial: Enumeration-Based I/O with Iteratees](http://apocalisp.wordpress.com/2010/10/17/scalaz-tutorial-enumeration-based-io-with-iteratees/)
- [Iteratees](http://jsuereth.com/scala/2012/02/29/iteratees.html)。これは [Josh Suereth さん (@jsuereth)](http://twitter.com/jsuereth) による Iteratee。
- Haskell wiki の [Enumerator and iteratee](http://www.haskell.org/haskellwiki/Enumerator_and_iteratee)。
