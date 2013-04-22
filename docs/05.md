  [day4]: http://eed3si9n.com/learning-scalaz-day4

On [day 4][day4] we reviewed typeclass laws like Functor laws and used ScalaCheck to validate on arbitrary examples of a typeclass. We also looked at three different ways of using `Option` as Monoid, and looked at `Foldable` that can `foldMap` etc.

### A fist full of Monads

We get to start a new chapter today on [Learn You a Haskell for Great Good](http://learnyouahaskell.com/a-fistful-of-monads).

> Monads are a natural extension applicative functors, and they provide a solution to the following problem: If we have a value with context, `m a`, how do we apply it to a function that takes a normal `a` and returns a value with a context.

The equivalent is called `Monad` in Scalaz. Here's [the typeclass contract](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Monad.scala):

<scala>
trait Monad[F[_]] extends Applicative[F] with Bind[F] { self =>
  ////
}
</scala>

It extends `Applicative` and `Bind`. So let's look at `Bind`.

### Bind

Here's [`Bind`'s contract](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Bind.scala):

<scala>
trait Bind[F[_]] extends Apply[F] { self =>
  /** Equivalent to `join(map(fa)(f))`. */
  def bind[A, B](fa: F[A])(f: A => F[B]): F[B]
}
</scala>

And here are [the operators](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/BindSyntax.scala):

<scala>
/** Wraps a value `self` and provides methods related to `Bind` */
trait BindOps[F[_],A] extends Ops[F[A]] {
  implicit def F: Bind[F]
  ////
  import Liskov.<~<

  def flatMap[B](f: A => F[B]) = F.bind(self)(f)
  def >>=[B](f: A => F[B]) = F.bind(self)(f)
  def ∗[B](f: A => F[B]) = F.bind(self)(f)
  def join[B](implicit ev: A <~< F[B]): F[B] = F.bind(self)(ev(_))
  def μ[B](implicit ev: A <~< F[B]): F[B] = F.bind(self)(ev(_))
  def >>[B](b: F[B]): F[B] = F.bind(self)(_ => b)
  def ifM[B](ifTrue: => F[B], ifFalse: => F[B])(implicit ev: A <~< Boolean): F[B] = {
    val value: F[Boolean] = Liskov.co[F, A, Boolean](ev)(self)
    F.ifM(value, ifTrue, ifFalse)
  }
  ////
}
</scala>

It introduces `flatMap` operator and its symbolic aliases `>>=` and `∗`. We'll worry about the other operators later. We are use to `flapMap` from the standard library:

<scala>
scala> 3.some flatMap { x => (x + 1).some }
res2: Option[Int] = Some(4)

scala> (none: Option[Int]) flatMap { x => (x + 1).some }
res3: Option[Int] = None
</scala>

### Monad

Back to `Monad`:

<scala>
trait Monad[F[_]] extends Applicative[F] with Bind[F] { self =>
  ////
}
</scala>

Unlike Haskell, `Monad[F[_]]` exntends `Applicative[F[_]]` so there's no `return` vs `pure` issues. They both use `point`.

<scala>
scala> Monad[Option].point("WHAT")
res5: Option[String] = Some(WHAT)

scala> 9.some flatMap { x => Monad[Option].point(x * 10) }
res6: Option[Int] = Some(90)

scala> (none: Option[Int]) flatMap { x => Monad[Option].point(x * 10) }
res7: Option[Int] = None
</scala>

### Walk the line

LYAHFGG:

> Let's say that [Pierre] keeps his balance if the number of birds on the left side of the pole and on the right side of the pole is within three. So if there's one bird on the right side and four birds on the left side, he's okay. But if a fifth bird lands on the left side, then he loses his balance and takes a dive.

Now let's try implementing `Pole` example from the book.

<scala>
scala> type Birds = Int
defined type alias Birds

scala> case class Pole(left: Birds, right: Birds)
defined class Pole
</scala>

I don't think it's common to alias `Int` like this in Scala, but we'll go with the flow. I am going to turn `Pole` into a case class so I can implement `landLeft` and `landRight` as methods:

<scala>
scala> case class Pole(left: Birds, right: Birds) {
         def landLeft(n: Birds): Pole = copy(left = left + n)
         def landRight(n: Birds): Pole = copy(right = right + n) 
       }
defined class Pole
</scala>

I think it looks better with some OO:

<scala>
scala> Pole(0, 0).landLeft(2)
res10: Pole = Pole(2,0)

scala> Pole(1, 2).landRight(1)
res11: Pole = Pole(1,3)

scala> Pole(1, 2).landRight(-1)
res12: Pole = Pole(1,1)
</scala>

We can chain these too:

<scala>
scala> Pole(0, 0).landLeft(1).landRight(1).landLeft(2)
res13: Pole = Pole(3,1)

scala> Pole(0, 0).landLeft(1).landRight(4).landLeft(-1).landRight(-2)
res15: Pole = Pole(0,2)
</scala>

As the book says, an intermediate value have failed but the calculation kept going. Now let's introduce failures as `Option[Pole]`:

<scala>
scala> case class Pole(left: Birds, right: Birds) {
         def landLeft(n: Birds): Option[Pole] = 
           if (math.abs((left + n) - right) < 4) copy(left = left + n).some
           else none
         def landRight(n: Birds): Option[Pole] =
           if (math.abs(left - (right + n)) < 4) copy(right = right + n).some
           else none
       }
defined class Pole


scala> Pole(0, 0).landLeft(2)
res16: Option[Pole] = Some(Pole(2,0))

scala> Pole(0, 3).landLeft(10)
res17: Option[Pole] = None
</scala>

Let's try the chaining using `flatMap`:

<scala>
scala> Pole(0, 0).landRight(1) flatMap {_.landLeft(2)}
res18: Option[Pole] = Some(Pole(2,1))

scala> (none: Option[Pole]) flatMap {_.landLeft(2)}
res19: Option[Pole] = None

scala> Monad[Option].point(Pole(0, 0)) flatMap {_.landRight(2)} flatMap {_.landLeft(2)} flatMap {_.landRight(2)}
res21: Option[Pole] = Some(Pole(2,4))
</scala>

Note the use of `Monad[Option].point(...)` here to start the initial value in `Option` context. We can also try the `>>=` alias to make it look more monadic:

<scala>
scala> Monad[Option].point(Pole(0, 0)) >>= {_.landRight(2)} >>= {_.landLeft(2)} >>= {_.landRight(2)}
res22: Option[Pole] = Some(Pole(2,4))
</scala>

Let's see if monadic chaining simlulates the pole balancing better:

<scala>
scala> Monad[Option].point(Pole(0, 0)) >>= {_.landLeft(1)} >>= {_.landRight(4)} >>= {_.landLeft(-1)} >>= {_.landRight(-2)}
res23: Option[Pole] = None
</scala>

It works.

### Banana on wire

LYAHFGG:

> We may also devise a function that ignores the current number of birds on the balancing pole and just makes Pierre slip and fall. We can call it `banana`.

Here's the `banana` that always fails:

<scala>
scala> case class Pole(left: Birds, right: Birds) {
         def landLeft(n: Birds): Option[Pole] = 
           if (math.abs((left + n) - right) < 4) copy(left = left + n).some
           else none
         def landRight(n: Birds): Option[Pole] =
           if (math.abs(left - (right + n)) < 4) copy(right = right + n).some
           else none
         def banana: Option[Pole] = none
       }
defined class Pole

scala> Monad[Option].point(Pole(0, 0)) >>= {_.landLeft(1)} >>= {_.banana} >>= {_.landRight(1)}
res24: Option[Pole] = None
</scala>

LYAHFGG:

> Instead of making functions that ignore their input and just return a predetermined monadic value, we can use the `>>` function.

Here's how `>>` behaves with `Option`:

<scala>
scala> (none: Option[Int]) >> 3.some
res25: Option[Int] = None

scala> 3.some >> 4.some
res26: Option[Int] = Some(4)

scala> 3.some >> (none: Option[Int])
res27: Option[Int] = None
</scala>

Let's try replacing `banana` with `>> (none: Option[Pole])`:

<scala>
scala> Monad[Option].point(Pole(0, 0)) >>= {_.landLeft(1)} >> (none: Option[Pole]) >>= {_.landRight(1)}
<console>:26: error: missing parameter type for expanded function ((x$1) => x$1.landLeft(1))
              Monad[Option].point(Pole(0, 0)) >>= {_.landLeft(1)} >> (none: Option[Pole]) >>= {_.landRight(1)}
                                                   ^
</scala>

The type inference broke down all the sudden. The problem is likely the operator precedence. [Programming in Scala](http://www.artima.com/pins1ed/basic-types-and-operations.html) says:

> The one exception to the precedence rule, alluded to above, concerns assignment operators, which end in an equals character. If an operator ends in an equals character (`=`), and the operator is not one of the comparison operators `<=`, `>=`, `==`, or `!=`, then the precedence of the operator is the same as that of simple assignment (`=`). That is, it is lower than the precedence of any other operator.

Note: The above description is incomplete. Another exception from the assignment operator rule is if it starts with (`=`) like `===`.

Because `>>=` (bind) ends in equals character, its precedence is the lowest, which forces `({_.landLeft(1)} >> (none: Option[Pole]))` to evaluate first. There are a few unpalatable work arounds. First we can use dot-and-parens like normal method calls:

<scala>
scala> Monad[Option].point(Pole(0, 0)).>>=({_.landLeft(1)}).>>(none: Option[Pole]).>>=({_.landRight(1)})
res9: Option[Pole] = None
</scala>

Or recognize the precedence issue and place parens around just the right place:

<scala>
scala> (Monad[Option].point(Pole(0, 0)) >>= {_.landLeft(1)}) >> (none: Option[Pole]) >>= {_.landRight(1)}
res10: Option[Pole] = None
</scala>

Both yield the right result. By the way, changing `>>=` to `flatMap` is not going to help since `>>` still has higher precedence.

### for syntax

LYAHFGG:

> Monads in Haskell are so useful that they got their own special syntax called `do` notation.

First, let write the nested lambda:

<scala>
scala> 3.some >>= { x => "!".some >>= { y => (x.shows + y).some } }
res14: Option[String] = Some(3!)
</scala>

By using `>>=`, any part of the calculation can fail:

<scala>
scala> 3.some >>= { x => (none: Option[String]) >>= { y => (x.shows + y).some } }
res17: Option[String] = None

scala> (none: Option[Int]) >>= { x => "!".some >>= { y => (x.shows + y).some } }
res16: Option[String] = None

scala> 3.some >>= { x => "!".some >>= { y => (none: Option[String]) } }
res18: Option[String] = None
</scala>

Instead of the `do` notation in Haskell, Scala has `for` syntax, which does the same thing:

<scala>
scala> for {
         x <- 3.some
         y <- "!".some
       } yield (x.shows + y)
res19: Option[String] = Some(3!)
</scala>

LYAHFGG:

> In a `do` expression, every line that isn't a `let` line is a monadic value. 

I think this applies true for Scala's `for` syntax too.

### Pierre returns

LYAHFGG:

> Our tightwalker's routine can also be expressed with `do` notation.

<scala>
scala> def routine: Option[Pole] =
         for {
           start <- Monad[Option].point(Pole(0, 0))
           first <- start.landLeft(2)
           second <- first.landRight(2)
           third <- second.landLeft(1)
         } yield third
routine: Option[Pole]

scala> routine
res20: Option[Pole] = Some(Pole(3,2))
</scala>

We had to extract `third` since `yield` expects `Pole` not `Option[Pole]`.

LYAHFGG:

> If we want to throw the Pierre a banana peel in `do` notation, we can do the following:

<scala>
scala> def routine: Option[Pole] =
         for {
           start <- Monad[Option].point(Pole(0, 0))
           first <- start.landLeft(2)
           _ <- (none: Option[Pole])
           second <- first.landRight(2)
           third <- second.landLeft(1)
         } yield third
routine: Option[Pole]

scala> routine
res23: Option[Pole] = None
</scala>

### Pattern matching and failure

LYAHFGG:

> In `do` notation, when we bind monadic values to names, we can utilize pattern matching, just like in let expressions and function parameters.

<scala>
scala> def justH: Option[Char] =
         for {
           (x :: xs) <- "hello".toList.some
         } yield x
justH: Option[Char]

scala> justH
res25: Option[Char] = Some(h)
</scala>

> When pattern matching fails in a do expression, the `fail` function is called. It's part of the `Monad` type class and it enables failed pattern matching to result in a failure in the context of the current monad instead of making our program crash. 

<scala>
scala> def wopwop: Option[Char] =
         for {
           (x :: xs) <- "".toList.some
         } yield x
wopwop: Option[Char]

scala> wopwop
res28: Option[Char] = None
</scala>

The failed pattern matching returns `None` here. This is an interesting aspect of `for` syntax that I haven't thought about, but totally makes sense.

### List Monad

LYAHFGG:

> On the other hand, a value like `[3,8,9]` contains several results, so we can view it as one value that is actually many values at the same time. Using lists as applicative functors showcases this non-determinism nicely.

Let's look at using `List` as Applicatives again (This notation might require Scalaz 7.0.0-M3):

<scala>
scala> ^(List(1, 2, 3), List(10, 100, 100)) {_ * _}
res29: List[Int] = List(10, 100, 100, 20, 200, 200, 30, 300, 300)
</scala>

> let's try feeding a non-deterministic value to a function:

<scala>
scala> List(3, 4, 5) >>= {x => List(x, -x)}
res30: List[Int] = List(3, -3, 4, -4, 5, -5)
</scala>

So in this monadic view, `List` context represent mathematical value that could have multiple solutions. Other than that manipulating `List`s using `for` notation is just like plain Scala:

<scala>
scala> for {
         n <- List(1, 2)
         ch <- List('a', 'b')
       } yield (n, ch)
res33: List[(Int, Char)] = List((1,a), (1,b), (2,a), (2,b))
</scala>

### MonadPlus and the guard function

Scala's `for` notation allows filtering:

<scala>
scala> for {
         x <- 1 |-> 50 if x.shows contains '7'
       } yield x
res40: List[Int] = List(7, 17, 27, 37, 47)
</scala>

LYAHFGG:

> The `MonadPlus` type class is for monads that can also act as monoids.

Here's [the typeclass contract for `MonadPlus`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/MonadPlus.scala):

<scala>
trait MonadPlus[F[_]] extends Monad[F] with ApplicativePlus[F] { self =>
  ...
}
</scala>

### Plus, PlusEmpty, and ApplicativePlus

It extends [`ApplicativePlus`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/ApplicativePlus.scala):

<scala>
trait ApplicativePlus[F[_]] extends Applicative[F] with PlusEmpty[F] { self =>
  ...
}
</scala>

And that extends [`PlusEmpty`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/PlusEmpty.scala):

<scala>
trait PlusEmpty[F[_]] extends Plus[F] { self =>
  ////
  def empty[A]: F[A]
}
</scala>

And that extends [`Plus`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/PlusEmpty.scala):

<scala>
trait Plus[F[_]]  { self =>
  def plus[A](a: F[A], b: => F[A]): F[A]
}
</scala>

Similar to `Semigroup[A]` and `Monoid[A]`, `Plus[F[_]]` and `PlusEmpty[F[_]]` requires theier instances to implement `plus` and `empty`, but at the type constructor ( `F[_]`) level. 

`Plus` introduces `<+>` operator to append two containers:

<scala>
scala> List(1, 2, 3) <+> List(4, 5, 6)
res43: List[Int] = List(1, 2, 3, 4, 5, 6)
</scala>

### MonadPlus again

`MonadPlus` introduces `filter` operation.

<scala>
scala> (1 |-> 50) filter { x => x.shows contains '7' }
res46: List[Int] = List(7, 17, 27, 37, 47)
</scala>

### A knight's quest

LYAHFGG:

> Here's a problem that really lends itself to being solved with non-determinism. Say you have a chess board and only one knight piece on it. We want to find out if the knight can reach a certain position in three moves.

Instead of type aliasing a pair, let's make this into a case class again:

<scala>
scala> case class KnightPos(c: Int, r: Int)
defined class KnightPos
</scala>

Heres the function to calculate all of his next next positions:

<scala>
scala> case class KnightPos(c: Int, r: Int) {
         def move: List[KnightPos] =
           for {
             KnightPos(c2, r2) <- List(KnightPos(c + 2, r - 1), KnightPos(c + 2, r + 1),
               KnightPos(c - 2, r - 1), KnightPos(c - 2, r + 1),
               KnightPos(c + 1, r - 2), KnightPos(c + 1, r + 2),
               KnightPos(c - 1, r - 2), KnightPos(c - 1, r + 2)) if (
               ((1 |-> 8) contains c2) && ((1 |-> 8) contains r2))
           } yield KnightPos(c2, r2)
       }
defined class KnightPos

scala> KnightPos(6, 2).move
res50: List[KnightPos] = List(KnightPos(8,1), KnightPos(8,3), KnightPos(4,1), KnightPos(4,3), KnightPos(7,4), KnightPos(5,4))

scala> KnightPos(8, 1).move
res51: List[KnightPos] = List(KnightPos(6,2), KnightPos(7,3))
</scala>

The answers look good. Now we implement chaining this three times:

<scala>
scala> case class KnightPos(c: Int, r: Int) {
         def move: List[KnightPos] =
           for {
             KnightPos(c2, r2) <- List(KnightPos(c + 2, r - 1), KnightPos(c + 2, r + 1),
             KnightPos(c - 2, r - 1), KnightPos(c - 2, r + 1),
             KnightPos(c + 1, r - 2), KnightPos(c + 1, r + 2),
             KnightPos(c - 1, r - 2), KnightPos(c - 1, r + 2)) if (
             ((1 |-> 8) element c2) && ((1 |-> 8) contains r2))
           } yield KnightPos(c2, r2)
         def in3: List[KnightPos] =
           for {
             first <- move
             second <- first.move
             third <- second.move
           } yield third
         def canReachIn3(end: KnightPos): Boolean = in3 contains end
       }
defined class KnightPos

scala> KnightPos(6, 2) canReachIn3 KnightPos(6, 1)
res56: Boolean = true

scala> KnightPos(6, 2) canReachIn3 KnightPos(7, 3)
res57: Boolean = false
</scala>

### Monad laws

#### Left identity

LYAHFGG:

> The first monad law states that if we take a value, put it in a default context with `return` and then feed it to a function by using `>>=`, it's the same as just taking the value and applying the function to it. 

To put this in Scala,

<scala>
// (Monad[F].point(x) flatMap {f}) assert_=== f(x)

scala> (Monad[Option].point(3) >>= { x => (x + 100000).some }) assert_=== 3 |> { x => (x + 100000).some }
</scala>

#### Right identity

> The second law states that if we have a monadic value and we use `>>=` to feed it to `return`, the result is our original monadic value.

<scala>
// (m forMap {Monad[F].point(_)}) assert_=== m

scala> ("move on up".some flatMap {Monad[Option].point(_)}) assert_=== "move on up".some
</scala>

#### Associativity

> The final monad law says that when we have a chain of monadic function applications with `>>=`, it shouldn't matter how they're nested. 

<scala>
// (m flatMap f) flatMap g assert_=== m flatMap { x => f(x) flatMap {g} }

scala> Monad[Option].point(Pole(0, 0)) >>= {_.landRight(2)} >>= {_.landLeft(2)} >>= {_.landRight(2)}
res76: Option[Pole] = Some(Pole(2,4))

scala> Monad[Option].point(Pole(0, 0)) >>= { x =>
       x.landRight(2) >>= { y =>
       y.landLeft(2) >>= { z =>
       z.landRight(2)
       }}}
res77: Option[Pole] = Some(Pole(2,4))
</scala>

Scalaz 7 expresses these laws as the following:

<scala>
  trait MonadLaw extends ApplicativeLaw {
    /** Lifted `point` is a no-op. */
    def rightIdentity[A](a: F[A])(implicit FA: Equal[F[A]]): Boolean = FA.equal(bind(a)(point(_: A)), a)
    /** Lifted `f` applied to pure `a` is just `f(a)`. */
    def leftIdentity[A, B](a: A, f: A => F[B])(implicit FB: Equal[F[B]]): Boolean = FB.equal(bind(point(a))(f), f(a))
    /**
     * As with semigroups, monadic effects only change when their
     * order is changed, not when the order in which they're
     * combined changes.
     */
    def associativeBind[A, B, C](fa: F[A], f: A => F[B], g: B => F[C])(implicit FC: Equal[F[C]]): Boolean =
      FC.equal(bind(bind(fa)(f))(g), bind(fa)((a: A) => bind(f(a))(g)))
  }
</scala>

Here's how to check if `Option` conforms to the Monad laws. Run `sbt test:console` with `build.sbt` we used in day 4:

<scala>
scala> monad.laws[Option].check
+ monad.applicative.functor.identity: OK, passed 100 tests.
+ monad.applicative.functor.associative: OK, passed 100 tests.
+ monad.applicative.identity: OK, passed 100 tests.
+ monad.applicative.composition: OK, passed 100 tests.
+ monad.applicative.homomorphism: OK, passed 100 tests.
+ monad.applicative.interchange: OK, passed 100 tests.
+ monad.right identity: OK, passed 100 tests.
+ monad.left identity: OK, passed 100 tests.
+ monad.associativity: OK, passed 100 tests.
</scala>

Looking good, `Option`. We'll pick it up from here.
