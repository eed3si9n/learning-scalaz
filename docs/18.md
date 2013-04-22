  [day17]: http://eed3si9n.com/learning-scalaz-day17

On [day 17][day17] we looked at IO monad as a way of abstracting side effects, and Iteratees as a way of handling streams. And the series ended.

### Func

I wanted to continue exploring a better way to compose applicative functions, and came up with a wrapper called `AppFunc`:

<scala>
val f = AppFuncU { (x: Int) => x + 1 }
val g = AppFuncU { (x: Int) => List(x, 5) }
(f @&&& g) traverse List(1, 2, 3)
</scala>

After sending this in as [a pull request](https://github.com/scalaz/scalaz/pull/161) Lars Hupel ([@larsr_h](https://twitter.com/larsr_h)) suggested that I generalize the concept using typelevel module, so I expanded it to `Func`:

<scala>
/**
 * Represents a function `A => F[B]` where `[F: TC]`.
 */
trait Func[F[_], TC[F[_]] <: Functor[F], A, B] {
  def runA(a: A): F[B]
  implicit def TC: KTypeClass[TC]
  implicit def F: TC[F]
  ...
}
</scala>

Using this, `AppFunc` becomes `Func` with `Applicative` in the second type parameter. Lars still wants to expand it composition into general `HList`, but I am optimistic that this will be part of Scalaz 7 eventually.

### Interpreter

What I want to explore today actually is the Free monad by reading Gabriel Gonzalez's [Why free monads matter](http://www.haskellforall.com/2012/06/you-could-have-invented-free-monads.html):

> Let's try to come up with some sort of abstraction that represents the essence of a syntax tree. ... Our toy language will only have three commands:

<haskell>
output b -- prints a "b" to the console
bell     -- rings the computer's bell
done     -- end of execution
</haskell>

Here's `Toy` translated into Scala:

<scala>
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait Toy[A]
case class Output[A, B](a: A, next: B) extends Toy[A]
case class Bell[A, B](next: B) extends Toy[A]
case class Done[A]() extends Toy[A]

// Exiting paste mode, now interpreting.

defined trait Toy
defined class Output
defined class Bell
defined class Done

scala> Output('A', Done())
res0: Output[Char,Done[Nothing]] = Output(A,Done())

scala> Bell(Output('A', Done()))
res1: Bell[Nothing,Output[Char,Done[Nothing]]] = Bell(Output(A,Done()))
</scala>

WFMM:

> but unfortunately this doesn't work because every time I want to add a command, it changes the type.

I don't know if this is a problem in Scala since they all extend `Toy`, but let's play along and define `Fix`:

<scala>
scala> case class Fix[F[_]](f: F[Fix[F]])
defined class Fix

scala> Fix[({type λ[α] = Toy[Char]})#λ](Output('A', Fix[({type λ[α] = Toy[Char]})#λ](Done())))
res4: Fix[[α]Toy[Char]] = Fix(Output(A,Fix(Done())))

scala> Fix[({type λ[α] = Toy[Char]})#λ](Bell(Fix[({type λ[α] = Toy[Char]})#λ](Output('A', Fix[({type λ[α] = Toy[Char]})#λ](Done())))))
res11: Fix[[α]Toy[Char]] = Fix(Bell(Fix(Output(A,Fix(Done())))))
</scala>

We are also going to try to implement `FixE`, which adds exception to this.

<scala>
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait FixE[F[_], E]
case class Fix[F[_], E](f: F[FixE[F, E]]) extends FixE[F, E]
case class Throw[F[_], E](e: E) extends FixE[F, E] 

// Exiting paste mode, now interpreting.

defined trait FixE
defined class Fix
defined class Throw
</scala>

Since `catch` is a reserverd word, I am going to rename this to `catchy`:

<scala>
scala> import scalaz._
import scalaz._

scala> def catchy[F[_]: Functor, E1, E2](ex: => FixE[F, E1])(f: E1 => FixE[F, E2]): FixE[F, E2] = ex match {
         case Fix(x)   => Fix[F, E2](Functor[F].map(x) {catchy(_)(f)})
         case Throw(e) => f(e)
       }
catchy: [F[_], E1, E2](ex: => FixE[F,E1])(f: E1 => FixE[F,E2])(implicit evidence$1: scalaz.Functor[F])FixE[F,E2]

scala> implicit def ToyFunctor[A1]: Functor[({type λ[α] = Toy[A1]})#λ] = new Functor[({type λ[α] = Toy[A1]})#λ] {
         def map[A, B](fa: Toy[A1])(f: A => B): Toy[A1] = fa match {
           case o: Output[A1, A] => Output(o.a, f(o.next))
           case b: Bell[A1, A]   => Bell(f(b.next))
           case Done()           => Done()
         }
       }
ToyFunctor: [A1]=> scalaz.Functor[[α]Toy[A1,α]]
</scala>

Here's the sample usage:

<scala>
scala> case class IncompleteException()
defined class IncompleteException

scala> def subroutine = Fix[({type λ[α] = Toy[Char]})#λ, IncompleteException](Output('A', Throw[({type λ[α] = Toy[Char]})#λ, IncompleteException](IncompleteException())))
subroutine: Fix[[α]Toy[Char],IncompleteException]

scala> def program = catchy[({type λ[α] = Toy[Char]})#λ, IncompleteException, Nothing](subroutine) { _ =>
         Fix[({type λ[α] = Toy[Char]})#λ, Nothing](Bell(Fix[({type λ[α] = Toy[Char]})#λ, Nothing](Done())))
       }
program: FixE[[α]Toy[Char],Nothing]
</scala>

### Free monads part 1

WFMM:

> our `FixE` already exists, too, and it's called the Free monad:

<haskell>
data Free f r = Free (f (Free f r)) | Pure r
</haskell>

> As the name suggests, it is automatically a monad (if `f` is a functor):

<haskell>
instance (Functor f) => Monad (Free f) where
    return = Pure
    (Free x) >>= f = Free (fmap (>>= f) x)
    (Pure r) >>= f = f r
</haskell>

The corresponding structure in Scalaz is called `Free`:

<scala>
sealed abstract class Free[S[+_], +A](implicit S: Functor[S]) {
  final def map[B](f: A => B): Free[S, B] =
    flatMap(a => Return(f(a)))

  final def flatMap[B](f: A => Free[S, B]): Free[S, B] = this match {
    case Gosub(a, g) => Gosub(a, (x: Any) => Gosub(g(x), f))
    case a           => Gosub(a, f)
  }
  ...
}

object Free extends FreeInstances {
  /** Return from the computation with the given value. */
  case class Return[S[+_]: Functor, +A](a: A) extends Free[S, A]

  /** Suspend the computation with the given suspension. */
  case class Suspend[S[+_]: Functor, +A](a: S[Free[S, A]]) extends Free[S, A]

  /** Call a subroutine and continue with the given function. */
  case class Gosub[S[+_]: Functor, A, +B](a: Free[S, A],
                                          f: A => Free[S, B]) extends Free[S, B]
}

trait FreeInstances {
  implicit def freeMonad[S[+_]:Functor]: Monad[({type f[x] = Free[S, x]})#f] =
    new Monad[({type f[x] = Free[S, x]})#f] {
      def point[A](a: => A) = Return(a)
      override def map[A, B](fa: Free[S, A])(f: A => B) = fa map f
      def bind[A, B](a: Free[S, A])(f: A => Free[S, B]) = a flatMap f
    }
}
</scala>

In Scalaz version, `Free` constructor is called `Free.Suspend` and `Pure` is called `Free.Return`. Let's implement `liftF`, and port `Toy` commands:

<scala>
scala> def output[A](a: A): Free[({type λ[+α] = Toy[A]})#λ, Unit] =
         Free.Suspend[({type λ[+α] = Toy[A]})#λ, Unit](Output(a, Free.Return[({type λ[+α] = Toy[A]})#λ, Unit](())))
output: [A](a: A)scalaz.Free[[+α]Toy[A],Unit]

scala> def liftF[F[+_]: Functor, R](command: F[R]): Free[F, R] =
         Free.Suspend[F, R](Functor[F].map(command) {Free.Return[F, R](_)})
liftF: [F[+_], R](command: F[R])(implicit evidence$1: scalaz.Functor[F])scalaz.Free[F,R]

scala> def output[A](a: A) = liftF[({type λ[+α] = Toy[A]})#λ, Unit](Output(a, ()))
output: [A](a: A)scalaz.Free[[+α]Toy[A],Unit]

scala> def bell[A] = liftF[({type λ[+α] = Toy[A]})#λ, Unit](Bell(()))
bell: [A]=> scalaz.Free[[+α]Toy[A],Unit]

scala> def done[A] = liftF[({type λ[+α] = Toy[A]})#λ, Unit](Done())
done: [A]=> scalaz.Free[[+α]Toy[A],Unit]
</scala>

Let's try sequencing the commands:

<scala>
scala> val subroutine = output('A')
subroutine: scalaz.Free[[+α]Toy[Char],Unit] = Suspend(Output(A,Return(())))

scala> val program = for {
         _ <- subroutine
         _ <- bell[Char]
         _ <- done[Char]
       } yield ()
program: scalaz.Free[[+α]Toy[Char],Unit] = Gosub(Suspend(Output(A,Return(()))),<function1>)
</scala>

The `showProgram` doesn't quite work as is for this Free. See the definition of `flatMap`:

<scala>
  final def flatMap[B](f: A => Free[S, B]): Free[S, B] = this match {
    case Gosub(a, g) => Gosub(a, (x: Any) => Gosub(g(x), f))
    case a           => Gosub(a, f)
  }
</scala>

Instead of recalculating a new `Return` or `Suspend` it's just creating `Gosub` structure. There's `resume` method that evaluates `Gosub` and returns `\/`, so using that we can implement `showProgram` as:

<scala>
scala> def showProgram[A: Show, R: Show](p: Free[({type λ[+α] = Toy[A]})#λ, R]): String =
         p.resume.fold({
           case Output(a: A, next: Free[({type λ[+α] = Toy[A]})#λ, R]) =>
             "output " + Show[A].shows(a) + "\n" + showProgram(next)
           case Bell(next: Free[({type λ[+α] = Toy[A]})#λ, R]) =>
             "bell " + "\n" + showProgram(next)
           case d: Done[A] =>
             "done\n"
         },
         { r: R => "return " + Show[R].shows(r) + "\n" }) 
showProgram: [A, R](p: scalaz.Free[[+α]Toy[A],R])(implicit evidence$1: scalaz.Show[A], implicit evidence$2: scalaz.Show[R])String

scala> showProgram(program)
res101: String = 
"output A
bell 
done
"
</scala>

Here's the pretty printer:

<scala>
scala> def pretty[A: Show, R: Show](p: Free[({type λ[+α] = Toy[A]})#λ, R]) = print(showProgram(p))
pretty: [A, R](p: scalaz.Free[[+α]Toy[A],R])(implicit evidence$1: scalaz.Show[A], implicit evidence$2: scalaz.Show[R])Unit

scala> pretty(output('A'))
output A
return ()

scala> pretty(output('A') >>= { _ => done[Char]})
output A
done
</scala>

Let's skip to part 2.

### Free monads part 2

WFMM:

<haskell>
data Free f r = Free (f (Free f r)) | Pure r
data List a   = Cons  a (List a  )  | Nil
</haskell>

> In other words, we can think of a free monad as just being a list of functors. The `Free` constructor behaves like a `Cons`, prepending a functor to the list, and the `Pure` constructor behaves like `Nil`, representing an empty list (i.e. no functors).

And here's part 3.

### Free monads part 3

WFMM:

> The free monad is the interpreter's best friend. Free monads "free the interpreter" as much as possible while still maintaining the bare minimum necessary to form a monad.

On the flip side, from the program writer's point of view, free monads do not give anything but being sequential. The interpreter needs to provide some `run` function to make it useful. The point, I think, is that given a data structure that satisfies `Functor`, `Free` provides minimal monads automatically.

Another way of looking at it is that `Free` monad provides a way of building a syntax tree given a container.

### Stackless Scala with Free Monads

Now that we have general understanding of Free monads, let's watch Rúnar's presentation from Scala Days 2012: [Stackless Scala With Free Monads](http://skillsmatter.com/podcast/scala/stackless-scala-free-monads). I recommend watching the talk before reading the paper, but it's easier to quote the paper version [Stackless Scala With Free Monads](http://days2012.scala-lang.org/sites/days2012/files/bjarnason_trampolines.pdf).

Rúnar starts out with a code that uses State monad to zip a list with index. It blows the stack when the list is larger than the stack limit. Then he introduces tranpoline, which is a single loop that drives the entire program.

<scala>
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
</scala>

In the above code, `Function0` `k` is used as a thunk for the next step.

To extend its usage for State monad, he then reifies `flatMap` into a data structure called `FlatMap`:

<scala>
case class FlatMap [A,+B](
  sub: Trampoline [A],
  k: A => Trampoline[B]) extends Trampoline[B]
</scala>

Next, it is revealed that `Trampoline` is a free monad of `Function0`. Here's how it's defined in Scalaz 7:

<scala>
  type Trampoline[+A] = Free[Function0, A]
</scala>

### Free monads

In addition, Rúnar introduces several data structures that can form useful free monad:

<scala>
type Pair[+A] = (A, A)
type BinTree[+A] = Free[Pair, A]

type Tree[+A] = Free[List, A]

type FreeMonoid[+A] = Free[({type λ[+α] = (A,α)})#λ, Unit]

type Trivial[+A] = Unit
type Option[+A] = Free[Trivial, A]
</scala>

There's also iteratees implementation based on free monads. Finally, he summarizes free monads in nice bullet points:

> - A model for any recursive data type with data at the leaves.
> - A free monad is an expression tree with variables at the leaves and flatMap is variable substitution.

### Trampoline

Using Trampoline any program can be transformed into a stackless one. Let's try implementing `even` and `odd` from the talk using Scalaz 7's `Trampoline`. `Free` object extends `FreeFunction` which defines a few useful functions for tramplining:

<scala>
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
</scala>

We can call `import Free._` to use these.

<scala>
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
</scala>

This was surprisingly simple. 

### List using Free

Let's try defining "List" using Free.

<scala>
scala> type FreeMonoid[A] = Free[({type λ[+α] = (A,α)})#λ, Unit]
defined type alias FreeMonoid

scala> def cons[A](a: A): FreeMonoid[A] = Free.Suspend[({type λ[+α] = (A,α)})#λ, Unit]((a, Free.Return[({type λ[+α] = (A,α)})#λ, Unit](())))
cons: [A](a: A)FreeMonoid[A]

scala> cons(1)
res0: FreeMonoid[Int] = Suspend((1,Return(())))

scala> cons(1) >>= {_ => cons(2)}
res1: scalaz.Free[[+α](Int, α),Unit] = Gosub(Suspend((1,Return(()))),<function1>)
</scala>

As a way of interpretting the result, let's try converting this to a standard List:

<scala>
scala> def toList[A](list: FreeMonoid[A]): List[A] =
         list.resume.fold(
           { case (x: A, xs: FreeMonoid[A]) => x :: toList(xs) },
           { _ => Nil })

scala> toList(res1)
res4: List[Int] = List(1, 2)
</scala>

That's it for today.
