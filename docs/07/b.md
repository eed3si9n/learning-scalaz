---
out: State.html
---

### Tasteful stateful computations

[Learn You a Haskell for Great Good](http://learnyouahaskell.com/for-a-few-monads-more) says:

> Haskell features a thing called the state monad, which makes dealing with stateful problems a breeze while still keeping everything nice and pure.

Let's implement the stack example. This time I am going to translate Haskell into Scala without making it into case class:

```scala
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
```

### State and StateT

LYAHFGG:

>  We'll say that a stateful computation is a function that takes some state and returns a value along with some new state. That function would have the following type:

```haskell
s -> (a, s)
```

The important thing to note is that unlike the general monads we've seen, `State` specifically wraps functions. Let's look at `State`'s definition in Scalaz:

```scala
  type State[S, +A] = StateT[Id, S, A]

  // important to define here, rather than at the top-level, to avoid Scala 2.9.2 bug
  object State extends StateFunctions {
    def apply[S, A](f: S => (S, A)): State[S, A] = new StateT[Id, S, A] {
      def apply(s: S) = f(s)
    }
  }
```

As with `Writer`, `State[S, +A]` is a type alias of `StateT[Id, S, A]`. Here's the simplified version of [`StateT`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/StateT.scala):

```scala
trait StateT[F[+_], S, +A] { self =>
  /** Run and return the final value and state in the context of `F` */
  def apply(initial: S): F[(S, A)]

  /** An alias for `apply` */
  def run(initial: S): F[(S, A)] = apply(initial)

  /** Calls `run` using `Monoid[S].zero` as the initial state */
  def runZero(implicit S: Monoid[S]): F[(S, A)] =
    run(S.zero)
}
```

We can construct a new state using `State` singleton:

```scala
scala> State[List[Int], Int] { case x :: xs => (xs, x) }
res1: scalaz.State[List[Int],Int] = scalaz.package$State$$anon$1@19f58949
```

Let's try implementing the stack using `State`:

```scala
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
```

Using `State[List[Int], Int] {...}` we were able to abstract out the "extract state, and return value with a state" portion of the code. The powerful part is the fact that we can monadically chain each operations using `for` syntax without manually passing around the `Stack` values as demonstrated in `stackManip` above.

### Getting and setting state

LYAHFGG:

> The `Control.Monad.State` module provides a type class that's called `MonadState` and it features two pretty useful functions, namely `get` and `put`.

The `State` object extends `StateFunctions` trait, which defines a few helper functions:

```scala
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
```

These are confusing at first. But remember `State` monad encapsulates functions that takes a state and returns a pair of a value and a state. So `get` in the context of state simply means to retreive the state into the value:

```scala
  def init[S]: State[S, S] = State(s => (s, s))
  def get[S]: State[S, S] = init
```

And `put` in this context means to put some value into the state:

```scala
  def put[S](s: S): State[S, Unit] = State(_ => (s, ()))
```

To illustrate this point, let's implement `stackyStack` function.

```scala
scala> def stackyStack: State[Stack, Unit] = for {
         stackNow <- get
         r <- if (stackNow === List(1, 2, 3)) put(List(8, 3, 1))
              else put(List(9, 2, 1))
       } yield r
stackyStack: scalaz.State[Stack,Unit]

scala> stackyStack(List(1, 2, 3))
res4: (Stack, Unit) = (List(8, 3, 1),())
```

We can also implement `pop` and `push` in terms of `get` and `put`:

```scala
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
```

As you can see a monad on its own doesn't do much (encapsulate a function that returns a tuple), but by chaining them we can remove some boilerplates.
