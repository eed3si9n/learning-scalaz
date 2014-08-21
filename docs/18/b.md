  [1]: http://www.haskellforall.com/2012/06/you-could-have-invented-free-monads.html
  [2]: https://github.com/eed3si9n/learning-scalaz/blob/master/docs/18/b.md

I've updated this post quite a bit based on the guidance by Rúnar. See [source][2] in github for older revisions.

## Free Monad

What I want to explore today actually is the Free monad by reading Gabriel Gonzalez's [Why free monads matter][1]:

> Let's try to come up with some sort of abstraction that represents the essence of a syntax tree. ... Our toy language will only have three commands:

```
output b -- prints a "b" to the console
bell     -- rings the computer's bell
done     -- end of execution
```

> So we represent it as a syntax tree where subsequent commands are leaves of prior commands:

```haskell
data Toy b next =
    Output b next
  | Bell next
  | Done
```

Here's `Toy` translated into Scala as is:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait Toy[+A, +Next]
case class Output[A, Next](a: A, next: Next) extends Toy[A, Next]
case class Bell[Next](next: Next) extends Toy[Nothing, Next]
case class Done() extends Toy[Nothing, Nothing]

// Exiting paste mode, now interpreting.

scala> Output('A', Done())
res0: Output[Char,Done] = Output(A,Done())

scala> Bell(Output('A', Done()))
res1: Bell[Output[Char,Done]] = Bell(Output(A,Done()))
```

### CharToy

WFMM's DSL takes the type of output data as one of the type parameters, so it's able to handle any output types. As demonstrated above as `Toy`, Scala can do this too. But doing so unnecessarily complicates the demonstration of of `Free` because of Scala's handling of partially applied types. So we'll first hardcode the data type to `Char` as follows:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait CharToy[+Next]
object CharToy {
  case class CharOutput[Next](a: Char, next: Next) extends CharToy[Next]
  case class CharBell[Next](next: Next) extends CharToy[Next]
  case class CharDone() extends CharToy[Nothing]

  def output[Next](a: Char, next: Next): CharToy[Next] = CharOutput(a, next)
  def bell[Next](next: Next): CharToy[Next] = CharBell(next)
  def done: CharToy[Nothing] = CharDone()
}

// Exiting paste mode, now interpreting.

scala> import CharToy._
import CharToy._

scala> output('A', done)
res0: CharToy[CharToy[Nothing]] = CharOutput(A,CharDone())

scala> bell(output('A', done))
res1: CharToy[CharToy[CharToy[Nothing]]] = CharBell(CharOutput(A,CharDone()))
```

I've added helper functions lowercase `output`, `bell`, and `done` to unify the types to `CharToy`.

### Fix

WFMM:

> but unfortunately this doesn't work because every time I want to add a command, it changes the type.

Let's define `Fix`:


```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

case class Fix[F[_]](f: F[Fix[F]])
object Fix {
  def fix(toy: CharToy[Fix[CharToy]]) = Fix[CharToy](toy)
}

// Exiting paste mode, now interpreting.

scala> import Fix._
import Fix._

scala> fix(output('A', fix(done)))
res4: Fix[CharToy] = Fix(CharOutput(A,Fix(CharDone())))

scala> fix(bell(fix(output('A', fix(done)))))
res5: Fix[CharToy] = Fix(CharBell(Fix(CharOutput(A,Fix(CharDone())))))
```

Again, `fix` is provided so that the type inference works.

### FixE

We are also going to try to implement `FixE`, which adds exception to this. Since `throw` and `catch` are reserverd, I am renaming them to `throwy` and `catchy`:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait FixE[F[_], E]
object FixE {
  case class Fix[F[_], E](f: F[FixE[F, E]]) extends FixE[F, E]
  case class Throwy[F[_], E](e: E) extends FixE[F, E]   

  def fix[E](toy: CharToy[FixE[CharToy, E]]): FixE[CharToy, E] =
  　　Fix[CharToy, E](toy)
  def throwy[F[_], E](e: E): FixE[F, E] = Throwy(e)
  def catchy[F[_]: Functor, E1, E2](ex: => FixE[F, E1])
  　　(f: E1 => FixE[F, E2]): FixE[F, E2] = ex match {
    case Fix(x)    => Fix[F, E2](Functor[F].map(x) {catchy(_)(f)})
    case Throwy(e) => f(e)
  }
}

// Exiting paste mode, now interpreting.
```

> We can only use this if Toy b is a functor, so we muddle around until we find something that type-checks (and satisfies the Functor laws).

Let's define `Functor` for `CharToy`:

```scala
scala> implicit val charToyFunctor: Functor[CharToy] = new Functor[CharToy] {
         def map[A, B](fa: CharToy[A])(f: A => B): CharToy[B] = fa match {
           case o: CharOutput[A] => CharOutput(o.a, f(o.next))
           case b: CharBell[A]   => CharBell(f(b.next))
           case CharDone()       => CharDone()
         }
       }
charToyFunctor: scalaz.Functor[CharToy] = \$anon\$1@7bc135fe
```

Here's the sample usage:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

import FixE._
case class IncompleteException()
def subroutine = fix[IncompleteException](
  output('A', 
    throwy[CharToy, IncompleteException](IncompleteException())))
def program = catchy[CharToy, IncompleteException, Nothing](subroutine) { _ =>
  fix[Nothing](bell(fix[Nothing](done)))
}
```

The fact that we need to supply type parameters everywhere is a bit unfortunate.

### Free monads part 1

WFMM:

> our `FixE` already exists, too, and it's called the Free monad:

```haskell
data Free f r = Free (f (Free f r)) | Pure r
```

> As the name suggests, it is automatically a monad (if `f` is a functor):

```haskell
instance (Functor f) => Monad (Free f) where
    return = Pure
    (Free x) >>= f = Free (fmap (>>= f) x)
    (Pure r) >>= f = f r
```

> The `return` was our `Throw`, and `(>>=)` was our `catch`. 

The corresponding structure in Scalaz is called `Free`:

```scala
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
```

In Scalaz version, `Free` constructor is called `Free.Suspend` and `Pure` is called `Free.Return`. Let's re-implement `CharToy` commands based on `Free`:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait CharToy[+Next]
object CharToy {
  case class CharOutput[Next](a: Char, next: Next) extends CharToy[Next]
  case class CharBell[Next](next: Next) extends CharToy[Next]
  case class CharDone() extends CharToy[Nothing]

  implicit val charToyFunctor: Functor[CharToy] = new Functor[CharToy] {
    def map[A, B](fa: CharToy[A])(f: A => B): CharToy[B] = fa match {
        case o: CharOutput[A] => CharOutput(o.a, f(o.next))
        case b: CharBell[A]   => CharBell(f(b.next))
        case CharDone()       => CharDone()
      }
    }

  def output(a: Char): Free[CharToy, Unit] =
    Free.Suspend(CharOutput(a, Free.Return[CharToy, Unit](())))
  def bell: Free[CharToy, Unit] =
    Free.Suspend(CharBell(Free.Return[CharToy, Unit](())))
  def done: Free[CharToy, Unit] = Free.Suspend(CharDone())
}

// Exiting paste mode, now interpreting.

defined trait CharToy
defined module CharToy
```

> I'll be damned if that's not a common pattern we can abstract.

Let's add `liftF` refactoring. We also need a `return` equivalent, which we'll call `pointed`.

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait CharToy[+Next]
object CharToy {
  case class CharOutput[Next](a: Char, next: Next) extends CharToy[Next]
  case class CharBell[Next](next: Next) extends CharToy[Next]
  case class CharDone() extends CharToy[Nothing]

  implicit val charToyFunctor: Functor[CharToy] = new Functor[CharToy] {
    def map[A, B](fa: CharToy[A])(f: A => B): CharToy[B] = fa match {
        case o: CharOutput[A] => CharOutput(o.a, f(o.next))
        case b: CharBell[A]   => CharBell(f(b.next))
        case CharDone()       => CharDone()
      }
    }
  private def liftF[F[+_]: Functor, R](command: F[R]): Free[F, R] =
    Free.Suspend[F, R](Functor[F].map(command) { Free.Return[F, R](_) })
  def output(a: Char): Free[CharToy, Unit] =
    liftF[CharToy, Unit](CharOutput(a, ()))
  def bell: Free[CharToy, Unit] = liftF[CharToy, Unit](CharBell(()))
  def done: Free[CharToy, Unit] = liftF[CharToy, Unit](CharDone())
  def pointed[A](a: A) = Free.Return[CharToy, A](a)
}

// Exiting paste mode, now interpreting.
```

Here's the command sequence:

```scala
scala> import CharToy._
import CharToy._

scala> val subroutine = output('A')
subroutine: scalaz.Free[CharToy,Unit] = Suspend(CharOutput(A,Return(())))

scala> val program = for {
         _ <- subroutine
         _ <- bell
         _ <- done
       } yield ()
program: scalaz.Free[CharToy,Unit] = Gosub(<function0>,<function1>)
```

> This is where things get magical. We now have `do` notation for something that hasn't even been interpreted yet: it's pure data. 

Next we'd like to define `showProgram` to prove that what we have is just data. WFMM defines `showProgram` using simple pattern matching, but it doesn't quite work that way for our Free. See the definition of `flatMap`:

```scala
  final def flatMap[B](f: A => Free[S, B]): Free[S, B] = this match {
    case Gosub(a, g) => Gosub(a, (x: Any) => Gosub(g(x), f))
    case a           => Gosub(a, f)
  }
```

Instead of recalculating a new `Return` or `Suspend` it's just creating `Gosub` structure. There's `resume` method that evaluates `Gosub` and returns `\/`, so using that we can implement `showProgram` as:

```scala
scala> def showProgram[R: Show](p: Free[CharToy, R]): String =
         p.resume.fold({
           case CharOutput(a, next) =>
             "output " + Show[Char].shows(a) + "\n" + showProgram(next)
           case CharBell(next) =>
             "bell " + "\n" + showProgram(next)
           case CharDone() =>
             "done\n"
         },
         { r: R => "return " + Show[R].shows(r) + "\n" }) 
showProgram: [R](p: scalaz.Free[CharToy,R])(implicit evidence\$1: scalaz.Show[R])String

scala> showProgram(program)
res12: String = 
"output A
bell 
done
"
```

Here's the pretty printer:

```scala
scala> def pretty[R: Show](p: Free[CharToy, R]) = print(showProgram(p))
pretty: [R](p: scalaz.Free[CharToy,R])(implicit evidence\$1: scalaz.Show[R])Unit

scala> pretty(output('A'))
output A
return ()
```

Now is the moment of truth. Does this monad generated using `Free` satisfy monad laws?

```scala
scala> pretty(output('A'))
output A
return ()

scala> pretty(pointed('A') >>= output)
output A
return ()

scala> pretty(output('A') >>= pointed)
output A
return ()

scala> pretty((output('A') >> done) >> output('C'))
output A
done

scala> pretty(output('A') >> (done >> output('C')))
output A
done
```

Looking good. Also notice the "abort" semantics of `done`.

### Free monads part 2

WFMM:

```haskell
data Free f r = Free (f (Free f r)) | Pure r
data List a   = Cons  a (List a  )  | Nil
```

> In other words, we can think of a free monad as just being a list of functors. The `Free` constructor behaves like a `Cons`, prepending a functor to the list, and the `Pure` constructor behaves like `Nil`, representing an empty list (i.e. no functors).

And here's part 3.

### Free monads part 3

WFMM:

> The free monad is the interpreter's best friend. Free monads "free the interpreter" as much as possible while still maintaining the bare minimum necessary to form a monad.

On the flip side, from the program writer's point of view, free monads do not give anything but being sequential. The interpreter needs to provide some `run` function to make it useful. The point, I think, is that given a data structure that satisfies `Functor`, `Free` provides minimal monads automatically.

Another way of looking at it is that `Free` monad provides a way of building a syntax tree given a container.
