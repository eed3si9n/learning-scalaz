
### Effect system

In [Lazy Functional State Threads](http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.144.2237&rep=rep1&type=pdf) John Launchbury and Simon Peyton-Jones write:

> Based on earlier work on monads, we present a way of securely encapsulating stateful computations that manipulate multiple, named, mutable objects, in the context of a non-strict purely-functional language.

Because Scala has `var` at first it seems like we might not need this, but the concept of encapsulating stateful computation can be useful. Under some circumstances like concurrently running computations, it's critical that states are either not shared or shared carefully.

### ST

In Scalaz there's `ST` monad that corresponds to `ST` described in the paper. Also see [Towards an Effect System in Scala, Part 1: ST Monad](http://apocalisp.wordpress.com/2011/03/20/towards-an-effect-system-in-scala-part-1/) by Rúnar for details. Here's the typeclass contract for [`ST`]($scalazBaseUrl$/effect/src/main/scala/scalaz/effect/ST.scala):

```scala
sealed trait ST[S, A] {
  private[effect] def apply(s: World[S]): (World[S], A)
}
```

This looks similar to `State` monad, but the difference I think is that the state is mutated in-place, and in return is not observable from outside.

### STRef

LFST:

> What, then is a "state"? Part of every state is a finite mapping from *reference* to values. ... A reference can be thought of as the name of (or address of) a *variable*, an updatable location in the state capable of holding a value.

`STRef` is a mutable variable that's used only within the context of `ST` monad. It's created using `ST.newVar[A]`, and supports the following operations:

```scala
sealed trait STRef[S, A] {
  protected var value: A

  /**Reads the value pointed at by this reference. */
  def read: ST[S, A] = returnST(value)
  /**Modifies the value at this reference with the given function. */
  def mod[B](f: A => A): ST[S, STRef[S, A]] = ...
  /**Associates this reference with the given value. */
  def write(a: => A): ST[S, STRef[S, A]] = ...
  /**Synonym for write*/
  def |=(a: => A): ST[S, STRef[S, A]] = ...
  /**Swap the value at this reference with the value at another. */
  def swap(that: STRef[S, A]): ST[S, Unit] = ...
}
```

I'm going to use my local version of Scalaz 7:

```scala
\$ sbt
scalaz> project effect
scalaz-effect> console
[info] Compiling 2 Scala sources to /Users/eed3si9n/work/scalaz-seven/effect/target/scala-2.9.2/classes...
[info] Starting scala interpreter...
[info]

scala> import scalaz._
import scalaz._

scala> import Scalaz._
import Scalaz._

scala> import effect._
import effect._

scala> import ST.{newVar, runST, newArr, returnST}
import ST.{newVar, runST, newArr, returnST}

scala> def e1[S] = for {
         x <- newVar[S](0)
         r <- x mod {_ + 1}
       } yield x
e1: [S]=> scalaz.effect.ST[S,scalaz.effect.STRef[S,Int]]

scala> def e2[S]: ST[S, Int] = for {
         x <- e1[S]
         r <- x.read
       } yield r
e2: [S]=> scalaz.effect.ST[S,Int]

scala> type ForallST[A] = Forall[({type λ[S] = ST[S, A]})#λ]
defined type alias ForallST

scala> runST(new ForallST[Int] { def apply[S] = e2[S] })
res5: Int = 1
```

On Rúnar's blog, [Paul Chiusano (@pchiusano)](http://twitter.com/pchiusano) asks what you're probably thinking:

> I’m still sort of undecided on the utility of doing this in Scala – just to play devils advocate – if you need to do some local mutation for purposes of implementing an algorithm (like, say, quicksort), just don’t mutate anything passed into your function. Is there much benefit in convincing the compiler you’ve done this properly? I am not sure I care about having compiler help with this.

He comes back to the site 30 min later and answers himself:

> If I were writing an imperative quicksort, I would probably copy the input sequence to an array, mutate it in place during the sort, then return some immutable view of the sorted array. With STRef, I can accept an STRef to a mutable array, and avoid making a copy at all. Furthermore, my imperative actions are first class and I can use all the usual combinators for combining them.

This is an interesting point. Because the mutable state is guaranteed not to bleed out, the change to the mutable state can be chained and composed without copying the data around. When you need mutation, many times you need arrays, so there's an array wrapper called `STArray`:

```scala
sealed trait STArray[S, A] {
  val size: Int
  val z: A
  private val value: Array[A] = Array.fill(size)(z)
  /**Reads the value at the given index. */
  def read(i: Int): ST[S, A] = returnST(value(i))
  /**Writes the given value to the array, at the given offset. */
  def write(i: Int, a: A): ST[S, STArray[S, A]] = ...
  /**Turns a mutable array into an immutable one which is safe to return. */
  def freeze: ST[S, ImmutableArray[A]] = ...
  /**Fill this array from the given association list. */
  def fill[B](f: (A, B) => A, xs: Traversable[(Int, B)]): ST[S, Unit] = ...
  /**Combine the given value with the value at the given index, using the given function. */
  def update[B](f: (A, B) => A, i: Int, v: B) = ...
}
```

This is created using `ST.newArr(size: Int, z: A)`. Let's calculate all the prime numbers including or under 1000 using the sieve of Eratosthenes..

### Interruption

I actually found a bug in `STArray` implementation. Let me fix this up quickly.

```
\$ git pull --rebase
Current branch scalaz-seven is up to date.
\$ git branch topic/starrayfix
\$ git co topic/starrayfix
Switched to branch 'topic/starrayfix'
```

Since `ST` is missing a spec, I'm going to start one to reproduce the bug. This way it would be caught if someone tried to roll back my fix.

```scala
package scalaz
package effect

import std.AllInstances._
import ST._

class STTest extends Spec {
  type ForallST[A] = Forall[({type λ[S] = ST[S, A]})#λ]

  "STRef" in {
    def e1[S] = for {
      x <- newVar[S](0)
      r <- x mod {_ + 1}
    } yield x
    def e2[S]: ST[S, Int] = for {
      x <- e1[S]
      r <- x.read
    } yield r
    runST(new ForallST[Int] { def apply[S] = e2[S] }) must be_===(1)
  }

  "STArray" in {
    def e1[S] = for {
      arr <- newArr[S, Boolean](3, true)
      _ <- arr.write(0, false)
      r <- arr.freeze
    } yield r
    runST(new ForallST[ImmutableArray[Boolean]] { def apply[S] = e1[S] }).toList must be_===(
      List(false, true, true))
  }
}
```

Here's the result:

```
[info] STTest
[info]
[info] + STRef
[error] ! STArray
[error]   NullPointerException: null (ArrayBuilder.scala:37)
[error] scala.collection.mutable.ArrayBuilder\$.make(ArrayBuilder.scala:37)
[error] scala.Array\$.newBuilder(Array.scala:52)
[error] scala.Array\$.fill(Array.scala:235)
[error] scalaz.effect.STArray\$class.\$init\$(ST.scala:71)
...
```

NullPointerException in Scala?! This is coming from the following code in `STArray`:

```scala
sealed trait STArray[S, A] {
  val size: Int
  val z: A
  implicit val manifest: Manifest[A]

  private val value: Array[A] = Array.fill(size)(z)
  ...
}
...
trait STArrayFunctions {
  def stArray[S, A](s: Int, a: A)(implicit m: Manifest[A]): STArray[S, A] = new STArray[S, A] {
    val size = s
    val z = a
    implicit val manifest = m
  }
}
```

Do you see it? Paulp wrote [a FAQ](https://github.com/paulp/scala-faq/wiki/Initialization-Order) on this. `value` is initialized using uninitialized `size` and `z`. Here's my fix:

```scala
sealed trait STArray[S, A] {
  def size: Int
  def z: A
  implicit def manifest: Manifest[A]

  private lazy val value: Array[A] = Array.fill(size)(z)
  ...
}
```

Now the test passes. Push it up and [send a pull request](https://github.com/scalaz/scalaz/pull/155).

### Back to the usual programming

[The sieve of Eratosthenes](http://en.wikipedia.org/wiki/Sieve_of_Eratosthenes#Implementation) is a simple algorithm to calculate prime numbers.

```scala
scala> import scalaz._, Scalaz._, effect._, ST._
import scalaz._
import Scalaz._
import effect._
import ST._

scala> def mapM[A, S, B](xs: List[A])(f: A => ST[S, B]): ST[S, List[B]] =
         Monad[({type λ[α] = ST[S, α]})#λ].sequence(xs map f)
mapM: [A, S, B](xs: List[A])(f: A => scalaz.effect.ST[S,B])scalaz.effect.ST[S,List[B]]

scala> def sieve[S](n: Int) = for {
         arr <- newArr[S, Boolean](n + 1, true)
         _ <- arr.write(0, false)
         _ <- arr.write(1, false)
         val nsq = (math.sqrt(n.toDouble).toInt + 1)
         _ <- mapM (1 |-> nsq) { i =>
           for {
             x <- arr.read(i)
             _ <-
               if (x) mapM (i * i |--> (i, n)) { j => arr.write(j, false) }
               else returnST[S, List[Boolean]] {Nil}
           } yield ()
         }
         r <- arr.freeze
       } yield r
sieve: [S](n: Int)scalaz.effect.ST[S,scalaz.ImmutableArray[Boolean]]

scala> type ForallST[A] = Forall[({type λ[S] = ST[S, A]})#λ]
defined type alias ForallST

scala> def prime(n: Int) =
         runST(new ForallST[ImmutableArray[Boolean]] { def apply[S] = sieve[S](n) }).toArray.
         zipWithIndex collect { case (true, x) => x }
prime: (n: Int)Array[Int]

scala> prime(1000)
res21: Array[Int] = Array(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97, 101, 103, 107, 109, 113, 127, 131, 137, 139, 149, 151, 157, 163, 167, 173, 179, 181, 191, 193, 197, 199, 211, 223, 227, 229, 233, 239, 241, 251, 257, 263, 269, 271, 277, 281, 283, 293, 307, 311, 313, 317, 331, 337, 347, 349, 353, 359, 367, 373, 379, 383, 389, 397, 401, 409, 419, 421, 431, 433, 439, 443, 449, 457, 461, 463, 467, 479, 487, 491, 499, 503, 509, 521, 523, 541, 547, 557, 563, 569, 571, 577, 587, 593, 599, 601, 607, 613, 617, 619, 631, 641, 643, 647, 653, 659, 661, 673, 677, 683, 691, 701, 709, 719, 727, 733, 739, 743, 751, 757, 761, 769, 773, 787, 797, 809, 811, 821, 823, 827, 829, 839, 853, 857, 859, 863, 877, 881, 883, 887, 907, 911, 919, 929, 937, 941, ...
```

The result looks ok according [this list of first 1000 primes](http://primes.utm.edu/lists/small/1000.txt). The most difficult part was wrapping my head around the iteration over `STArray`. Because we are in the context of `ST[S, _]`, the result of the loop also needs to be an ST monad. If we mapped over a list and wrote into the array that's going to return `List[ST[S, Unit]]`.

I implemented `mapM`, which takes a monadic function for `ST[S, B]` and returns `ST[S, List[B]]` by inverting the monads. It's basically like `sequence`, but I think it's easier to understand. It's definitely not pain-free compared to using `var`, but the ability to pass around the mutable contexts around may be useful.

We'll pick it from from here later.
