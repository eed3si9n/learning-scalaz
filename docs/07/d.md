
### Validation

Another data structure that's compared to `Either` in Scalaz is [`Validation`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Validation.scala):

```scala
sealed trait Validation[+E, +A] {
  /** Return `true` if this validation is success. */
  def isSuccess: Boolean = this match {
    case Success(_) => true
    case Failure(_) => false
  }
  /** Return `true` if this validation is failure. */
  def isFailure: Boolean = !isSuccess

  ...
}

final case class Success[E, A](a: A) extends Validation[E, A]
final case class Failure[E, A](e: E) extends Validation[E, A]
```

At the first glance `Validation` looks similar to `\/`. They can even be converted back and forth using `validation` method and `disjunction` method.

[`ValidationV`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/ValidationV.scala) introduces `success[X]`, `successNel[X]`, `failure[X]`, and `failureNel[X]` methods to all data types (don't worry about the `Nel` thing for now):

```scala
scala> "event 1 ok".success[String]
res36: scalaz.Validation[String,String] = Success(event 1 ok)

scala> "event 1 failed!".failure[String]
res38: scalaz.Validation[String,String] = Failure(event 1 failed!)
```

What's different about `Validation` is that it is not a monad, but it's an applicative functor. Instead of chaining the result from first event to the next, `Validation` validates all events:

```scala
scala> ("event 1 ok".success[String] |@| "event 2 failed!".failure[String] |@| "event 3 failed!".failure[String]) {_ + _ + _}
res44: scalaz.Unapply[scalaz.Apply,scalaz.Validation[String,String]]{type M[X] = scalaz.Validation[String,X]; type A = String}#M[String] = Failure(event 2 failed!event 3 failed!)
```

It's a bit difficult to see, but the final result is `Failure(event 2 failed!event 3 failed!)`. Unlike `\/` monad which cut the calculation short, `Validation` keeps going and reports back all failures. This probably would be useful for validating user's input on an online bacon shop.

The problem, however, is that the error messages are mushed together into one string. Shouldn't it be something like a list?

### NonEmptyList

This is where [`NonEmptyList`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/NonEmptyList.scala) (or `Nel` for short) comes in:

```scala
/** A singly-linked list that is guaranteed to be non-empty. */
sealed trait NonEmptyList[+A] {
  val head: A
  val tail: List[A]
  def <::[AA >: A](b: AA): NonEmptyList[AA] = nel(b, head :: tail)
  ...
}
```

This is a wrapper trait for plain `List` that's guaranteed to be non-empty. Since there's at least one item in the list, `head` always works. `IdOps` adds `wrapNel` to all data types to create a `Nel`.

```scala
scala> 1.wrapNel
res47: scalaz.NonEmptyList[Int] = NonEmptyList(1)
```

Now does `successNel[X]` and `failureNel[X]` make sense?

```scala
scala> "event 1 ok".successNel[String]
res48: scalaz.ValidationNEL[String,String] = Success(event 1 ok)

scala> "event 1 failed!".failureNel[String]
res49: scalaz.ValidationNEL[String,String] = Failure(NonEmptyList(event 1 failed!))

scala> ("event 1 ok".successNel[String] |@| "event 2 failed!".failureNel[String] |@| "event 3 failed!".failureNel[String]) {_ + _ + _}
res50: scalaz.Unapply[scalaz.Apply,scalaz.ValidationNEL[String,String]]{type M[X] = scalaz.ValidationNEL[String,X]; type A = String}#M[String] = Failure(NonEmptyList(event 2 failed!, event 3 failed!))
```

In `Failure`, we were able to accumulate all failed messages.

We will pick it up from here later.
