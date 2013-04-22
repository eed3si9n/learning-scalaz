  [day5]: http://eed3si9n.com/learning-scalaz-day5

[Yesterday][day5] we looked at `Monad` typeclass, which introduces `flatMap`. We looked at how monadic chaining can add contexts to values. Because both `Option` and `List` already have `flatMap` in the standard library, it was more about changing the way we see things rather than introducing new code. We also reviewed `for` syntax as a way of chaining monadic operations.

### for syntax again

There's a subtle difference in Haskell's `do` notation and Scala's `for` syntax. Here's an example of `do` notation:

<haskell>
foo = do
  x <- Just 3
  y <- Just "!"
  Just (show x ++ y)
</haskell>

Typically one would write `return (show x ++ y)`, but I wrote out `Just`, so it's clear that the last line is a monadic value. On the other hand, Scala would look as follows:

<scala>
scala> def foo = for {
         x <- 3.some
         y <- "!".some
       } yield x.shows + y
</scala>

Looks almost the same, but in Scala `x.shows + y` is plain `String`, and `yield` forces the value to get in the context. This is great if we have the raw value. But what if there's a function that returns monadic value? 

<haskell>
in3 start = do
  first <- moveKnight start
  second <- moveKnight first
  moveKnight second
</haskell>

We can't write this in Scala without extract the value from `moveKnight second` and re-wrapping it using yeild:

<scala>
def in3: List[KnightPos] = for {
  first <- move
  second <- first.move
  third <- second.move
} yield third
</scala>

This difference shouldn't pose much problem in practice, but it's something to keep in mind.

### Writer? I hardly knew her!

[Learn You a Haskell for Great Good](http://learnyouahaskell.com/for-a-few-monads-more) says:

> Whereas the `Maybe` monad is for values with an added context of failure, and the list monad is for nondeterministic values, `Writer` monad is for values that have another value attached that acts as a sort of log value.

Let's follow the book and implement `applyLog` function:

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

Since method injection is a common use case for implicits, Scala 2.10 adds a syntax sugar called implicit class to make the promotion from a class to an enriched class easier. Here's how we can generalize the log to a `Monoid`:

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

In Scalaz, the equivalent is called [`Writer`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/package.scala):

<scala>
type Writer[+W, +A] = WriterT[Id, W, A]
</scala>

`Writer[+W, +A]` is a type alias for `WriterT[Id, W, A]`.

### WriterT

Here's the simplified version of [`WriterT`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/WriterT.scala):

<scala>
sealed trait WriterT[F[+_], +W, +A] { self =>
  val run: F[(W, A)]

  def written(implicit F: Functor[F]): F[W] =
    F.map(run)(_._1)
  def value(implicit F: Functor[F]): F[A] =
    F.map(run)(_._2)
}
</scala>

It wasn't immediately obvious to me how a writer is actually created at first, but eventually figured it out:

<scala>
scala> 3.set("Smallish gang.")
res46: scalaz.Writer[String,Int] = scalaz.WriterTFunctions$$anon$26@477a0c05
</scala>

The following operators are supported by all data types enabled by `import Scalaz._`:

<scala>
trait ToDataOps extends ToIdOps with ToTreeOps with ToWriterOps with ToValidationOps with ToReducerOps with ToKleisliOps
</scala>

The operator in question is part of [`WriterV`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/ToWriterOps.scala):

<scala>
trait WriterV[A] extends Ops[A] {
  def set[W](w: W): Writer[W, A] = WriterT.writer(w -> self)

  def tell: Writer[A, Unit] = WriterT.tell(self)
}
</scala>

The above methods are injected to all types so we can use them to create Writers:

<scala>
scala> 3.set("something")
res57: scalaz.Writer[String,Int] = scalaz.WriterTFunctions$$anon$26@159663c3

scala> "something".tell
res58: scalaz.Writer[String,Unit] = scalaz.WriterTFunctions$$anon$26@374de9cf
</scala>

What if we want to get the identity value like `return 3 :: Writer String Int`? `Monad[F[_]]` expects a type constructor with one parameter, but `Writer[+W, +A]` takes two. There's a helper type in Scalaz called `MonadWriter` to help us out:

<scala>
scala> MonadWriter[Writer, String]
res62: scalaz.MonadWriter[scalaz.Writer,String] = scalaz.WriterTInstances$$anon$1@6b8501fa

scala> MonadWriter[Writer, String].point(3).run
res64: (String, Int) = ("",3)
</scala>

### Using for syntax with Writer

LYAHFGG:

> Now that we have a `Monad` instance, we're free to use `do` notation for `Writer` values.


Let's implement the example in Scala:

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

### Adding logging to program

Here's the `gcd` example:

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

### Inefficient List construction

LYAHFGG:

> When using the `Writer` monad, you have to be careful which monoid to use, because using lists can sometimes turn out to be very slow. That's because lists use `++` for `mappend` and using `++` to add something to the end of a list is slow if that list is really long.

Here's [the table of performance characteristics for major collections](http://docs.scala-lang.org/overviews/collections/performance-characteristics.html). What stands out for immutable collection is `Vector` since it has effective constant for all operations. `Vector` is a tree structure with the branching factor of 32, and it's able to achieve fast updates by structure sharing.

For whatever reason, Scalaz 7 does not enable typeclasses for `Vector`s using `import Scalaz._`. So let's import it manually:

<scala>
scala> import std.vector._
import std.vector._

scala> Monoid[Vector[String]]
res73: scalaz.Monoid[Vector[String]] = scalaz.std.IndexedSeqSubInstances$$anon$4@6f82f06f
</scala>

Here's the vector version of `gcd`:

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

### Comparing performance

Like the book let's write a microbenchmark to compare the performance:

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

We can now run this as follows:

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

As you can see `List` is taking almost double the time.

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

Let's try implementing the example:

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

Essentially, the reader monad lets us pretend the value is already there. I am guessing that this works only for functions that accepts one parameter. Unlike `Option` and `List` monads, neither `Writer` nor reader monad is available in the standard library. And they look pretty useful.

Let's pick it up from here later.
