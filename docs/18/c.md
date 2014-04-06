
## Stackless Scala with Free Monads

Now that we have general understanding of Free monads, let's watch Rúnar's presentation from Scala Days 2012: [Stackless Scala With Free Monads](http://skillsmatter.com/podcast/scala/stackless-scala-free-monads). I recommend watching the talk before reading the paper, but it's easier to quote the paper version [Stackless Scala With Free Monads](http://days2012.scala-lang.org/sites/days2012/files/bjarnason_trampolines.pdf).

Rúnar starts out with a code that uses State monad to zip a list with index. It blows the stack when the list is larger than the stack limit. Then he introduces tranpoline, which is a single loop that drives the entire program.

```scala
sealed trait Trampoline [+ A] {
  final def runT : A =
    this match {
      case More (k) => k().runT
      case Done (v) => v
    }
}
case class More[+A](k: () => Trampoline[A])
  extends Trampoline[A]
case class Done [+A](result: A)
  extends Trampoline [A]
```

In the above code, `Function0` `k` is used as a thunk for the next step.

To extend its usage for State monad, he then reifies `flatMap` into a data structure called `FlatMap`:

```scala
case class FlatMap [A,+B](
  sub: Trampoline [A],
  k: A => Trampoline[B]) extends Trampoline[B]
```

Next, it is revealed that `Trampoline` is a free monad of `Function0`. Here's how it's defined in Scalaz 7:

```scala
  type Trampoline[+A] = Free[Function0, A]
```

### Free monads

In addition, Rúnar introduces several data structures that can form useful free monad:

```scala
type Pair[+A] = (A, A)
type BinTree[+A] = Free[Pair, A]

type Tree[+A] = Free[List, A]

type FreeMonoid[+A] = Free[({type λ[+α] = (A,α)})#λ, Unit]

type Trivial[+A] = Unit
type Option[+A] = Free[Trivial, A]
```

There's also iteratees implementation based on free monads. Finally, he summarizes free monads in nice bullet points:

> - A model for any recursive data type with data at the leaves.
> - A free monad is an expression tree with variables at the leaves and flatMap is variable substitution.

### Trampoline

Using Trampoline any program can be transformed into a stackless one. Let's try implementing `even` and `odd` from the talk using Scalaz 7's `Trampoline`. `Free` object extends `FreeFunction` which defines a few useful functions for tramplining:

```scala
trait FreeFunctions {
  /** Collapse a trampoline to a single step. */
  def reset[A](r: Trampoline[A]): Trampoline[A] = { val a = r.run; return_(a) }

  /** Suspend the given computation in a single step. */
  def return_[S[+_], A](value: => A)(implicit S: Pointed[S]): Free[S, A] =
    Suspend[S, A](S.point(Return[S, A](value)))

  def suspend[S[+_], A](value: => Free[S, A])(implicit S: Pointed[S]): Free[S, A] =
    Suspend[S, A](S.point(value))

  /** A trampoline step that doesn't do anything. */
  def pause: Trampoline[Unit] =
    return_(())

  ...
}
```

We can call `import Free._` to use these.

```scala
scala> import Free._
import Free._

scala> :paste
// Entering paste mode (ctrl-D to finish)

def even[A](ns: List[A]): Trampoline[Boolean] =
  ns match {
    case Nil => return_(true)
    case x :: xs => suspend(odd(xs))
  }
def odd[A](ns: List[A]): Trampoline[Boolean] =
  ns match {
    case Nil => return_(false)
    case x :: xs => suspend(even(xs))
  }

// Exiting paste mode, now interpreting.

even: [A](ns: List[A])scalaz.Free.Trampoline[Boolean]
odd: [A](ns: List[A])scalaz.Free.Trampoline[Boolean]

scala> even(List(1, 2, 3)).run
res118: Boolean = false

scala> even(0 |-> 3000).run
res119: Boolean = false
```

This was surprisingly simple. 

### List using Free

Let's try defining "List" using Free.

```scala
scala> type FreeMonoid[A] = Free[({type λ[+α] = (A,α)})#λ, Unit]
defined type alias FreeMonoid

scala> def cons[A](a: A): FreeMonoid[A] = Free.Suspend[({type λ[+α] = (A,α)})#λ, Unit]((a, Free.Return[({type λ[+α] = (A,α)})#λ, Unit](())))
cons: [A](a: A)FreeMonoid[A]

scala> cons(1)
res0: FreeMonoid[Int] = Suspend((1,Return(())))

scala> cons(1) >>= {_ => cons(2)}
res1: scalaz.Free[[+α](Int, α),Unit] = Gosub(Suspend((1,Return(()))),<function1>)
```

As a way of interpretting the result, let's try converting this to a standard List:

```scala
scala> def toList[A](list: FreeMonoid[A]): List[A] =
         list.resume.fold(
           { case (x: A, xs: FreeMonoid[A]) => x :: toList(xs) },
           { _ => Nil })

scala> toList(res1)
res4: List[Int] = List(1, 2)
```

That's it for today.
