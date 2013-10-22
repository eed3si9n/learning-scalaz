
### Zipper

LYAHFGG:

> ジッパーは、ほぼどんなデータ型に対しても作れるので、リストと部分リストに対して作れるといっても不思議ではないでしょう。

リストの Zipper のかわりに、Scalaz は `Stream` 向けのものを提供する。Haskell の遅延評価のため、Scala の `Stream` を Haskell のリストを考えるのは理にかなっているのかもしれない。これが [`Zipper`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Zipper.scala) だ:

```scala
sealed trait Zipper[+A] {
  val focus: A
  val lefts: Stream[A]
  val rights: Stream[A]
  ...
}
```

Zipper を作るには `Stream` に注入された `toZipper` か `zipperEnd` メソッドを使う:

```scala
trait StreamOps[A] extends Ops[Stream[A]] {
  final def toZipper: Option[Zipper[A]] = s.toZipper(self)
  final def zipperEnd: Option[Zipper[A]] = s.zipperEnd(self)
  ...
}
```

使ってみる:

```scala
scala> Stream(1, 2, 3, 4)
res23: scala.collection.immutable.Stream[Int] = Stream(1, ?)

scala> Stream(1, 2, 3, 4).toZipper
res24: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 1, <rights>))
```

`TreeLoc` 同様に `Zipper` にも移動のために多くのメソッドが用意されてある:

```scala
sealed trait Zipper[+A] {
  ...
  /** Possibly moves to next element to the right of focus. */
  def next: Option[Zipper[A]] = ...
  def nextOr[AA >: A](z: => Zipper[AA]): Zipper[AA] = next getOrElse z
  def tryNext: Zipper[A] = nextOr(sys.error("cannot move to next element"))
  /** Possibly moves to the previous element to the left of focus. */
  def previous: Option[Zipper[A]] = ...
  def previousOr[AA >: A](z: => Zipper[AA]): Zipper[AA] = previous getOrElse z
  def tryPrevious: Zipper[A] = previousOr(sys.error("cannot move to previous element"))
  /** Moves focus n elements in the zipper, or None if there is no such element. */
  def move(n: Int): Option[Zipper[A]] = ...
  def findNext(p: A => Boolean): Option[Zipper[A]] = ...
  def findPrevious(p: A => Boolean): Option[Zipper[A]] = ...
  
  def modify[AA >: A](f: A => AA) = ...
  def toStream: Stream[A] = ...
  ...
}
```

使ってみるとこうなる:

```scala
scala> Stream(1, 2, 3, 4).toZipper >>= {_.next}
res25: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 2, <rights>))

scala> Stream(1, 2, 3, 4).toZipper >>= {_.next} >>= {_.next}
res26: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 3, <rights>))

scala> Stream(1, 2, 3, 4).toZipper >>= {_.next} >>= {_.next} >>= {_.previous}
res27: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 2, <rights>))
```

現在のフォーカスを変更して `Stream` に戻すには `modify` と `toStream` メソッドを使う:

```scala
scala> Stream(1, 2, 3, 4).toZipper >>= {_.next} >>= {_.next} >>= {_.modify {_ => 7}.some}
res31: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 7, <rights>))

scala> res31.get.toStream.toList
res32: List[Int] = List(1, 2, 7, 4)
```

これは `for` 構文を使って書くこともできる:

```scala
scala> for {
         z <- Stream(1, 2, 3, 4).toZipper
         n1 <- z.next
         n2 <- n1.next
       } yield { n2.modify {_ => 7} }
res33: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 7, <rights>))
```

読みやすいとは思うけど、何行も使うから場合によりけりだと思う。

すごいHaskellたのしく学ぼう (Learn You a Haskell for Great Good) はひとまずこれでおしまい。Scalaz が提供する全てのものはカバーしなかったけど、基礎からゆっくりと入っていくのにとても良い本だったと思う。Haskell に対応する型を探しているうちに Scalaz のソースを読む勘がついてきたので、あとは色々調べながらでもいけそうだ。

とりあえず、紹介する機会を逸した型クラスがいくつかあるので紹介したい。
