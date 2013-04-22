How many programming languages have been called Lisp in sheep's clothing? Java brought in GC to familiar C++ like grammar. Although there have been other languages with GC, in 1996 it felt like a big deal because it promised to become a viable alternative to C++. Eventually, people got used to not having to manage memory by hand. JavaScript and Ruby both have been called Lisp in sheep's clothing for their first-class functions and block syntax. The homoiconic nature of S-expression still makes Lisp-like languages interesting as it fits well to macros.

Recently languages are borrowing concepts from newer breed of functional languages. Type inference and pattern matching I am guessing goes back to ML. Eventually people will come to expect these features too. Given that Lisp came out in 1958 and ML in 1973, it seems to take decades for good ideas to catch on. For those cold decades, these languages were probably considered heretical or worse "not serious."

Looking back to our Scala community, it pains me to see people jeering at Scalaz. I'm not saying it's going to be the next big thing. I don't even know about it yet. But one thing for sure is that guys using it are serious about solving their problems. Or just as pedantic as the rest of the Scala community using pattern matching. Given that Haskell came out in 1990, the witch hunt may last a while, but I am going to keep an open mind.

### typeclasses 101

  [tt]: http://learnyouahaskell.com/types-and-typeclasses
  [moott]: http://learnyouahaskell.com/making-our-own-types-and-typeclasses
  [z7]: https://github.com/scalaz/scalaz/tree/scalaz-seven
  [start]: http://halcat0x15a.github.com/slide/start_scalaz/out/#4
  [z7docs]: http://halcat0x15a.github.com/scalaz/core/target/scala-2.9.2/api/

[Learn You a Haskell for Great Good][tt] says:

> A typeclass is a sort of interface that defines some behavior. If a type is a part of a typeclass, that means that it supports and implements the behavior the typeclass describes.

[Scalaz][z7] says:

> It provides purely functional data structures to complement those from the Scala standard library. It defines a set of foundational type classes (e.g. `Functor`, `Monad`) and corresponding instances for a large number of data structures.

Let's see if I can learn Scalaz by learning me a Haskell.

### sbt

Here's build.sbt to test Scalaz 7. It's [a slide][start] from Nekoharu sensei's talk with some updates:

<scala>
scalaVersion := "2.10.0-M7"

resolvers += "Typesafe Snapshots" at "http://repo.typesafe.com/typesafe/snapshots/"

libraryDependencies ++= Seq(
  "org.scalaz" % "scalaz-core" % "7.0.0-M3" cross CrossVersion.full
)

scalacOptions += "-feature"

initialCommands in console := "import scalaz._, Scalaz._"
</scala>

All you have to do now is open the REPL using sbt 0.12.0:

<code>
$ sbt console
...
[info] downloading http://repo1.maven.org/maven2/org/scalaz/scalaz-core_2.10.0-M7/7.0.0-M3/scalaz-core_2.10.0-M7-7.0.0-M3.jar ...
import scalaz._
import Scalaz._
Welcome to Scala version 2.10.0-M7 (Java HotSpot(TM) 64-Bit Server VM, Java 1.6.0_33).
Type in expressions to have them evaluated.
Type :help for more information.

scala> 
</code>

There's also [API docs][z7docs] generated for Scalaz 7.0.0 M1 by him.

### Equal

LYAHFGG:

> `Eq` is used for types that support equality testing. The functions its members implement are `==` and `/=`.

Scalaz equivalent for the `Eq` typeclass is called `Equal`:

<code>
scala> 1 === 1
res0: Boolean = true

scala> 1 === "foo"
<console>:14: error: could not find implicit value for parameter F0: scalaz.Equal[Object]
              1 === "foo"
              ^

scala> 1 == "foo"
<console>:14: warning: comparing values of types Int and String using `==' will always yield false
              1 == "foo"
                ^
res2: Boolean = false

scala> 1.some =/= 2.some
res3: Boolean = true

scala> 1 assert_=== 2
java.lang.RuntimeException: 1 ≠ 2
</code>

Instead of the standard `==`, `Equal` enables `===`, `=/=`, and `assert_===` syntax by declaring `equal` method. The main difference is that `===` would fail compilation if you tried to compare `Int` and `String`.

Note: I originally had `/==` instead of `=/=`, but [Eiríkr Åsheim (@d6)](http://twitter.com/d6/status/243557748091011074) pointed out to me:
> you should encourage people to use =/= and not /== since the latter has bad precedence.

Normally comparison operators like `!=` have lower higher precedence than `&&`, all letters, etc. Due to special precedence rule `/==` is recognized as an assignment operator because it ends with `=` and does not start with `=`, which drops to the bottom of the precedence:

<scala>
scala> 1 != 2 && false
res4: Boolean = false

scala> 1 /== 2 && false
<console>:14: error: value && is not a member of Int
              1 /== 2 && false
                      ^

scala> 1 =/= 2 && false
res6: Boolean = false
</scala>

### Order

LYAHFGG:

> `Ord` is for types that have an ordering. `Ord` covers all the standard comparing functions such as `>`, `<`, `>=` and `<=`.

Scalaz equivalent for the `Ord` typeclass is `Order`:

<code>
scala> 1 > 2.0
res8: Boolean = false

scala> 1 gt 2.0
<console>:14: error: could not find implicit value for parameter F0: scalaz.Order[Any]
              1 gt 2.0
              ^

scala> 1.0 ?|? 2.0
res10: scalaz.Ordering = LT

scala> 1.0 max 2.0
res11: Double = 2.0
</code>

`Order` enables `?|?` syntax which returns `Ordering`: `LT`, `GT`, and `EQ`. It also enables `lt`, `gt`, `lte`, `gte`, `min`, and `max` operators by declaring `order` method. Similar to `Equal`, comparing `Int` and `Doubl` fails compilation.

### Show

LYAHFGG:

> Members of `Show` can be presented as strings.

Scalaz equivalent for the `Show` typeclass is `Show`:

<code>
scala> 3.show
res14: scalaz.Cord = 3

scala> 3.shows
res15: String = 3

scala> "hello".println
"hello"
</code>

`Cord` apparently is a purely functional data structure for potentially long Strings.

### Read

LYAHFGG:

> `Read` is sort of the opposite typeclass of `Show`. The `read` function takes a string and returns a type which is a member of `Read`.

I could not find Scalaz equivalent for this typeclass.

### Enum

LYAHFGG:

> `Enum` members are sequentially ordered types — they can be enumerated. The main advantage of the `Enum` typeclass is that we can use its types in list ranges. They also have defined successors and predecesors, which you can get with the `succ` and `pred` functions.

Scalaz equivalent for the `Enum` typeclass is `Enum`:

<code>
scala> 'a' to 'e'
res30: scala.collection.immutable.NumericRange.Inclusive[Char] = NumericRange(a, b, c, d, e)

scala> 'a' |-> 'e'
res31: List[Char] = List(a, b, c, d, e)

scala> 3 |=> 5
res32: scalaz.EphemeralStream[Int] = scalaz.EphemeralStreamFunctions$$anon$4@6a61c7b6

scala> 'B'.succ
res33: Char = C
</code>

Instead of the standard `to`, `Enum` enables `|->` that returns a `List` by declaring `pred` and `succ` method on top of `Order` typeclass. There are a bunch of other operations it enables like `-+-`, `---`, `from`, `fromStep`, `pred`, `predx`, `succ`, `succx`, `|-->`, `|->`, `|==>`, and `|=>`. It seems like these are all about stepping forward or backward, and returning ranges.

### Bounded

> `Bounded` members have an upper and a lower bound.

Scalaz equivalent for `Bounded` seems to be `Enum` as well.

<code>
scala> implicitly[Enum[Char]].min
res43: Option[Char] = Some(?)

scala> implicitly[Enum[Char]].max
res44: Option[Char] = Some(￿)

scala> implicitly[Enum[Double]].max
res45: Option[Double] = Some(1.7976931348623157E308)

scala> implicitly[Enum[Int]].min
res46: Option[Int] = Some(-2147483648)

scala> implicitly[Enum[(Boolean, Int, Char)]].max
<console>:14: error: could not find implicit value for parameter e: scalaz.Enum[(Boolean, Int, Char)]
              implicitly[Enum[(Boolean, Int, Char)]].max
                        ^
</code>

`Enum` typeclass instance returns `Opton[T]` for max values.

### Num

> `Num` is a numeric typeclass. Its members have the property of being able to act like numbers.

I did not find Scalaz equivalent for `Num`, `Floating`, and `Integral`.

### typeclasses 102

I am now going to skip over to Chapter 8 [Making Our Own Types and Typeclasses][moott] (Chapter 7 if you have the book) since the chapters in between are mostly about Haskell syntax.

### A traffic light data type

<haskell>
data TrafficLight = Red | Yellow | Green
</haskell>

In Scala this would be:

<scala>
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait TrafficLight
case object Red extends TrafficLight
case object Yellow extends TrafficLight
case object Green extends TrafficLight
</scala>

Now let's define an instance for `Equal`.

<scala>
scala> implicit val TrafficLightEqual: Equal[TrafficLight] = Equal.equal(_ == _)
TrafficLightEqual: scalaz.Equal[TrafficLight] = scalaz.Equal$$anon$7@2457733b
</scala>

Can I use it?

<scala>
scala> Red === Yellow
<console>:18: error: could not find implicit value for parameter F0: scalaz.Equal[Product with Serializable with TrafficLight]
              Red === Yellow
</scala>

So apparently `Equal[TrafficLight]` doesn't get picked up because `Equal` has nonvariant subtyping: `Equal[F]`. If I turned `TrafficLight` to a case class then `Red` and `Yellow` would have the same type, but then I lose the tight pattern matching from sealed #fail.

<scala>
scala> :paste
// Entering paste mode (ctrl-D to finish)

case class TrafficLight(name: String)
val red = TrafficLight("red")
val yellow = TrafficLight("yellow")
val green = TrafficLight("green")
implicit val TrafficLightEqual: Equal[TrafficLight] = Equal.equal(_ == _)
red === yellow

// Exiting paste mode, now interpreting.

defined class TrafficLight
red: TrafficLight = TrafficLight(red)
yellow: TrafficLight = TrafficLight(yellow)
green: TrafficLight = TrafficLight(green)
TrafficLightEqual: scalaz.Equal[TrafficLight] = scalaz.Equal$$anon$7@42988fee
res3: Boolean = false
</scala>

### a Yes-No typeclass

Let's see if we can make our own truthy value typeclass in the style of Scalaz. Except I am going to add my twist to it for the naming convention. Scalaz calls three or four different things using the name of the typeclass like `Show`, `show`, and `show`, which is a bit confusing.

I like to prefix the typeclass name with `Can` borrowing from `CanBuildFrom`, and name its method as verb + `s`, borrowing from sjson/sbinary. Since `yesno` doesn't make much sense, let's call ours `truthy`. Eventual goal is to get `1.truthy` to return `true`. The downside is that the extra s gets appended if we want to use typeclass instances as functions like `CanTruthy[Int].truthys(1)`.

<scala>
scala> :paste
// Entering paste mode (ctrl-D to finish)

trait CanTruthy[A] { self =>
  /** @return true, if `a` is truthy. */
  def truthys(a: A): Boolean
}
object CanTruthy {
  def apply[A](implicit ev: CanTruthy[A]): CanTruthy[A] = ev
  def truthys[A](f: A => Boolean): CanTruthy[A] = new CanTruthy[A] {
    def truthys(a: A): Boolean = f(a)
  }
}
trait CanTruthyOps[A] {
  def self: A
  implicit def F: CanTruthy[A]
  final def truthy: Boolean = F.truthys(self)
}
object ToCanIsTruthyOps {
  implicit def toCanIsTruthyOps[A](v: A)(implicit ev: CanTruthy[A]) =
    new CanTruthyOps[A] {
      def self = v
      implicit def F: CanTruthy[A] = ev
    }
}

// Exiting paste mode, now interpreting.

defined trait CanTruthy
defined module CanTruthy
defined trait CanTruthyOps
defined module ToCanIsTruthyOps

scala> import ToCanIsTruthyOps._
import ToCanIsTruthyOps._
</scala>

Here's how we can define typeclass instances for `Int`:

<scala>
scala> implicit val intCanTruthy: CanTruthy[Int] = CanTruthy.truthys({
         case 0 => false
         case _ => true
       })
intCanTruthy: CanTruthy[Int] = CanTruthy$$anon$1@71780051

scala> 10.truthy
res6: Boolean = true
</scala>

Next is for `List[A]`:

<scala>
scala> implicit def listCanTruthy[A]: CanTruthy[List[A]] = CanTruthy.truthys({
         case Nil => false
         case _   => true  
       })
listCanTruthy: [A]=> CanTruthy[List[A]]

scala> List("foo").truthy
res7: Boolean = true

scala> Nil.truthy
<console>:23: error: could not find implicit value for parameter ev: CanTruthy[scala.collection.immutable.Nil.type]
              Nil.truthy
</scala>

It looks like we need to treat `Nil` specially because of the nonvariance.

<scala>
scala> implicit val nilCanTruthy: CanTruthy[scala.collection.immutable.Nil.type] = CanTruthy.truthys(_ => false)
nilCanTruthy: CanTruthy[collection.immutable.Nil.type] = CanTruthy$$anon$1@1e5f0fd7

scala> Nil.truthy
res8: Boolean = false
</scala>

And for `Boolean` using `identity`:

<scala>
scala> implicit val booleanCanTruthy: CanTruthy[Boolean] = CanTruthy.truthys(identity)
booleanCanTruthy: CanTruthy[Boolean] = CanTruthy$$anon$1@334b4cb

scala> false.truthy
res11: Boolean = false
</scala>

Using `CanTruthy` typeclass, let's define `truthyIf` like LYAHFGG:

> Now let's make a function that mimics the `if` statement, but that works with `YesNo` values.

To delay the evaluation of the passed arguments, we can use pass-by-name:

<scala>
scala> :paste
// Entering paste mode (ctrl-D to finish)

def truthyIf[A: CanTruthy, B, C](cond: A)(ifyes: => B)(ifno: => C) =
  if (cond.truthy) ifyes
  else ifno

// Exiting paste mode, now interpreting.

truthyIf: [A, B, C](cond: A)(ifyes: => B)(ifno: => C)(implicit evidence$1: CanTruthy[A])Any
</scala>

Here's how we can use it:

<scala>
scala> truthyIf (Nil) {"YEAH!"} {"NO!"}
res12: Any = NO!

scala> truthyIf (2 :: 3 :: 4 :: Nil) {"YEAH!"} {"NO!"}
res13: Any = YEAH!

scala> truthyIf (true) {"YEAH!"} {"NO!"}
res14: Any = YEAH!
</scala>

We'll pick it from here later.

