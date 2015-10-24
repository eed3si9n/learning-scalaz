---
out: Iteratees.html
---

### Enumeration-Based I/O with Iteratees

There's another way of handling IOs called Iteratee that is talk of the town. There's [Scalaz Tutorial: Enumeration-Based I/O with Iteratees](http://blog.higher-order.com/blog/2010/10/14/scalaz-tutorial-enumeration-based-io-with-iteratees/) (EBIOI) by Rúnar on Scalaz 5 implementation, but a whole new Iteratee has been added to Scalaz 7.

I am going to read EBIOI first:

> Most programmers have come across the problem of treating an I/O data source (such as a file or a socket) as a data structure. This is a common thing to want to do.
> ...
> Instead of implementing an interface from which we request Strings by pulling, we’re going to give an implementation of an interface that can receive Strings by pushing. And indeed, this idea is nothing new. This is exactly what we do when we fold a list:

```scala
def foldLeft[B](b: B)(f: (B, A) => B): B
```

Let's look at Scalaz 7's interfaces. Here's [`Input`]($scalazBaseUrl$/iteratee/src/main/scala/scalaz/iteratee/Input.scala):

```scala
sealed trait Input[E] {
  def fold[Z](empty: => Z, el: (=> E) => Z, eof: => Z): Z
  def apply[Z](empty: => Z, el: (=> E) => Z, eof: => Z) =
    fold(empty, el, eof)
}
```

And here's [`IterateeT`]($scalazBaseUrl$/iteratee/src/main/scala/scalaz/iteratee/IterateeT.scala):

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

`IterateeT` seems to be a monad transformer.

EBIOI:

> Let’s see how we would use this to process a List. The following function takes a list and an iteratee and feeds the list’s elements to the iteratee.

We can skip this step, because `Iteratee` object extends `EnumeratorTFunctions`, which implements `enumerate` etc:

```scala
  def enumerate[E](as: Stream[E]): Enumerator[E] = ...
  def enumList[E, F[_] : Monad](xs: List[E]): EnumeratorT[E, F] = ...
  ...
```

This returns <a href="$scalazBaseUrl$/iteratee/src/main/scala/scalaz/iteratee/EnumeratorT.scala"><code>Enumerator[E]</code></a>, which is defined as follows:

```scala
trait EnumeratorT[E, F[_]] { self =>
  def apply[A]: StepT[E, F, A] => IterateeT[E, F, A]
  ...
}
type Enumerator[E] = EnumeratorT[E, Id]
```

Let's try implementing the counter example from EBIOI. For that we switch to `iteratee` project using sbt:

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

For common operation like this, Scalaz provides these folding functions under `Iteratee` object. But because it was written for `IterateeT` in mind, we need to supply `Id` monad as a type parameter:

```scala
scala> (length[Int, Id] &= enumerate(Stream(1, 2, 3))).run
res1: scalaz.Scalaz.Id[Int] = 3
```

I'll just copy the `drop` and `head` from [`IterateeTFunctions`]($scalazBaseUrl$/iteratee/src/main/scala/scalaz/iteratee/IterateeT.scala):

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

### Composing Iteratees

EBIOI:

> In other words, iteratees compose sequentially.

Here's `drop1keep1` using Scalaz 7:

```scala
scala> def drop1Keep1[E]: Iteratee[E, Option[E]] = for {
         _ <- drop[E, Id](1)
         x <- head[E, Id]
       } yield x
drop1Keep1: [E]=> scalaz.iteratee.package.Iteratee[E,Option[E]]
```

There's now `repeatBuild` function that can accumulate to a given monoid, so we can write Stream version of `alternates` example as follows:

```scala
scala> def alternates[E]: Iteratee[E, Stream[E]] =
         repeatBuild[E, Option[E], Stream](drop1Keep1) map {_.flatten}
alternates: [E](n: Int)scalaz.iteratee.package.Iteratee[E,Stream[E]]

scala> (alternates[Int] &= enumerate(Stream.range(1, 15))).run.force
res7: scala.collection.immutable.Stream[Int] = Stream(2, 4, 6, 8, 10, 12, 14)
```

### File Input With Iteratees

EBIOI:

> Using the iteratees to read from file input turns out to be incredibly easy.

To process `java.io.Reader` Scalaz 7 comes with `Iteratee.enumReader[F[_]](r: => java.io.Reader)` function. This is when it starts to make sense why `Iteratee` was implemented as `IterateeT` because we can just stick `IO` into it:

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

To get the first character, we can run `head[Char, IO]` as follows:

```scala
scala> (head[IoExceptionOr[Char], IO] &= res0).map(_ flatMap {_.toOption}).run.unsafePerformIO
res1: Option[Char] = Some(#)
```

EBIOI:

> We can get the number of lines in two files combined, by composing two enumerations and using our “counter” iteratee from above.

Let's try this out.

```scala
scala> def lengthOfTwoFiles(f1: File, f2: File) = {
         val l1 = length[IoExceptionOr[Char], IO] &= enumReader[IO](new BufferedReader(new FileReader(f1)))
         val l2 = l1 &= enumReader[IO](new BufferedReader(new FileReader(f2)))
         l2.run
       }

scala> lengthOfTwoFiles(new File("./README.md"), new File("./TODO.txt")).unsafePerformIO
res65: Int = 12731
```

There are some more interesting examples in [`IterateeUsage.scala`]($scalazBaseUrl$/example/src/main/scala/scalaz/example/IterateeUsage.scala):

```scala
scala> val readLn = takeWhile[Char, List](_ != '\n') flatMap (ln => drop[Char, Id](1).map(_ => ln))
readLn: scalaz.iteratee.IterateeT[Char,scalaz.Id.Id,List[Char]] = scalaz.iteratee.IterateeTFunctions\$\$anon\$9@560ff23d

scala> (readLn &= enumStream("Iteratees\nare\ncomposable".toStream)).run
res67: scalaz.Id.Id[List[Char]] = List(I, t, e, r, a, t, e, e, s)

scala> (collect[List[Char], List] %= readLn.sequenceI &= enumStream("Iteratees\nare\ncomposable".toStream)).run
res68: scalaz.Id.Id[List[List[Char]]] = List(List(I, t, e, r, a, t, e, e, s), List(a, r, e), List(c, o, m, p, o, s, a, b, l, e))
```

In the above `sequenceI` method turns `readLn` into an `EnumerateeT`, and `%=` is able to chain it to an iteratee.

EBIOI:

> So what we have here is a uniform and compositional interface for enumerating both pure and effectful data sources.

It might take a while for this one to sink in.

### Links

- [Scalaz Tutorial: Enumeration-Based I/O with Iteratees](http://apocalisp.wordpress.com/2010/10/17/scalaz-tutorial-enumeration-based-io-with-iteratees/)
- [Iteratees](http://jsuereth.com/scala/2012/02/29/iteratees.html). This is [Josh Suereth (@jsuereth)](http://twitter.com/jsuereth)'s take on Iteratees.
- [Enumerator and iteratee](http://www.haskell.org/haskellwiki/Enumerator_and_iteratee) from Haskell wiki.
