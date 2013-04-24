
### a Yes-No typeclass

Let's see if we can make our own truthy value typeclass in the style of Scalaz. Except I am going to add my twist to it for the naming convention. Scalaz calls three or four different things using the name of the typeclass like `Show`, `show`, and `show`, which is a bit confusing.

I like to prefix the typeclass name with `Can` borrowing from `CanBuildFrom`, and name its method as verb + `s`, borrowing from sjson/sbinary. Since `yesno` doesn't make much sense, let's call ours `truthy`. Eventual goal is to get `1.truthy` to return `true`. The downside is that the extra s gets appended if we want to use typeclass instances as functions like `CanTruthy[Int].truthys(1)`.

```scala
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
```

Here's how we can define typeclass instances for `Int`:

```scala
scala> implicit val intCanTruthy: CanTruthy[Int] = CanTruthy.truthys({
         case 0 => false
         case _ => true
       })
intCanTruthy: CanTruthy[Int] = CanTruthy$$anon$1@71780051

scala> 10.truthy
res6: Boolean = true
```

Next is for `List[A]`:

```scala
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
```

It looks like we need to treat `Nil` specially because of the nonvariance.

```scala
scala> implicit val nilCanTruthy: CanTruthy[scala.collection.immutable.Nil.type] = CanTruthy.truthys(_ => false)
nilCanTruthy: CanTruthy[collection.immutable.Nil.type] = CanTruthy$$anon$1@1e5f0fd7

scala> Nil.truthy
res8: Boolean = false
```

And for `Boolean` using `identity`:

```scala
scala> implicit val booleanCanTruthy: CanTruthy[Boolean] = CanTruthy.truthys(identity)
booleanCanTruthy: CanTruthy[Boolean] = CanTruthy$$anon$1@334b4cb

scala> false.truthy
res11: Boolean = false
```

Using `CanTruthy` typeclass, let's define `truthyIf` like LYAHFGG:

> Now let's make a function that mimics the `if` statement, but that works with `YesNo` values.

To delay the evaluation of the passed arguments, we can use pass-by-name:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

def truthyIf[A: CanTruthy, B, C](cond: A)(ifyes: => B)(ifno: => C) =
  if (cond.truthy) ifyes
  else ifno

// Exiting paste mode, now interpreting.

truthyIf: [A, B, C](cond: A)(ifyes: => B)(ifno: => C)(implicit evidence$1: CanTruthy[A])Any
```

Here's how we can use it:

```scala
scala> truthyIf (Nil) {"YEAH!"} {"NO!"}
res12: Any = NO!

scala> truthyIf (2 :: 3 :: 4 :: Nil) {"YEAH!"} {"NO!"}
res13: Any = YEAH!

scala> truthyIf (true) {"YEAH!"} {"NO!"}
res14: Any = YEAH!
```

We'll pick it from here later.
