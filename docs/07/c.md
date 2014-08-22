---
out: Either.html
---

### \/

LYAHFGG:

> The `Either e a` type on the other hand, allows us to incorporate a context of possible failure to our values while also being able to attach values to the failure, so that they can describe what went wrong or provide some other useful info regarding the failure.

We know `Either[A, B]` from the standard library, but Scalaz 7 implements its own `Either` equivalent named [`\/`]($scalazBaseUrl$/core/src/main/scala/scalaz/Either.scala):

```scala
sealed trait \/[+A, +B] {
  ...
  /** Return `true` if this disjunction is left. */
  def isLeft: Boolean =
    this match {
      case -\/(_) => true
      case \/-(_) => false
    }

  /** Return `true` if this disjunction is right. */
  def isRight: Boolean =
    this match {
      case -\/(_) => false
      case \/-(_) => true
    }
  ...
  /** Flip the left/right values in this disjunction. Alias for `unary_~` */
  def swap: (B \/ A) =
    this match {
      case -\/(a) => \/-(a)
      case \/-(b) => -\/(b)
    }
  /** Flip the left/right values in this disjunction. Alias for `swap` */
  def unary_~ : (B \/ A) = swap
  ...
  /** Return the right value of this disjunction or the given default if left. Alias for `|` */
  def getOrElse[BB >: B](x: => BB): BB =
    toOption getOrElse x
  /** Return the right value of this disjunction or the given default if left. Alias for `getOrElse` */
  def |[BB >: B](x: => BB): BB = getOrElse(x)

  /** Return this if it is a right, otherwise, return the given value. Alias for `|||` */
  def orElse[AA >: A, BB >: B](x: => AA \/ BB): AA \/ BB =
    this match {
      case -\/(_) => x
      case \/-(_) => this
    }
  /** Return this if it is a right, otherwise, return the given value. Alias for `orElse` */
  def |||[AA >: A, BB >: B](x: => AA \/ BB): AA \/ BB = orElse(x)
  ...
}

private case class -\/[+A](a: A) extends (A \/ Nothing)
private case class \/-[+B](b: B) extends (Nothing \/ B)
```

These values are created using `right` and `left` method injected to all data types via `IdOps`:

```scala
scala> 1.right[String]
res12: scalaz.\/[String,Int] = \/-(1)

scala> "error".left[Int]
res13: scalaz.\/[String,Int] = -\/(error)
```

The `Either` type in Scala standard library is not a monad on its own, which means it does not implement `flatMap` method with or without Scalaz:

```scala
scala> Left[String, Int]("boom") flatMap { x => Right[String, Int](x + 1) }
<console>:8: error: value flatMap is not a member of scala.util.Left[String,Int]
              Left[String, Int]("boom") flatMap { x => Right[String, Int](x + 1) }
                                        ^
```

You have to call `right` method to turn it into `RightProjection`:

```scala
scala> Left[String, Int]("boom").right flatMap { x => Right[String, Int](x + 1)}
res15: scala.util.Either[String,Int] = Left(boom)
```

This is silly since the point of having `Either` is to report an error on the left. Scalaz's `\/` assumes that you'd mostly want right projection:

```scala
scala> "boom".left[Int] >>= { x => (x + 1).right }
res18: scalaz.Unapply[scalaz.Bind,scalaz.\/[String,Int]]{type M[X] = scalaz.\/[String,X]; type A = Int}#M[Int] = -\/(boom)
```

This is nice. Let's try using it in `for` syntax:

```scala
scala> for {
         e1 <- "event 1 ok".right
         e2 <- "event 2 failed!".left[String]
         e3 <- "event 3 failed!".left[String]
       } yield (e1 |+| e2 |+| e3)
res24: scalaz.\/[String,String] = -\/(event 2 failed!)
```

As you can see, the first failure rolls up as the final result. How do we get the value out of `\/`? First there's `isRight` and `isLeft` method to check which side we are on:

```scala
scala> "event 1 ok".right.isRight
res25: Boolean = true

scala> "event 1 ok".right.isLeft
res26: Boolean = false
```

For right side, we can use `getOrElse` and its symbolic alias `|` as follows:

```scala
scala> "event 1 ok".right | "something bad"
res27: String = event 1 ok
```

For left value, we can call `swap` method or it's symbolic alias `unary_~`:

```scala
scala> ~"event 2 failed!".left[String] | "something good"
res28: String = event 2 failed!
```

We can use `map` to modify the right side value:

```scala
scala> "event 1 ok".right map {_ + "!"}
res31: scalaz.\/[Nothing,String] = \/-(event 1 ok!)
```

To chain on the left side, there's `orElse`, which accepts `=> AA \/ BB` where `[AA >: A, BB >: B]`. The symbolic alias for `orElse` is `|||`:

```scala
scala> "event 1 failed!".left ||| "retry event 1 ok".right
res32: scalaz.\/[String,String] = \/-(retry event 1 ok)
```
