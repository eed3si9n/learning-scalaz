  [day16]: http://eed3si9n.com/learning-scalaz-day16

<div class="floatingimage">
<img src="http://eed3si9n.com/images/openphoto-6987bw.jpg">
<div class="credit">Daniel Steger for openphoto.net</div>
</div>

[Yesterday][day16] we looked at `Memo` for caching computation results, and `ST` as a way of encapsulating mutation. Today we'll continue into IO.

### IO Monad

Instead of reading the second half of the paper, we can get the gist by reading [Towards an Effect System in Scala, Part 2: IO Monad](http://apocalisp.wordpress.com/2011/12/19/towards-an-effect-system-in-scala-part-2-io-monad/) by [Rúnar (@runarorama)](http://twitter.com/runarorama):

> While ST gives us guarantees that mutable memory is never shared, it says nothing about reading/writing files, throwing exceptions, opening network sockets, database connections, etc.

Here's the typeclass contract for [`ST`](https://github.com/scalaz/scalaz/blob/scalaz-seven/effect/src/main/scala/scalaz/effect/ST.scala) again:

<scala>
sealed trait ST[S, A] {
  private[effect] def apply(s: World[S]): (World[S], A)
}
</scala>

And the following is the typeclass contract of `IO`:

<scala>
sealed trait IO[+A] {
  private[effect] def apply(rw: World[RealWorld]): Trampoline[(World[RealWorld], A)]
}
</scala>

If we ignore the `Trampoline` part, `IO` is like `ST` with state fixed to `RealWorld`. Similar to `ST`, we can create `IO` monads using the functions under `IO` object. Here's Hello world.

<scala>
scala> import scalaz._, Scalaz._, effect._, IO._
import scalaz._
import Scalaz._
import effect._
import IO._

scala> val action1 = for {
         _ <- putStrLn("Hello, world!")
       } yield ()
action1: scalaz.effect.IO[Unit] = scalaz.effect.IOFunctions$$anon$4@149f6f65

scala> action1.unsafePerformIO
Hello, world!

</scala>

Here are the IO actions under `IO`:

<scala>
  /** Reads a character from standard input. */
  def getChar: IO[Char] = ...
  /** Writes a character to standard output. */
  def putChar(c: Char): IO[Unit] = ...
  /** Writes a string to standard output. */
  def putStr(s: String): IO[Unit] = ...
  /** Writes a string to standard output, followed by a newline.*/
  def putStrLn(s: String): IO[Unit] = ...
  /** Reads a line of standard input. */
  def readLn: IO[String] = ...
  /** Write the given value to standard output. */
  def putOut[A](a: A): IO[Unit] = ...
  // Mutable variables in the IO monad
  def newIORef[A](a: => A): IO[IORef[A]] = ...
  /**Throw the given error in the IO monad. */
  def throwIO[A](e: Throwable): IO[A] = ...
  /** An IO action that does nothing. */
  val ioUnit: IO[Unit] = ...
}
</scala>

We can also make our own action using the `apply` method under `IO` object as follows:

<scala>
scala> val action2 = IO {
         val source = scala.io.Source.fromFile("./README.md")
         source.getLines.toStream
       }
action2: scalaz.effect.IO[scala.collection.immutable.Stream[String]] = scalaz.effect.IOFunctions$$anon$4@bab4387

scala> action2.unsafePerformIO.toList
res57: List[String] = List(# Scalaz, "", Scalaz is a Scala library for functional programming., "", It provides purely functional data structures to complement those from the Scala standard library., ...
</scala>

TESS2:

> Composing these into programs is done monadically. So we can use `for`-comprehensions. Here’s a program that reads a line of input and prints it out again:

<scala>
def program: IO[Unit] = for {
  line <- readLn
  _    <- putStrLn(line)
} yield ()
</scala>


> `IO[Unit]` is an instance of `Monoid`, so we can re-use the monoid addition function `|+|`.

Let's try this out:

<scala>
scala> (program |+| program).unsafePerformIO
123
123

</scala>

### Enumeration-Based I/O with Iteratees

There's another way of handling IOs called Iteratee that is talk of the town. There's [Scalaz Tutorial: Enumeration-Based I/O with Iteratees](http://apocalisp.wordpress.com/2010/10/17/scalaz-tutorial-enumeration-based-io-with-iteratees/) (EBIOI) by Rúnar on Scalaz 5 implementation, but a whole new Iteratee has been added to Scalaz 7.

I am going to read EBIOI first:

> Most programmers have come across the problem of treating an I/O data source (such as a file or a socket) as a data structure. This is a common thing to want to do.
> ...
> Instead of implementing an interface from which we request Strings by pulling, we’re going to give an implementation of an interface that can receive Strings by pushing. And indeed, this idea is nothing new. This is exactly what we do when we fold a list:

<scala>
def foldLeft[B](b: B)(f: (B, A) => B): B
</scala>

Let's look at Scalaz 7's interfaces. Here's [`Input`](https://github.com/scalaz/scalaz/blob/scalaz-seven/iteratee/src/main/scala/scalaz/iteratee/Input.scala):

<scala>
sealed trait Input[E] {
  def fold[Z](empty: => Z, el: (=> E) => Z, eof: => Z): Z
  def apply[Z](empty: => Z, el: (=> E) => Z, eof: => Z) =
    fold(empty, el, eof)
}
</scala>

And here's [`IterateeT`](https://github.com/scalaz/scalaz/blob/scalaz-seven/iteratee/src/main/scala/scalaz/iteratee/IterateeT.scala):

<scala>
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
</scala>

`IterateeT` seems to be a monad transformer.

EBIOI:

> Let’s see how we would use this to process a List. The following function takes a list and an iteratee and feeds the list’s elements to the iteratee.

We can skip this step, because `Iteratee` object extends `EnumeratorTFunctions`, which implements `enumerate` etc:

<scala>
  def enumerate[E](as: Stream[E]): Enumerator[E] = ...
  def enumList[E, F[_] : Monad](xs: List[E]): EnumeratorT[E, F] = ...
  ...
</scala>

This returns [`Enumerator[E]`](https://github.com/scalaz/scalaz/blob/scalaz-seven/iteratee/src/main/scala/scalaz/iteratee/EnumeratorT.scala), which is defined as follows:

<scala>
trait EnumeratorT[E, F[_]] { self =>
  def apply[A]: StepT[E, F, A] => IterateeT[E, F, A]
  ...
}
type Enumerator[E] = EnumeratorT[E, Id]
</scala>

Let's try implementing the counter example from EBIOI. For that we switch to `iteratee` project using sbt:

<scala>
$ sbt
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
</scala>

For common operation like this, Scalaz provides these folding functions under `Iteratee` object. But because it was written for `IterateeT` in mind, we need to supply `Id` monad as a type parameter:

<scala>
scala> (length[Int, Id] &= enumerate(Stream(1, 2, 3))).run
res1: scalaz.Scalaz.Id[Int] = 3
</scala>

I'll just copy the `drop` and `head` from [`IterateeTFunctions`](https://github.com/scalaz/scalaz/blob/scalaz-seven/iteratee/src/main/scala/scalaz/iteratee/IterateeT.scala):

<scala>
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
</scala>

### Composing Iteratees

EBIOI:

> In other words, iteratees compose sequentially.

Here's `drop1keep1` using Scalaz 7:

<scala>
scala> def drop1Keep1[E]: Iteratee[E, Option[E]] = for {
         _ <- drop[E, Id](1)
         x <- head[E, Id]
       } yield x
drop1Keep1: [E]=> scalaz.iteratee.package.Iteratee[E,Option[E]]
</scala>

There's now `repeatBuild` function that can accumulate to a given monoid, so we can write Stream version of `alternates` example as follows:

<scala>
scala> def alternates[E]: Iteratee[E, Stream[E]] =
         repeatBuild[E, Option[E], Stream](drop1Keep1) map {_.flatten}
alternates: [E](n: Int)scalaz.iteratee.package.Iteratee[E,Stream[E]]

scala> (alternates[Int] &= enumerate(Stream.range(1, 15))).run.force
res7: scala.collection.immutable.Stream[Int] = Stream(2, 4, 6, 8, 10, 12, 14)
</scala>

### File Input With Iteratees

EBIOI:

> Using the iteratees to read from file input turns out to be incredibly easy. 

To process `java.io.Reader` Scalaz 7 comes with `Iteratee.enumReader[F[_]](r: => java.io.Reader)` function. This is when it starts to make sense why `Iteratee` was implemented as `IterateeT` because we can just stick `IO` into it:

<scala>
scala> import scalaz._, Scalaz._, iteratee._, Iteratee._, effect._
import scalaz._
import Scalaz._
import iteratee._
import Iteratee._
import effect._

scala> import java.io._
import java.io._

scala> enumReader[IO](new BufferedReader(new FileReader("./README.md")))
res0: scalaz.iteratee.EnumeratorT[scalaz.effect.IoExceptionOr[Char],scalaz.effect.IO] = scalaz.iteratee.EnumeratorTFunctions$$anon$14@548ace66
</scala>

To get the first character, we can run `head[Char, IO]` as follows:

<scala>
scala> (head[IoExceptionOr[Char], IO] &= res0).map(_ flatMap {_.toOption}).run.unsafePerformIO
res1: Option[Char] = Some(#)
</scala>

EBIOI:

> We can get the number of lines in two files combined, by composing two enumerations and using our “counter” iteratee from above.

Let's try this out.

<scala>
scala> def lengthOfTwoFiles(f1: File, f2: File) = {
         val l1 = length[IoExceptionOr[Char], IO] &= enumReader[IO](new BufferedReader(new FileReader(f1)))
         val l2 = l1 &= enumReader[IO](new BufferedReader(new FileReader(f2)))
         l2.run
       }

scala> lengthOfTwoFiles(new File("./README.md"), new File("./TODO.txt")).unsafePerformIO
res65: Int = 12731
</scala>

There are some more interesting examples in [`IterateeUsage.scala`](https://github.com/scalaz/scalaz/blob/scalaz-seven/example/src/main/scala/scalaz/example/IterateeUsage.scala):

<scala>
scala> val readLn = takeWhile[Char, List](_ != '\n') flatMap (ln => drop[Char, Id](1).map(_ => ln))
readLn: scalaz.iteratee.IterateeT[Char,scalaz.Id.Id,List[Char]] = scalaz.iteratee.IterateeTFunctions$$anon$9@560ff23d

scala> (readLn &= enumStream("Iteratees\nare\ncomposable".toStream)).run
res67: scalaz.Id.Id[List[Char]] = List(I, t, e, r, a, t, e, e, s)

scala> (collect[List[Char], List] %= readLn.sequenceI &= enumStream("Iteratees\nare\ncomposable".toStream)).run
res68: scalaz.Id.Id[List[List[Char]]] = List(List(I, t, e, r, a, t, e, e, s), List(a, r, e), List(c, o, m, p, o, s, a, b, l, e))
</scala>

In the above `sequenceI` method turns `readLn` into an `EnumerateeT`, and `%=` is able to chain it to an iteratee.

EBIOI:

> So what we have here is a uniform and compositional interface for enumerating both pure and effectful data sources.

It might take a while for this one to sink in.

### Links

- [Scalaz Tutorial: Enumeration-Based I/O with Iteratees](http://apocalisp.wordpress.com/2010/10/17/scalaz-tutorial-enumeration-based-io-with-iteratees/)
- [Iteratees](http://jsuereth.com/scala/2012/02/29/iteratees.html). This is [Josh Suereth (@jsuereth)](http://twitter.com/jsuereth)'s take on Iteratees.
- [Enumerator and iteratee](http://www.haskell.org/haskellwiki/Enumerator_and_iteratee) from Haskell wiki.

### Thanks for reading

This is going to be the last day of learning Scalaz series. There are still some useful details I was not able to cover, but I think I was able to cover the main parts. Thanks for the comments and retweets!

Kudos to Miran Lipovača for writing [Learn You a Haskell for Great Good!](http://learnyouahaskell.com/). It really helped to have the book as a guide with many examples. 

And of course, the authors and contributors of Scalaz deserve some shoutout! The following list is from [`build.scala`](https://github.com/scalaz/scalaz/blob/scalaz-seven/project/build.scala):

<scala>
Seq(
  ("runarorama", "Runar Bjarnason"),
  ("pchiusano", "Paul Chiusano"),
  ("tonymorris", "Tony Morris"),
  ("retronym", "Jason Zaugg"),
  ("ekmett", "Edward Kmett"),
  ("alexeyr", "Alexey Romanov"),
  ("copumpkin", "Daniel Peebles"),
  ("rwallace", "Richard Wallace"),
  ("nuttycom", "Kris Nuttycombe"),
  ("larsrh", "Lars Hupel")
).map {
  case (id, name) =>
    <developer>
      <id>{id}</id>
      <name>{name}</name>
      <url>http://github.com/{id}</url>
    </developer>
}
</scala>

It was fun learning functional programming through Scalaz, and I hope the learning continues. Oh yea, don't forget the [Scalaz cheat sheet](http://eed3si9n.com/scalaz-cheat-sheet) too.
