
### Making monads

LYAHFGG:

> In this section, we're going to look at an example of how a type gets made, identified as a monad and then given the appropriate `Monad` instance. 
> ...
> What if we wanted to model a non-deterministic value like `[3,5,9]`, but we wanted to express that `3` has a 50% chance of happening and `5` and `9` both have a 25% chance of happening? 

Since Scala doesn't have a built-in rational, let's just use `Double`. Here's the case class:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

case class Prob[A](list: List[(A, Double)])

trait ProbInstances {
  implicit def probShow[A]: Show[Prob[A]] = Show.showA
}

case object Prob extends ProbInstances

// Exiting paste mode, now interpreting.

defined class Prob
defined trait ProbInstances
defined module Prob
```

> Is this a functor? Well, the list is a functor, so this should probably be a functor as well, because we just added some stuff to the list.

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

case class Prob[A](list: List[(A, Double)])

trait ProbInstances {
  implicit val probInstance = new Functor[Prob] {
    def map[A, B](fa: Prob[A])(f: A => B): Prob[B] =
      Prob(fa.list map { case (x, p) => (f(x), p) })
  }
  implicit def probShow[A]: Show[Prob[A]] = Show.showA
}

case object Prob extends ProbInstances

scala> Prob((3, 0.5) :: (5, 0.25) :: (9, 0.25) :: Nil) map {-_} 
res77: Prob[Int] = Prob(List((-3,0.5), (-5,0.25), (-9,0.25)))
```

Just like the book we are going to implement `flatten` first.

```scala
case class Prob[A](list: List[(A, Double)])

trait ProbInstances {
  def flatten[B](xs: Prob[Prob[B]]): Prob[B] = {
    def multall(innerxs: Prob[B], p: Double) =
      innerxs.list map { case (x, r) => (x, p * r) }
    Prob((xs.list map { case (innerxs, p) => multall(innerxs, p) }).flatten)
  }

  implicit val probInstance = new Functor[Prob] {
    def map[A, B](fa: Prob[A])(f: A => B): Prob[B] =
      Prob(fa.list map { case (x, p) => (f(x), p) })
  }
  implicit def probShow[A]: Show[Prob[A]] = Show.showA
}

case object Prob extends ProbInstances
```

This should be enough prep work for monad:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

case class Prob[A](list: List[(A, Double)])

trait ProbInstances {
  def flatten[B](xs: Prob[Prob[B]]): Prob[B] = {
    def multall(innerxs: Prob[B], p: Double) =
      innerxs.list map { case (x, r) => (x, p * r) }
    Prob((xs.list map { case (innerxs, p) => multall(innerxs, p) }).flatten)
  }

  implicit val probInstance = new Functor[Prob] with Monad[Prob] {
    def point[A](a: => A): Prob[A] = Prob((a, 1.0) :: Nil)
    def bind[A, B](fa: Prob[A])(f: A => Prob[B]): Prob[B] = flatten(map(fa)(f)) 
    override def map[A, B](fa: Prob[A])(f: A => B): Prob[B] =
      Prob(fa.list map { case (x, p) => (f(x), p) })
  }
  implicit def probShow[A]: Show[Prob[A]] = Show.showA
}

case object Prob extends ProbInstances

// Exiting paste mode, now interpreting.

defined class Prob
defined trait ProbInstances
defined module Prob
```

The book says it satisfies the monad laws. Let's implement the `Coin` example:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait Coin
case object Heads extends Coin
case object Tails extends Coin
implicit val coinEqual: Equal[Coin] = Equal.equalA

def coin: Prob[Coin] = Prob(Heads -> 0.5 :: Tails -> 0.5 :: Nil)
def loadedCoin: Prob[Coin] = Prob(Heads -> 0.1 :: Tails -> 0.9 :: Nil)

def flipThree: Prob[Boolean] = for {
  a <- coin
  b <- coin
  c <- loadedCoin
} yield { List(a, b, c) all {_ === Tails} }

// Exiting paste mode, now interpreting.

defined trait Coin
defined module Heads
defined module Tails
coin: Prob[Coin]
loadedCoin: Prob[Coin]
flipThree: Prob[Boolean]

scala> flipThree
res81: Prob[Boolean] = Prob(List((false,0.025), (false,0.225), (false,0.025), (false,0.225), (false,0.025), (false,0.225), (false,0.025), (true,0.225)))
```

So the probability of having all three coins on `Tails` even with a loaded coin is pretty low.

We will continue from here later.
