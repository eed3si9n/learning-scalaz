---
out: Writer.html
---

### Writer? I hardly knew her!

[Learn You a Haskell for Great Good](http://learnyouahaskell.com/for-a-few-monads-more) says:

> Whereas the `Maybe` monad is for values with an added context of failure, and the list monad is for nondeterministic values, `Writer` monad is for values that have another value attached that acts as a sort of log value.

Let's follow the book and implement `applyLog` function:

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

Since method injection is a common use case for implicits, Scala 2.10 adds a syntax sugar called implicit class to make the promotion from a class to an enriched class easier. Here's how we can generalize the log to a `Monoid`:

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

> To attach a monoid to a value, we just need to put them together in a tuple. The `Writer w a` type is just a `newtype` wrapper for this.

In Scalaz, the equivalent is called [`Writer`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/package.scala):

```scala
type Writer[+W, +A] = WriterT[Id, W, A]
```

`Writer[+W, +A]` is a type alias for `WriterT[Id, W, A]`.

### WriterT

Here's the simplified version of [`WriterT`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/WriterT.scala):

```scala
sealed trait WriterT[F[+_], +W, +A] { self =>
  val run: F[(W, A)]

  def written(implicit F: Functor[F]): F[W] =
    F.map(run)(_._1)
  def value(implicit F: Functor[F]): F[A] =
    F.map(run)(_._2)
}
```

It wasn't immediately obvious to me how a writer is actually created at first, but eventually figured it out:

```scala
scala> 3.set("Smallish gang.")
res46: scalaz.Writer[String,Int] = scalaz.WriterTFunctions$$anon$26@477a0c05
```

The following operators are supported by all data types enabled by `import Scalaz._`:

```scala
trait ToDataOps extends ToIdOps with ToTreeOps with ToWriterOps with ToValidationOps with ToReducerOps with ToKleisliOps
```

The operator in question is part of [`WriterV`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/ToWriterOps.scala):

```scala
trait WriterV[A] extends Ops[A] {
  def set[W](w: W): Writer[W, A] = WriterT.writer(w -> self)

  def tell: Writer[A, Unit] = WriterT.tell(self)
}
```

The above methods are injected to all types so we can use them to create Writers:

```scala
scala> 3.set("something")
res57: scalaz.Writer[String,Int] = scalaz.WriterTFunctions$$anon$26@159663c3

scala> "something".tell
res58: scalaz.Writer[String,Unit] = scalaz.WriterTFunctions$$anon$26@374de9cf
```

What if we want to get the identity value like `return 3 :: Writer String Int`? `Monad[F[_]]` expects a type constructor with one parameter, but `Writer[+W, +A]` takes two. There's a helper type in Scalaz called `MonadWriter` to help us out:

```scala
scala> MonadWriter[Writer, String]
res62: scalaz.MonadWriter[scalaz.Writer,String] = scalaz.WriterTInstances$$anon$1@6b8501fa

scala> MonadWriter[Writer, String].point(3).run
res64: (String, Int) = ("",3)
```

### Using for syntax with Writer

LYAHFGG:

> Now that we have a `Monad` instance, we're free to use `do` notation for `Writer` values.


Let's implement the example in Scala:

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

### Adding logging to program

Here's the `gcd` example:

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

### Inefficient List construction

LYAHFGG:

> When using the `Writer` monad, you have to be careful which monoid to use, because using lists can sometimes turn out to be very slow. That's because lists use `++` for `mappend` and using `++` to add something to the end of a list is slow if that list is really long.

Here's [the table of performance characteristics for major collections](http://docs.scala-lang.org/overviews/collections/performance-characteristics.html). What stands out for immutable collection is `Vector` since it has effective constant for all operations. `Vector` is a tree structure with the branching factor of 32, and it's able to achieve fast updates by structure sharing.

For whatever reason, Scalaz 7 does not enable typeclasses for `Vector`s using `import Scalaz._`. So let's import it manually:

```scala
scala> import std.vector._
import std.vector._

scala> Monoid[Vector[String]]
res73: scalaz.Monoid[Vector[String]] = scalaz.std.IndexedSeqSubInstances$$anon$4@6f82f06f
```

Here's the vector version of `gcd`:

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

### Comparing performance

Like the book let's write a microbenchmark to compare the performance:

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

We can now run this as follows:

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

As you can see `List` is taking almost double the time.
