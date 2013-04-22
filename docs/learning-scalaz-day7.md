  [day6]: http://eed3si9n.com/learning-scalaz-day6

On [day 6][day6] we reviewed `for` syntax and checked out the `Writer` monad and the reader monad, which is basically using functions as monads.

### Applicative Builder

One thing I snuck in while covering the reader monad is the Applicative builder `|@|`. On [day 2](http://eed3si9n.com/learning-scalaz-day2) we introduced `^(f1, f2) {...}` style that was introduced in 7.0.0-M3, but that does not seem to work for functions or any type constructor with two parameters.

The discussion on the Scalaz mailing list seems to suggest that `|@|` will be undeprecated, so that's the style we will be using, which looks like this:

<scala>
scala> (3.some |@| 5.some) {_ + _}
res18: Option[Int] = Some(8)

scala> val f = ({(_: Int) * 2} |@| {(_: Int) + 10}) {_ + _}
f: Int => Int = <function1>
</scala>

### Tasteful stateful computations

[Learn You a Haskell for Great Good](http://learnyouahaskell.com/for-a-few-monads-more) says:

> Haskell features a thing called the state monad, which makes dealing with stateful problems a breeze while still keeping everything nice and pure.

Let's implement the stack example. This time I am going to translate Haskell into Scala without making it into case class:

<scala>
scala> type Stack = List[Int]
defined type alias Stack

scala> def pop(stack: Stack): (Int, Stack) = stack match {
         case x :: xs => (x, xs)
       }
pop: (stack: Stack)(Int, Stack)

scala> def push(a: Int, stack: Stack): (Unit, Stack) = ((), a :: stack)
push: (a: Int, stack: Stack)(Unit, Stack)

scala> def stackManip(stack: Stack): (Int, Stack) = {
         val (_, newStack1) = push(3, stack)
         val (a, newStack2) = pop(newStack1)
         pop(newStack2)
       }
stackManip: (stack: Stack)(Int, Stack)

scala> stackManip(List(5, 8, 2, 1))
res0: (Int, Stack) = (5,List(8, 2, 1))
</scala>

### State and StateT

LYAHFGG:

>  We'll say that a stateful computation is a function that takes some state and returns a value along with some new state. That function would have the following type:

<haskell>
s -> (a, s)
</haskell>

The important thing to note is that unlike the general monads we've seen, `State` specifically wraps functions. Let's look at `State`'s definition in Scalaz:

<scala>
  type State[S, +A] = StateT[Id, S, A]

  // important to define here, rather than at the top-level, to avoid Scala 2.9.2 bug
  object State extends StateFunctions {
    def apply[S, A](f: S => (S, A)): State[S, A] = new StateT[Id, S, A] {
      def apply(s: S) = f(s)
    }
  }
</scala>

As with `Writer`, `State[S, +A]` is a type alias of `StateT[Id, S, A]`. Here's the simplified version of [`StateT`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/StateT.scala):

<scala>
trait StateT[F[+_], S, +A] { self =>
  /** Run and return the final value and state in the context of `F` */
  def apply(initial: S): F[(S, A)]

  /** An alias for `apply` */
  def run(initial: S): F[(S, A)] = apply(initial)

  /** Calls `run` using `Monoid[S].zero` as the initial state */
  def runZero(implicit S: Monoid[S]): F[(S, A)] =
    run(S.zero)
}
</scala>

We can construct a new state using `State` singleton:

<scala>
scala> State[List[Int], Int] { case x :: xs => (xs, x) }
res1: scalaz.State[List[Int],Int] = scalaz.package$State$$anon$1@19f58949
</scala>

Let's try implementing the stack using `State`:

<scala>
scala> type Stack = List[Int]
defined type alias Stack

scala> val pop = State[Stack, Int] {
         case x :: xs => (xs, x)
       }
pop: scalaz.State[Stack,Int]

scala> def push(a: Int) = State[Stack, Unit] {
         case xs => (a :: xs, ())
       }
push: (a: Int)scalaz.State[Stack,Unit]

scala> def stackManip: State[Stack, Int] = for {
         _ <- push(3)
         a <- pop
         b <- pop
       } yield(b)
stackManip: scalaz.State[Stack,Int]

scala> stackManip(List(5, 8, 2, 1))
res2: (Stack, Int) = (List(8, 2, 1),5)
</scala>

Using `State[List[Int], Int] {...}` we were able to abstract out the "extract state, and return value with a state" portion of the code. The powerful part is the fact that we can monadically chain each operations using `for` syntax without manually passing around the `Stack` values as demonstrated in `stackManip` above.

### Getting and setting state

LYAHFGG:

> The `Control.Monad.State` module provides a type class that's called `MonadState` and it features two pretty useful functions, namely `get` and `put`.

The `State` object extends `StateFunctions` trait, which defines a few helper functions:

<scala>
trait StateFunctions {
  def constantState[S, A](a: A, s: => S): State[S, A] =
    State((_: S) => (s, a))
  def state[S, A](a: A): State[S, A] =
    State((_ : S, a))
  def init[S]: State[S, S] = State(s => (s, s))
  def get[S]: State[S, S] = init
  def gets[S, T](f: S => T): State[S, T] = State(s => (s, f(s)))
  def put[S](s: S): State[S, Unit] = State(_ => (s, ()))
  def modify[S](f: S => S): State[S, Unit] = State(s => {
    val r = f(s);
    (r, ())
  })
  /**
   * Computes the difference between the current and previous values of `a`
   */
  def delta[A](a: A)(implicit A: Group[A]): State[A, A] = State{
    (prevA) =>
      val diff = A.minus(a, prevA)
      (diff, a)
  }
}
</scala>

These are confusing at first. But remember `State` monad encapsulates functions that takes a state and returns a pair of a value and a state. So `get` in the context of state simply means to retreive the state into the value:

<scala>
  def init[S]: State[S, S] = State(s => (s, s))
  def get[S]: State[S, S] = init
</scala>

And `put` in this context means to put some value into the state:

<scala>
  def put[S](s: S): State[S, Unit] = State(_ => (s, ()))
</scala>

To illustrate this point, let's implement `stackyStack` function.

<scala>
scala> def stackyStack: State[Stack, Unit] = for {
         stackNow <- get
         r <- if (stackNow === List(1, 2, 3)) put(List(8, 3, 1))
              else put(List(9, 2, 1))
       } yield r
stackyStack: scalaz.State[Stack,Unit]

scala> stackyStack(List(1, 2, 3))
res4: (Stack, Unit) = (List(8, 3, 1),())
</scala>

We can also implement `pop` and `push` in terms of `get` and `put`:

<scala>
scala> val pop: State[Stack, Int] = for {
         s <- get[Stack]
         val (x :: xs) = s
         _ <- put(xs)
       } yield x
pop: scalaz.State[Stack,Int] = scalaz.StateT$$anon$7@40014da3

scala> def push(x: Int): State[Stack, Unit] = for {
         xs <- get[Stack]
         r <- put(x :: xs)
       } yield r
push: (x: Int)scalaz.State[Stack,Unit]
</scala>

As you can see a monad on its own doesn't do much (encapsulate a function that returns a tuple), but by chaining them we can remove some boilerplates.

### Error error on the wall

LYAHFGG:

> The `Either e a` type on the other hand, allows us to incorporate a context of possible failure to our values while also being able to attach values to the failure, so that they can describe what went wrong or provide some other useful info regarding the failure.

### \/

We know `Either[A, B]` from the standard library, but Scalaz 7 implements its own `Either` equivalent named [`\/`](https://github.com/eed3si9n/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Either.scala):

<scala>
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
</scala>

These values are created using `right` and `left` method injected to all data types via `IdOps`:

<scala>
scala> 1.right[String]
res12: scalaz.\/[String,Int] = \/-(1)

scala> "error".left[Int]
res13: scalaz.\/[String,Int] = -\/(error)
</scala>

The `Either` type in Scala standard library is not a monad on its own, which means it does not implement `flatMap` method with or without Scalaz:

<scala>
scala> Left[String, Int]("boom") flatMap { x => Right[String, Int](x + 1) }
<console>:8: error: value flatMap is not a member of scala.util.Left[String,Int]
              Left[String, Int]("boom") flatMap { x => Right[String, Int](x + 1) }
                                        ^
</scala>

You have to call `right` method to turn it into `RightProjection`:

<scala>
scala> Left[String, Int]("boom").right flatMap { x => Right[String, Int](x + 1)}
res15: scala.util.Either[String,Int] = Left(boom)
</scala>

This is silly since the point of having `Either` is to report an error on the left. Scalaz's `\/` assumes that you'd mostly want right projection:

<scala>
scala> "boom".left[Int] >>= { x => (x + 1).right }
res18: scalaz.Unapply[scalaz.Bind,scalaz.\/[String,Int]]{type M[X] = scalaz.\/[String,X]; type A = Int}#M[Int] = -\/(boom)
</scala>

This is nice. Let's try using it in `for` syntax:

<scala>
scala> for {
         e1 <- "event 1 ok".right
         e2 <- "event 2 failed!".left[String]
         e3 <- "event 3 failed!".left[String]
       } yield (e1 |+| e2 |+| e3)
res24: scalaz.\/[String,String] = -\/(event 2 failed!)
</scala>

As you can see, the first failure rolls up as the final result. How do we get the value out of `\/`? First there's `isRight` and `isLeft` method to check which side we are on:

<scala>
scala> "event 1 ok".right.isRight
res25: Boolean = true

scala> "event 1 ok".right.isLeft
res26: Boolean = false
</scala>

For right side, we can use `getOrElse` and its symbolic alias `|` as follows:

<scala>
scala> "event 1 ok".right | "something bad"
res27: String = event 1 ok
</scala>

For left value, we can call `swap` method or it's symbolic alias `unary_~`:

<scala>
scala> ~"event 2 failed!".left[String] | "something good"
res28: String = event 2 failed!
</scala>

We can use `map` to modify the right side value:

<scala>
scala> "event 1 ok".right map {_ + "!"}
res31: scalaz.\/[Nothing,String] = \/-(event 1 ok!)
</scala>

To chain on the left side, there's `orElse`, which accepts `=> AA \/ BB` where `[AA >: A, BB >: B]`. The symbolic alias for `orElse` is `|||`:

<scala>
scala> "event 1 failed!".left ||| "retry event 1 ok".right 
res32: scalaz.\/[String,String] = \/-(retry event 1 ok)
</scala>

### Validation

Another data structure that's compared to `Either` in Scalaz is [`Validation`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Validation.scala):

<scala>
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
</scala>

At the first glance `Validation` looks similar to `\/`. They can even be converted back and forth using `validation` method and `disjunction` method.

[`ValidationV`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/ValidationV.scala) introduces `success[X]`, `successNel[X]`, `failure[X]`, and `failureNel[X]` methods to all data types (don't worry about the `Nel` thing for now):

<scala>
scala> "event 1 ok".success[String]
res36: scalaz.Validation[String,String] = Success(event 1 ok)

scala> "event 1 failed!".failure[String]
res38: scalaz.Validation[String,String] = Failure(event 1 failed!)
</scala>

What's different about `Validation` is that it is not a monad, but it's an applicative functor. Instead of chaining the result from first event to the next, `Validation` validates all events:

<scala>
scala> ("event 1 ok".success[String] |@| "event 2 failed!".failure[String] |@| "event 3 failed!".failure[String]) {_ + _ + _}
res44: scalaz.Unapply[scalaz.Apply,scalaz.Validation[String,String]]{type M[X] = scalaz.Validation[String,X]; type A = String}#M[String] = Failure(event 2 failed!event 3 failed!)
</scala>

It's a bit difficult to see, but the final result is `Failure(event 2 failed!event 3 failed!)`. Unlike `\/` monad which cut the calculation short, `Validation` keeps going and reports back all failures. This probably would be useful for validating user's input on an online bacon shop.

The problem, however, is that the error messages are mushed together into one string. Shouldn't it be something like a list?

### NonEmptyList

This is where [`NonEmptyList`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/NonEmptyList.scala) (or `Nel` for short) comes in:

<scala>
/** A singly-linked list that is guaranteed to be non-empty. */
sealed trait NonEmptyList[+A] {
  val head: A
  val tail: List[A]
  def <::[AA >: A](b: AA): NonEmptyList[AA] = nel(b, head :: tail)
  ...
}
</scala>

This is a wrapper trait for plain `List` that's guaranteed to be non-empty. Since there's at least one item in the list, `head` always works. `IdOps` adds `wrapNel` to all data types to create a `Nel`.

<scala>
scala> 1.wrapNel
res47: scalaz.NonEmptyList[Int] = NonEmptyList(1)
</scala>

Now does `successNel[X]` and `failureNel[X]` make sense?

<scala>
scala> "event 1 ok".successNel[String]
res48: scalaz.ValidationNEL[String,String] = Success(event 1 ok)

scala> "event 1 failed!".failureNel[String]
res49: scalaz.ValidationNEL[String,String] = Failure(NonEmptyList(event 1 failed!))

scala> ("event 1 ok".successNel[String] |@| "event 2 failed!".failureNel[String] |@| "event 3 failed!".failureNel[String]) {_ + _ + _}
res50: scalaz.Unapply[scalaz.Apply,scalaz.ValidationNEL[String,String]]{type M[X] = scalaz.ValidationNEL[String,X]; type A = String}#M[String] = Failure(NonEmptyList(event 2 failed!, event 3 failed!))
</scala>

In `Failure`, we were able to accumulate all failed messages.

We will pick it up from here later.
