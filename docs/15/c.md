
### parallel composition

With the change I made to `Unapply` monoidal applicative functor now works, but we still could not combine them:

```scala
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
```

Running `f` and `g` is working fine. The problem is the way pair is interpretted by `traverseU`. If I manually combined `f` and `g`, it would look like:

```scala
scala> val h = { (x: Int) => (f(x), g(x)) }
h: Int => (Int, List[Int]) = <function1>
```

And here is `Tuple2Functor`:

```scala
private[scalaz] trait Tuple2Functor[A1] extends Functor[({type f[x] = (A1, x)})#f] {
  override def map[A, B](fa: (A1, A))(f: A => B) =
    (fa._1, f(fa._2))
}
```

Scalaz does have a concept of product of applicative functors, which is available via `product` method available on `Apply` typeclass, however I don't think it's available as implicits because it's using pairs to encode it. At this point I am not sure if Scalaz has a way to implementing product of applicative functions (`A => M[B]`) as described in EIP:

```haskell
data (m ⊠ n) a = Prod {pfst ::m a,psnd :: n a}
(⊗)::(Functor m,Functor n) ⇒ (a → m b) → (a → n b) → (a → (m ⊠ n) b)
(f ⊗ g) x = Prod (f x) (g x)
```

This could also be true for composition too. Let's branch from `scalaz-seven` branch:

```
$ git co scalaz-seven
Already on 'scalaz-seven'
$ git branch topic/appcompose
$ git co topic/appcompose
Switched to branch 'topic/appcompose'
```

We'll first store things into an actual type and then worry about making it elegant later.

```scala
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
```

The implementation is mostly ripped from `Product.scala`, which uses `Tuple2`. Here's is the first attempt at using `XProduct`:

```scala
scala> XProduct(1.some, 2.some) map {_ + 1}
<console>:14: error: Unable to unapply type `scalaz.XProduct[Option[Int],Option[Int]]` into a type constructor of kind `M[_]` that is classified by the type class `scalaz.Functor`
1) Check that the type class is defined by compiling `implicitly[scalaz.Functor[<type constructor>]]`.
2) Review the implicits in object Unapply, which only cover common type 'shapes'
(implicit not found: scalaz.Unapply[scalaz.Functor, scalaz.XProduct[Option[Int],Option[Int]]])
              XProduct(1.some, 2.some) map {_ + 1}
                      ^
```

The error message is actually helpful if you know how to decode it. It's looking for the `Unapply` meta-instance. Likely the particular shape is not there. Here's the new unapply:

```scala
  implicit def unapplyMFGA[TC[_[_]], F[_], G[_], M0[_, _], A0](implicit TC0: TC[({type λ[α] = M0[F[α], G[α]]})#λ]): Unapply[TC, M0[F[A0], G[A0]]] {
    type M[X] = M0[F[X], G[X]]
    type A = A0
  } = new Unapply[TC, M0[F[A0], G[A0]]] {
    type M[X] = M0[F[X], G[X]]
    type A = A0
    def TC = TC0
    def apply(ma: M0[F[A0], G[A0]]) = ma
  }
```

Try again.

```scala
scala> XProduct(1.some, 2.some) map {_ + 1}
res0: scalaz.Unapply[scalaz.Functor,scalaz.XProduct[Option[Int],Option[Int]]]{type M[X] = scalaz.XProduct[Option[X],Option[X]]; type A = Int}#M[Int] = XProduct(Some(2), Some(3))
```

We can use it as normal applicative:

```scala
scala> (XProduct(1, 2.some) |@| XProduct(3, none[Int])) {_ |+| (_: XProduct[Int, Option[Int]]) }
res1: scalaz.Unapply[scalaz.Apply,scalaz.XProduct[Int,Option[Int]]]{type M[X] = scalaz.XProduct[Int,Option[Int]]; type A = scalaz.XProduct[Int,Option[Int]]}#M[scalaz.XProduct[Int,Option[Int]]] = XProduct(4, Some(2))
```

Let's rewrite word count example from the EIP.

```scala
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
```

Now it's able to combine applicative functions in parallel. What happens if you use a pair?

```scala
scala> text traverseU { (c: Char) => (charCount(c), lineCount(c)) }
res27: (Int, List[Int]) = (35,List(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1))
```

Ha! However, the problem with `Unapply` is that it won't work for more complex structure:

```scala
scala> text traverseU { (c: Char) => XProduct(charCount(c), wordCount(c)) }
<console>:19: error: Unable to unapply type `scalaz.XProduct[Int,scalaz.StateT[scalaz.Id.Id,Boolean,Int]]` into a type constructor of kind `M[_]` that is classified by the type class `scalaz.Applicative`
1) Check that the type class is defined by compiling `implicitly[scalaz.Applicative[<type constructor>]]`.
2) Review the implicits in object Unapply, which only cover common type 'shapes'
(implicit not found: scalaz.Unapply[scalaz.Applicative, scalaz.XProduct[Int,scalaz.StateT[scalaz.Id.Id,Boolean,Int]]])
              text traverseU { (c: Char) => XProduct(charCount(c), wordCount(c)) }
                   ^
```

Once it all works out, it would be cool to have `@>>>` and `@&&&` operator on `Arrow` or `Function1` that does the applicative composition as it's described in EIP.

We'll cover some other topic later.
