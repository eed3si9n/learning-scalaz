
Also a comment from Jason Zaugg:

> This might be a good point to pause and discuss the laws by which a well behaved type class instance must abide.

I've been skipping all the sections in [Learn You a Haskell for Great Good](http://learnyouahaskell.com/functors-applicative-functors-and-monoids) about the laws and we got pulled over.

### Functor Laws

LYAHFGG:

> All functors are expected to exhibit certain kinds of functor-like properties and behaviors.
> ...
> The first functor law states that if we map the id function over a functor, the functor that we get back should be the same as the original functor.

In other words,

```scala
scala> List(1, 2, 3) map {identity} assert_=== List(1, 2, 3)
```

> The second law says that composing two functions and then mapping the resulting function over a functor should be the same as first mapping one function over the functor and then mapping the other one.

In other words,

```scala
scala> (List(1, 2, 3) map {{(_: Int) * 3} map {(_: Int) + 1}}) assert_=== (List(1, 2, 3) map {(_: Int) * 3} map {(_: Int) + 1})
```

These are laws the implementer of the functors must abide, and not something the compiler can check for you. Scalaz 7 ships with `FunctorLaw` traits that describes this in [code](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Functor.scala#L68-77):

```scala
trait FunctorLaw {
  /** The identity function, lifted, is a no-op. */
  def identity[A](fa: F[A])(implicit FA: Equal[F[A]]): Boolean = FA.equal(map(fa)(x => x), fa)

  /**
   * A series of maps may be freely rewritten as a single map on a
   * composed function.
   */
  def associative[A, B, C](fa: F[A], f1: A => B, f2: B => C)(implicit FC: Equal[F[C]]): Boolean = FC.equal(map(map(fa)(f1))(f2), map(fa)(f2 compose f1))
}
```

Not only that, it ships with ScalaCheck bindings to test these properties using arbiterary values. Here's the `build.sbt` to check from REPL:

```scala
scalaVersion := "2.10.1"

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-core" % "7.0.0",
  "org.scalaz" %% "scalaz-effect" % "7.0.0",
  "org.scalaz" %% "scalaz-typelevel" % "7.0.0",
  "org.scalaz" %% "scalaz-scalacheck-binding" % "7.0.0" % "test"
)

scalacOptions += "-feature"

initialCommands in console := "import scalaz._, Scalaz._"

initialCommands in console in Test := "import scalaz._, Scalaz._, scalacheck.ScalazProperties._, scalacheck.ScalazArbitrary._,scalacheck.ScalaCheckBinding._"
```

Instead of the usual `sbt console`, run `sbt test:console`:

```scala
$ sbt test:console
[info] Starting scala interpreter...
[info] 
import scalaz._
import Scalaz._
import scalacheck.ScalazProperties._
import scalacheck.ScalazArbitrary._
import scalacheck.ScalaCheckBinding._
Welcome to Scala version 2.10.1 (Java HotSpot(TM) 64-Bit Server VM, Java 1.6.0_45).
Type in expressions to have them evaluated.
Type :help for more information.

scala> 
```

Here's how you test if `List` meets the functor laws:

```scala
scala> functor.laws[List].check
+ functor.identity: OK, passed 100 tests.
+ functor.associative: OK, passed 100 tests.
```

### Breaking the law

Following the book, let's try breaking the law.

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait COption[+A] {}
case class CSome[A](counter: Int, a: A) extends COption[A]
case object CNone extends COption[Nothing]

implicit def coptionEqual[A]: Equal[COption[A]] = Equal.equalA
implicit val coptionFunctor = new Functor[COption] {
  def map[A, B](fa: COption[A])(f: A => B): COption[B] = fa match {
    case CNone => CNone
    case CSome(c, a) => CSome(c + 1, f(a))
  }
}

// Exiting paste mode, now interpreting.

defined trait COption
defined class CSome
defined module CNone
coptionEqual: [A]=> scalaz.Equal[COption[A]]
coptionFunctor: scalaz.Functor[COption] = $anon$1@42538425

scala> (CSome(0, "ho"): COption[String]) map {(_: String) + "ha"}
res4: COption[String] = CSome(1,hoha)

scala> (CSome(0, "ho"): COption[String]) map {identity}
res5: COption[String] = CSome(1,ho)
```

It's breaking the first law. Let's see if we can catch this.

```scala
scala> functor.laws[COption].check
<console>:26: error: could not find implicit value for parameter af: org.scalacheck.Arbitrary[COption[Int]]
              functor.laws[COption].check
                          ^
```

So now we have to supply "arbitrary" `COption[A]` implicitly:

```scala
scala> import org.scalacheck.{Gen, Arbitrary}
import org.scalacheck.{Gen, Arbitrary}

scala> implicit def COptionArbiterary[A](implicit a: Arbitrary[A]): Arbitrary[COption[A]] =
         a map { a => (CSome(0, a): COption[A]) }
COptionArbiterary: [A](implicit a: org.scalacheck.Arbitrary[A])org.scalacheck.Arbitrary[COption[A]]
```

This is pretty cool. ScalaCheck on its own does not ship `map` method, but Scalaz injected it as a `Functor[Arbitrary]`! Not much of an arbitrary `COption`, but I don't know enough ScalaCheck, so this will have to do.

```scala
scala> functor.laws[COption].check
! functor.identity: Falsified after 0 passed tests.
> ARG_0: CSome(0,-170856004)
! functor.associative: Falsified after 0 passed tests.
> ARG_0: CSome(0,1)
> ARG_1: <function1>
> ARG_2: <function1>
```

And the test fails as expected.

### Applicative Laws

Here are the laws for [Applicative](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Applicative.scala#L60-72):

```scala
  trait ApplicativeLaw extends FunctorLaw {
    def identityAp[A](fa: F[A])(implicit FA: Equal[F[A]]): Boolean =
      FA.equal(ap(fa)(point((a: A) => a)), fa)

    def composition[A, B, C](fbc: F[B => C], fab: F[A => B], fa: F[A])(implicit FC: Equal[F[C]]) =
      FC.equal(ap(ap(fa)(fab))(fbc), ap(fa)(ap(fab)(ap(fbc)(point((bc: B => C) => (ab: A => B) => bc compose ab)))))

    def homomorphism[A, B](ab: A => B, a: A)(implicit FB: Equal[F[B]]): Boolean =
      FB.equal(ap(point(a))(point(ab)), point(ab(a)))

    def interchange[A, B](f: F[A => B], a: A)(implicit FB: Equal[F[B]]): Boolean =
      FB.equal(ap(point(a))(f), ap(f)(point((f: A => B) => f(a))))
  }
```

LYAHFGG is skipping the details on this, so I am skipping too.

### Semigroup Laws

Here are the [Semigroup Laws](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Semigroup.scala#L38-47):

```scala
  /**
   * A semigroup in type F must satisfy two laws:
    *
    *  - '''closure''': `∀ a, b in F, append(a, b)` is also in `F`. This is enforced by the type system.
    *  - '''associativity''': `∀ a, b, c` in `F`, the equation `append(append(a, b), c) = append(a, append(b , c))` holds.
   */
  trait SemigroupLaw {
    def associative(f1: F, f2: F, f3: F)(implicit F: Equal[F]): Boolean =
      F.equal(append(f1, append(f2, f3)), append(append(f1, f2), f3))
  }
```

Remember, `1 * (2 * 3)` and `(1 * 2) * 3` must hold, which is called *associative*.

```scala
scala> semigroup.laws[Int @@ Tags.Multiplication].check
+ semigroup.associative: OK, passed 100 tests.
```

### Monoid Laws

Here are the [Monoid Laws](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Monoid.scala#L50-59):

```scala
  /**
   * Monoid instances must satisfy [[scalaz.Semigroup.SemigroupLaw]] and 2 additional laws:
   *
   *  - '''left identity''': `forall a. append(zero, a) == a`
   *  - '''right identity''' : `forall a. append(a, zero) == a`
   */
  trait MonoidLaw extends SemigroupLaw {
    def leftIdentity(a: F)(implicit F: Equal[F]) = F.equal(a, append(zero, a))
    def rightIdentity(a: F)(implicit F: Equal[F]) = F.equal(a, append(a, zero))
  }
```

This law is simple. I can `|+|` (`mappend`) identity value to either left hand side or right hand side. For multiplication:

```scala
scala> 1 * 2 assert_=== 2

scala> 2 * 1 assert_=== 2
```

Using Scalaz:

```scala
scala> (Monoid[Int @@ Tags.Multiplication].zero |+| Tags.Multiplication(2): Int) assert_=== 2

scala> (Tags.Multiplication(2) |+| Monoid[Int @@ Tags.Multiplication].zero: Int) assert_=== 2

scala> monoid.laws[Int @@ Tags.Multiplication].check
+ monoid.semigroup.associative: OK, passed 100 tests.
+ monoid.left identity: OK, passed 100 tests.
+ monoid.right identity: OK, passed 100 tests.
```
