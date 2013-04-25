
### Zipper

LYAHFGG:

> Zippers can be used with pretty much any data structure, so it's no surprise that they can be used to focus on sub-lists of lists.

Instead of a list zipper, Scalaz provides a zipper for `Stream`. Due to Haskell's laziness, it might actually make sense to think of Scala's `Stream` as Haskell's list. Here's [`Zipper`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Zipper.scala):

```scala
sealed trait Zipper[+A] {
  val focus: A
  val lefts: Stream[A]
  val rights: Stream[A]
  ...
}
```

To create a zipper use `toZipper` or `zipperEnd` method injected to `Stream`:

```scala
trait StreamOps[A] extends Ops[Stream[A]] {
  final def toZipper: Option[Zipper[A]] = s.toZipper(self)
  final def zipperEnd: Option[Zipper[A]] = s.zipperEnd(self)
  ...
}
```

Let's try using it.

```scala
scala> Stream(1, 2, 3, 4)
res23: scala.collection.immutable.Stream[Int] = Stream(1, ?)

scala> Stream(1, 2, 3, 4).toZipper
res24: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 1, <rights>))
```

As with `TreeLoc` there are lots of methods on `Zipper` to move around:

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

Here are these functions in action:

```scala
scala> Stream(1, 2, 3, 4).toZipper >>= {_.next}
res25: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 2, <rights>))

scala> Stream(1, 2, 3, 4).toZipper >>= {_.next} >>= {_.next}
res26: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 3, <rights>))

scala> Stream(1, 2, 3, 4).toZipper >>= {_.next} >>= {_.next} >>= {_.previous}
res27: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 2, <rights>))
```

To modify the current focus and bring it back to a `Stream`, use `modify` and `toStream` method:

```scala
scala> Stream(1, 2, 3, 4).toZipper >>= {_.next} >>= {_.next} >>= {_.modify {_ => 7}.some}
res31: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 7, <rights>))

scala> res31.get.toStream.toList
res32: List[Int] = List(1, 2, 7, 4)
```

We can also write this using `for` syntax:

```scala
scala> for {
         z <- Stream(1, 2, 3, 4).toZipper
         n1 <- z.next
         n2 <- n1.next
       } yield { n2.modify {_ => 7} }
res33: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 7, <rights>))
```

More readable, I guess, but it does take up lines so it's case by case.

This is pretty much the end of Learn You a Haskell for Great Good. It did not cover everything Scalaz has to offer, but I think it was an exellent way of gently getting introduced to the fundamentals. After looking up the corresponding Scalaz types for Haskell 
types, I am now comfortable enough to find my way around the source code and look things up as I go.
