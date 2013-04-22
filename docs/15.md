  [day14]: http://eed3si9n.com/learning-scalaz-day14

<div class="floatingimage">
<img src="http://eed3si9n.com/images/openphoto-9038bw.jpg">
<div class="credit">Rodolfo Cartas for openphoto.net</div>
</div>

On [day 14][day14] we started hacking on Scalaz. First, typeclass instances for `Vector` was added to `import Scalaz._`. Next, we rolled back `<*>` to be infix `ap`. Finally, we added an implicit converter to unpack `A` as `[α]A`, which helps compiler find `Applicative[({type λ[α]=Int})#λ]`.

All three of the pull requests were accepted by the upstream! Here's how to sync up:

<div style="clear: both;"
<code>
$ git co scalaz-seven
$ git pull --rebase
</code>

Let's take a moment to see some of the typeclasses I was looking.

### Arrow

An arrow is the term used in category theory as an abstract notion of thing that behaves like a function. In Scalaz, these are `Function1[A, B]`, `PartialFunction[A, B]`, `Kleisli[F[_], A, B]`, and `CoKleisli[F[_], A, B]`. `Arrow` abstracts them all similar to the way other typeclasses abtracts containers.

Here is the typeclass contract for [`Arrow`](https://github.com/eed3si9n/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Arrow.scala):

<scala>
trait Arrow[=>:[_, _]] extends Category[=>:] { self =>
  def id[A]: A =>: A
  def arr[A, B](f: A => B): A =>: B
  def first[A, B, C](f: (A =>: B)): ((A, C) =>: (B, C))
}
</scala>

Looks like `Arrow[=>:[_, _]]` extends `Category[=>:]`.

### Category, ArrId, and Compose

Here's [`Category[=>:[_, _]]`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Category.scala):

<scala>
trait Category[=>:[_, _]] extends ArrId[=>:] with Compose[=>:] { self =>
  // no contract function
} 
</scala>

This extends [`ArrId[=>:]`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/ArrId.scala):

<scala>
trait ArrId[=>:[_, _]]  { self =>
  def id[A]: A =>: A
}
</scala>

`Category[=>:[_, _]]` also extends [`Compose[=>:]`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Compose.scala):

<scala>
trait Compose[=>:[_, _]]  { self =>
  def compose[A, B, C](f: B =>: C, g: A =>: B): (A =>: C)
}
</scala>

`compose` function composes two arrows into one. Using `compose`, `Compose` introduces the following [operators](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/ComposeSyntax.scala):

<scala>
trait ComposeOps[F[_, _],A, B] extends Ops[F[A, B]] {
  final def <<<[C](x: F[C, A]): F[C, B] = F.compose(self, x)
  final def >>>[C](x: F[B, C]): F[A, C] = F.compose(x, self)
}
</scala>

The meaning of `>>>` and `<<<` depends on the arrow, but for functions, it's the same as `andThen` and `compose`:

<scala>
scala> val f = (_:Int) + 1
f: Int => Int = <function1>

scala> val g = (_:Int) * 100
g: Int => Int = <function1>

scala> (f >>> g)(2)
res0: Int = 300

scala> (f <<< g)(2)
res1: Int = 201
</scala> 

### Arrow, again

The type signature of `Arrow[=>:[_, _]]` looks a bit odd, but this is no different than saying `Arrow[M[_, _]]`. The neat things about type constructor that takes two type parameters is that we can write `=>:[A, B]` as `A =>: B`.

`arr` function creates an arrow from a normal function, `id` returns an identity arrow, and `first` creates a new arrow from an existing arrow by expanding the input and output as pairs. 

Using the above functions, arrows introduces the following [operators](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/ArrowSyntax.scala):

<scala>
trait ArrowOps[F[_, _],A, B] extends Ops[F[A, B]] {
  final def ***[C, D](k: F[C, D]): F[(A, C), (B, D)] = F.splitA(self, k)
  final def &&&[C](k: F[A, C]): F[A, (B, C)] = F.combine(self, k)
  ...
}
</scala>

Let's read Haskell's [Arrow tutorial](http://www.haskell.org/haskellwiki/Arrow_tutorial):

> `(***)` combines two arrows into a new arrow by running the two arrows on a pair of values (one arrow on the first item of the pair and one arrow on the second item of the pair).

Here's an example:

<scala>
scala> (f *** g)(1, 2)
res3: (Int, Int) = (2,200)
</scala>

> `(&&&)` combines two arrows into a new arrow by running the two arrows on the same value:

Here's an example for `&&&`:

<scala>
scala> (f &&& g)(2)
res4: (Int, Int) = (3,200)
</scala>

Arrows I think can be useful if you need to add some context to functions and pairs.

### Unapply

One thing that I've been fighting the Scala compiler over is the lack of type inference support across the different kinded types like `F[M[_, _]]` and `F[M[_]]`, and `M[_]` and `F[M[_]]`.

For example, an instance of `Applicative[M[_]]` is `(* -> *) -> *` (a type constructor that takes another type constructor that that takes exactly one type). It's known that `Int => Int` can be treated as an applicative by treating it as `Int => A`:

<scala>
scala> Applicative[Function1[Int, Int]]
<console>:14: error: Int => Int takes no type parameters, expected: one
              Applicative[Function1[Int, Int]]
                          ^

scala> Applicative[({type l[A]=Function1[Int, A]})#l]
res14: scalaz.Applicative[[A]Int => A] = scalaz.std.FunctionInstances$$anon$2@56ae78ac
</scala>

This becomes annoying for `M[_,_]` like `Validation`. One of the way Scalaz helps you out is to provide meta-instances of typeclass instance called [`Unapply`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Unapply.scala).

<scala>
trait Unapply[TC[_[_]], MA] {
  /** The type constructor */
  type M[_]
  /** The type that `M` was applied to */
  type A
  /** The instance of the type class */
  def TC: TC[M]
  /** Evidence that MA =:= M[A] */
  def apply(ma: MA): M[A]
}
</scala>

When Scalaz method like `traverse` requires you to pass in `Applicative[M[_]]`, it instead could ask for `Unapply[Applicative, X]`. During compile time, Scalac can look through all the implicit converters to see if it can coerce `Function1[Int, Int]` into `M[A]` by fixing or adding a parameter and of course using an existing typeclass instance.

<scala>
scala> implicitly[Unapply[Applicative, Function1[Int, Int]]]
res15: scalaz.Unapply[scalaz.Applicative,Int => Int] = scalaz.Unapply_0$$anon$9@2e86566f
</scala>

The feature I added yesterday allows type `A` to be promoted as `M[A]` by adding a fake type constructor. This let us treat `Int` as `Applicative` easier. But because it still requires `TC0: TC[({type λ[α] = A0})#λ]` implicitly, it does not allow just any type to be promoted as `Applicative`.

<scala>
scala> implicitly[Unapply[Applicative, Int]]
res0: scalaz.Unapply[scalaz.Applicative,Int] = scalaz.Unapply_3$$anon$1@5179dc20

scala> implicitly[Unapply[Applicative, Any]]
<console>:14: error: Unable to unapply type `Any` into a type constructor of kind `M[_]` that is classified by the type class `scalaz.Applicative`
1) Check that the type class is defined by compiling `implicitly[scalaz.Applicative[<type constructor>]]`.
2) Review the implicits in object Unapply, which only cover common type 'shapes'
(implicit not found: scalaz.Unapply[scalaz.Applicative, Any])
              implicitly[Unapply[Applicative, Any]]
                        ^
</scala>

Works. The upshot of all this is that we can now rewrite the following a bit cleaner:

<scala>
scala> val failedTree: Tree[Validation[String, Int]] = 1.success[String].node(
         2.success[String].leaf, "boom".failure[Int].leaf)
failedTree: scalaz.Tree[scalaz.Validation[String,Int]] = <tree>

scala> failedTree.sequence[({type l[X]=Validation[String, X]})#l, Int]
res2: scalaz.Validation[java.lang.String,scalaz.Tree[Int]] = Failure(boom)
<scala>

Here's `sequenceU`:

<scala>
scala> failedTree.sequenceU
res3: scalaz.Validation[String,scalaz.Tree[Int]] = Failure(boom)
</scala>

Boom.

### parallel composition

With the change I made to `Unapply` monoidal applicative functor now works, but we still could not combine them:

<scala>
scala> val f = { (x: Int) => x + 1 }
f: Int => Int = <function1>

scala> val g = { (x: Int) => List(x, 5) }
g: Int => List[Int] = <function1>

scala> val h = f &&& g
h: Int => (Int, List[Int]) = <function1>

scala> List(1, 2, 3) traverseU f
res0: Int = 9

scala> List(1, 2, 3) traverseU g
res1: List[List[Int]] = List(List(1, 2, 3), List(1, 2, 5), List(1, 5, 3), List(1, 5, 5), List(5, 2, 3), List(5, 2, 5), List(5, 5, 3), List(5, 5, 5))

scala> List(1, 2, 3) traverseU h
res2: (Int, List[List[Int]]) = (9,List(List(1, 5), List(2, 5), List(3, 5)))
</scala>

Running `f` and `g` is working fine. The problem is the way pair is interpretted by `traverseU`. If I manually combined `f` and `g`, it would look like:

<scala>
scala> val h = { (x: Int) => (f(x), g(x)) }
h: Int => (Int, List[Int]) = <function1>
</scala>

And here is `Tuple2Functor`:

<scala>
private[scalaz] trait Tuple2Functor[A1] extends Functor[({type f[x] = (A1, x)})#f] {
  override def map[A, B](fa: (A1, A))(f: A => B) =
    (fa._1, f(fa._2))
}
</scala>

Scalaz does have a concept of product of applicative functors, which is available via `product` method available on `Apply` typeclass, however I don't think it's available as implicits because it's using pairs to encode it. At this point I am not sure if Scalaz has a way to implementing product of applicative functions (`A => M[B]`) as described in EIP:

<haskell>
data (m ⊠ n) a = Prod {pfst ::m a,psnd :: n a}
(⊗)::(Functor m,Functor n) ⇒ (a → m b) → (a → n b) → (a → (m ⊠ n) b)
(f ⊗ g) x = Prod (f x) (g x)
</haskell>

This could also be true for composition too. Let's branch from `scalaz-seven` branch:

<code>
$ git co scalaz-seven
Already on 'scalaz-seven'
$ git branch topic/appcompose
$ git co topic/appcompose
Switched to branch 'topic/appcompose'
</code>

We'll first store things into an actual type and then worry about making it elegant later.

<scala>
package scalaz

import Id._

trait XProduct[A, B] {
  def _1: A
  def _2: B
  override def toString: String = "XProduct(" + _1.toString + ", " + _2.toString + ")"
}

trait XProductInstances {
  implicit def productSemigroup[A1, A2](implicit A1: Semigroup[A1], A2: Semigroup[A2]): Semigroup[XProduct[A1, A2]] = new XProductSemigroup[A1, A2] {
    implicit def A1 = A1
    implicit def A2 = A2
  }  
  implicit def productFunctor[F[_], G[_]](implicit F0: Functor[F], G0: Functor[G]): Functor[({type λ[α] = XProduct[F[α], G[α]]})#λ] = new XProductFunctor[F, G] {
    def F = F0
    def G = G0
  }
  implicit def productPointed[F[_], G[_]](implicit F0: Pointed[F], G0: Pointed[G]): Pointed[({type λ[α] = XProduct[F[α], G[α]]})#λ] = new XProductPointed[F, G] {
    def F = F0
    def G = G0
  }
  implicit def productApply[F[_], G[_]](implicit F0: Apply[F], G0: Apply[G]): Apply[({type λ[α] = XProduct[F[α], G[α]]})#λ] = new XProductApply[F, G] {
    def F = F0
    def G = G0
  }
  implicit def productApplicativeFG[F[_], G[_]](implicit F0: Applicative[F], G0: Applicative[G]): Applicative[({type λ[α] = XProduct[F[α], G[α]]})#λ] = new XProductApplicative[F, G] {
    def F = F0
    def G = G0
  }
  implicit def productApplicativeFB[F[_], B](implicit F0: Applicative[F], B0: Applicative[({type λ[α] = B})#λ]): Applicative[({type λ[α] = XProduct[F[α], B]})#λ] = new XProductApplicative[F, ({type λ[α] = B})#λ] {
    def F = F0
    def G = B0
  }
  implicit def productApplicativeAG[A, G[_]](implicit A0: Applicative[({type λ[α] = A})#λ], G0: Applicative[G]): Applicative[({type λ[α] = XProduct[A, G[α]]})#λ] = new XProductApplicative[({type λ[α] = A})#λ, G] {
    def F = A0
    def G = G0
  }
  implicit def productApplicativeAB[A, B](implicit A0: Applicative[({type λ[α] = A})#λ], B0: Applicative[({type λ[α] = B})#λ]): Applicative[({type λ[α] = XProduct[A, B]})#λ] = new XProductApplicative[({type λ[α] = A})#λ, ({type λ[α] = B})#λ] {
    def F = A0
    def G = B0
  }  
}

trait XProductFunctions {
  def product[A, B](a1: A, a2: B): XProduct[A, B] = new XProduct[A, B] {
    def _1 = a1
    def _2 = a2
  }
}

object XProduct extends XProductFunctions with XProductInstances {
  def apply[A, B](a1: A, a2: B): XProduct[A, B] = product(a1, a2)
}
private[scalaz] trait XProductSemigroup[A1, A2] extends Semigroup[XProduct[A1, A2]] {
  implicit def A1: Semigroup[A1]
  implicit def A2: Semigroup[A2]
  def append(f1: XProduct[A1, A2], f2: => XProduct[A1, A2]) = XProduct(
    A1.append(f1._1, f2._1),
    A2.append(f1._2, f2._2)
    )
}
private[scalaz] trait XProductFunctor[F[_], G[_]] extends Functor[({type λ[α] = XProduct[F[α], G[α]]})#λ] {
  implicit def F: Functor[F]
  implicit def G: Functor[G]
  override def map[A, B](fa: XProduct[F[A], G[A]])(f: (A) => B): XProduct[F[B], G[B]] =
    XProduct(F.map(fa._1)(f), G.map(fa._2)(f))
}

private[scalaz] trait XProductPointed[F[_], G[_]] extends Pointed[({type λ[α] = XProduct[F[α], G[α]]})#λ] with XProductFunctor[F, G] {
  implicit def F: Pointed[F]
  implicit def G: Pointed[G]
  def point[A](a: => A): XProduct[F[A], G[A]] = XProduct(F.point(a), G.point(a))
}

private[scalaz] trait XProductApply[F[_], G[_]] extends Apply[({type λ[α] = XProduct[F[α], G[α]]})#λ] with XProductFunctor[F, G] {
  implicit def F: Apply[F]
  implicit def G: Apply[G]
  def ap[A, B](fa: => XProduct[F[A], G[A]])(f: => XProduct[F[A => B], G[A => B]]): XProduct[F[B], G[B]] =
    XProduct(F.ap(fa._1)(f._1), G.ap(fa._2)(f._2))
}

private[scalaz] trait XProductApplicative[F[_], G[_]] extends Applicative[({type λ[α] = XProduct[F[α], G[α]]})#λ] with XProductPointed[F, G] {
  implicit def F: Applicative[F]
  implicit def G: Applicative[G]
  def ap[A, B](fa: => XProduct[F[A], G[A]])(f: => XProduct[F[(A) => B], G[(A) => B]]): XProduct[F[B], G[B]] =
    XProduct(F.ap(fa._1)(f._1), G.ap(fa._2)(f._2))
}
</scala>

The implementation is mostly ripped from `Product.scala`, which uses `Tuple2`. Here's is the first attempt at using `XProduct`:

<scala>
scala> XProduct(1.some, 2.some) map {_ + 1}
<console>:14: error: Unable to unapply type `scalaz.XProduct[Option[Int],Option[Int]]` into a type constructor of kind `M[_]` that is classified by the type class `scalaz.Functor`
1) Check that the type class is defined by compiling `implicitly[scalaz.Functor[<type constructor>]]`.
2) Review the implicits in object Unapply, which only cover common type 'shapes'
(implicit not found: scalaz.Unapply[scalaz.Functor, scalaz.XProduct[Option[Int],Option[Int]]])
              XProduct(1.some, 2.some) map {_ + 1}
                      ^
</scala>

The error message is actually helpful if you know how to decode it. It's looking for the `Unapply` meta-instance. Likely the particular shape is not there. Here's the new unapply:

<scala>
  implicit def unapplyMFGA[TC[_[_]], F[_], G[_], M0[_, _], A0](implicit TC0: TC[({type λ[α] = M0[F[α], G[α]]})#λ]): Unapply[TC, M0[F[A0], G[A0]]] {
    type M[X] = M0[F[X], G[X]]
    type A = A0
  } = new Unapply[TC, M0[F[A0], G[A0]]] {
    type M[X] = M0[F[X], G[X]]
    type A = A0
    def TC = TC0
    def apply(ma: M0[F[A0], G[A0]]) = ma
  }
</scala>

Try again.

<scala>
scala> XProduct(1.some, 2.some) map {_ + 1}
res0: scalaz.Unapply[scalaz.Functor,scalaz.XProduct[Option[Int],Option[Int]]]{type M[X] = scalaz.XProduct[Option[X],Option[X]]; type A = Int}#M[Int] = XProduct(Some(2), Some(3))
</scala>

We can use it as normal applicative:

<scala>
scala> (XProduct(1, 2.some) |@| XProduct(3, none[Int])) {_ |+| (_: XProduct[Int, Option[Int]]) }
res1: scalaz.Unapply[scalaz.Apply,scalaz.XProduct[Int,Option[Int]]]{type M[X] = scalaz.XProduct[Int,Option[Int]]; type A = scalaz.XProduct[Int,Option[Int]]}#M[scalaz.XProduct[Int,Option[Int]]] = XProduct(4, Some(2))
</scala>

Let's rewrite word count example from the EIP.

<scala>
scala> val text = "the cat in the hat\n sat on the mat\n".toList
text: List[Char] = 
List(t, h, e,  , c, a, t,  , i, n,  , t, h, e,  , h, a, t, 
,  , s, a, t,  , o, n,  , t, h, e,  , m, a, t, 
)

scala> def count[A] = (a: A) => 1
count: [A]=> A => Int

scala> val charCount = count[Char]
charCount: Char => Int = <function1>

scala> text traverseU charCount
res10: Int = 35

scala> import scalaz.std.boolean.test
import scalaz.std.boolean.test

scala> val lineCount = (c: Char) => test(c === '\n')
lineCount: Char => Int = <function1>

scala> text traverseU lineCount
res11: Int = 2

scala> val wordCount = (c: Char) => for {
         x <- get[Boolean]
         val y = c =/= ' '
         _ <- put(y)
       } yield test(y /\ !x)
wordCount: Char => scalaz.StateT[scalaz.Id.Id,Int,Int] = <function1>

scala> (text traverseU wordCount) eval false count(_ > 0)
res25: Int = 9

scala> text traverseU { (c: Char) => XProduct(charCount(c), lineCount(c)) }
res26: scalaz.XProduct[Int,Int] = XProduct(35, 2)
</scala>

Now it's able to combine applicative functions in parallel. What happens if you use a pair?

<scala>
scala> text traverseU { (c: Char) => (charCount(c), lineCount(c)) }
res27: (Int, List[Int]) = (35,List(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1))
</scala>

Ha! However, the problem with `Unapply` is that it won't work for more complex structure:

<scala>
scala> text traverseU { (c: Char) => XProduct(charCount(c), wordCount(c)) }
<console>:19: error: Unable to unapply type `scalaz.XProduct[Int,scalaz.StateT[scalaz.Id.Id,Boolean,Int]]` into a type constructor of kind `M[_]` that is classified by the type class `scalaz.Applicative`
1) Check that the type class is defined by compiling `implicitly[scalaz.Applicative[<type constructor>]]`.
2) Review the implicits in object Unapply, which only cover common type 'shapes'
(implicit not found: scalaz.Unapply[scalaz.Applicative, scalaz.XProduct[Int,scalaz.StateT[scalaz.Id.Id,Boolean,Int]]])
              text traverseU { (c: Char) => XProduct(charCount(c), wordCount(c)) }
                   ^
</scala>

Once it all works out, it would be cool to have `@>>>` and `@&&&` operator on `Arrow` or `Function1` that does the applicative composition as it's described in EIP.

We'll cover some other topic later.
