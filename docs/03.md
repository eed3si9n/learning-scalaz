  [day2]: http://eed3si9n.com/learning-scalaz-day2
  [tt]: http://learnyouahaskell.com/types-and-typeclasses

[Yesterday][day2] we started with `Functor`, which adds `map` operator, and ended with polymorphic `sequenceA` function that uses `Pointed[F].point` and Applicative `^(f1, f2) {_ :: _}` syntax.

### Kinds and some type-foo

One section I should've covered yesterday from [Making Our Own Types and Typeclasses][tt] but didn't is about kinds and types. I thought it wouldn't matter much to understand Scalaz, but it does, so we need to have the talk.

[Learn You a Haskell For Great Good][tt] says:

> Types are little labels that values carry so that we can reason about the values. But types have their own little labels, called kinds. A kind is more or less the type of a type. 
> ...
> What are kinds and what are they good for? Well, let's examine the kind of a type by using the :k command in GHCI.

I did not find `:k` command for Scala REPL so I wrote one for Scala 2.10.0-M7. <s>Unlike Haskell's version, it only accepts proper type as input but it's better than nothing!</s> For type constructors, pass in the companion type. (Thanks paulp for the suggestion)

<scala>
// requires Scala 2.10.0-M7
def kind[A: scala.reflect.TypeTag]: String = {
  import scala.reflect.runtime.universe._
  def typeKind(sig: Type): String = sig match {
    case PolyType(params, resultType) =>
      (params map { p =>
        typeKind(p.typeSignature) match {
          case "*" => "*"
          case s   => "(" + s + ")"
        }
      }).mkString(" -> ") + " -> *"
    case _ => "*"
  }
  def typeSig(tpe: Type): Type = tpe match {
    case SingleType(pre, sym) => sym.companionSymbol.typeSignature
    case ExistentialType(q, TypeRef(pre, sym, args)) => sym.typeSignature
    case TypeRef(pre, sym, args) => sym.typeSignature
  }
  val sig = typeSig(typeOf[A])
  val s = typeKind(sig)
  sig.typeSymbol.name + "'s kind is " + s + ". " + (s match {
    case "*" =>
      "This is a proper type."
    case x if !(x contains "(") =>
      "This is a type constructor: a 1st-order-kinded type."
    case x =>
      "This is a type constructor that takes type constructor(s): a higher-kinded type."
  })
}
</scala>

Run `sbt console` using `build.sbt` that I posted on day 1, and copy paste the above function. Let's try using it:

<code>
scala> kind[Int]
res0: String = Int's kind is *. This is a proper type.

scala> kind[Option.type]
res1: String = Option's kind is * -> *. This is a type constructor: a 1st-order-kinded type.

scala> kind[Either.type]
res2: String = Either's kind is * -> * -> *. This is a type constructor: a 1st-order-kinded type.

scala> kind[Equal.type]
res3: String = Equal's kind is * -> *. This is a type constructor: a 1st-order-kinded type.

scala> kind[Functor.type]
res4: String = Functor's kind is (* -> *) -> *. This is a type constructor that takes type constructor(s): a higher-kinded type.
</code>

From the top. `Int` and every other types that you can make a value out of is called a proper type and denoted with a symbol `*` (read "type"). This is analogous to value `1` at value-level.

A first-order value, or a value constructor like `(_: Int) + 3`, is normally called a function. Similarly, a first-order-kinded type is a type that accepts other types to create a proper type. This is normally called a type constructor. `Option`, `Either`, and `Equal` are all first-order-kinded. To denote that these accept other types, we use curried notation like `* -> *` and `* -> * -> *`. Note, `Option[Int]` is `*`; `Option` is `* -> *`.

A higher-order value like `(f: Int => Int, list: List[Int]) => list map {f}`, a function that accepts other functions is normally called higher-order function. Similarly, a higher-kinded type is a type constructor that accepts other type constructors. It probably should be called a higher-kinded type constructor but the name is not used. These are denoted as `(* -> *) -> *`. 

In case of Scalaz 7, `Equal` and others have the kind `* -> *` while `Functor` and all its derivatives have the kind `(* -> *) -> *`. You wouldn't worry about this if you are using injected operators like:

<scala>
scala> List(1, 2, 3).shows
res11: String = [1,2,3]
</scala>

But if you want to use `Show[A].shows`, you have to know it's `Show[List[Int]]`, not `Show[List]`. Similarly, if you want to lift a function, you need to know that it's `Functor[F]` (`F` is for `Functor`):

<scala>
scala> Functor[List[Int]].lift((_: Int) + 2)
<console>:14: error: List[Int] takes no type parameters, expected: one
              Functor[List[Int]].lift((_: Int) + 2)
                      ^

scala> Functor[List].lift((_: Int) + 2)
res13: List[Int] => List[Int] = <function1>
</scala>

In [the cheat sheet](http://eed3si9n.com/scalaz-cheat-sheet) I started I originally had type parameters for `Equal` written as `Equal[F]`, which is the same as Scalaz 7's source code. [Adam Rosien @arosien](http://twitter.com/arosien/status/241990437269815296) pointed out to me that it should be `Equal[A]`. Now it makes sense why!

### Tagged type

If you have the book Learn You a Haskell for Great Good you get to start a new chapter: Monoids. For the website, it's still [Functors, Applicative Functors and Monoids](http://learnyouahaskell.com/functors-applicative-functors-and-monoids).

LYAHFGG:

> The *newtype* keyword in Haskell is made exactly for these cases when we want to just take one type and wrap it in something to present it as another type.

This is a language-level feature in Haskell, so one would think we can't port it over to Scala.
About an year ago (September 2011) [Miles Sabin (@milessabin)](https://twitter.com/milessabin) wrote [a gist](https://gist.github.com/89c9b47a91017973a35f) and called it `Tagged` and [Jason Zaugg (@retronym)](https://twitter.com/retronym) added `@@` type alias.

<scala>
type Tagged[U] = { type Tag = U }
type @@[T, U] = T with Tagged[U]
</scala>

[Eric Torreborre (@etorreborre)](http://twitter.com/etorreborre) wrote [Practical uses for Unboxed Tagged Types](http://etorreborre.blogspot.com/2011/11/practical-uses-for-unboxed-tagged-types.html) and [Tim Perrett](http://es.twitter.com/timperrett) wrote [Unboxed new types within Scalaz7](http://timperrett.com/2012/06/15/unboxed-new-types-within-scalaz7/) if you want to read up on it.

Suppose we want a way to express mass using kilogram, because kg is the international standard of unit. Normally we would pass in `Double` and call it a day, but we can't distinguish that from other `Double` values. Can we use case class for this?

<scala>
case class KiloGram(value: Double)
</scala>

Although it does adds type safety, it's not fun to use because we have to call `x.value` every time we need to extract the value out of it. Tagged type to the rescue.

<scala>
scala> sealed trait KiloGram
defined trait KiloGram

scala> def KiloGram[A](a: A): A @@ KiloGram = Tag[A, KiloGram](a)
KiloGram: [A](a: A)scalaz.@@[A,KiloGram]

scala> val mass = KiloGram(20.0)
mass: scalaz.@@[Double,KiloGram] = 20.0

scala> 2 * mass
res2: Double = 40.0
</scala>

Just to be clear, `A @@ KiloGram` is an infix notation of `scalaz.@@[A, KiloGram]`. We can now define a function that calculates relativistic energy.

<scala>
scala> sealed trait JoulePerKiloGram
defined trait JoulePerKiloGram

scala> def JoulePerKiloGram[A](a: A): A @@ JoulePerKiloGram = Tag[A, JoulePerKiloGram](a)
JoulePerKiloGram: [A](a: A)scalaz.@@[A,JoulePerKiloGram]

scala> def energyR(m: Double @@ KiloGram): Double @@ JoulePerKiloGram =
     |   JoulePerKiloGram(299792458.0 * 299792458.0 * m)
energyR: (m: scalaz.@@[Double,KiloGram])scalaz.@@[Double,JoulePerKiloGram]

scala> energyR(mass)
res4: scalaz.@@[Double,JoulePerKiloGram] = 1.79751035747363533E18

scala> energyR(10.0)
<console>:18: error: type mismatch;
 found   : Double(10.0)
 required: scalaz.@@[Double,KiloGram]
    (which expands to)  Double with AnyRef{type Tag = KiloGram}
              energyR(10.0)
                      ^
</scala>

As you can see, passing in plain `Double` to `energyR` fails at compile-time. This sounds exactly like `newtype` except it's even better because we can define `Int @@ KiloGram` if we want.

### About those Monoids

LYAHFGG:

> It seems that both `*` together with `1` and `++` along with `[]` share some common properties:
> - The function takes two parameters.
> - The parameters and the returned value have the same type.
> - There exists such a value that doesn't change other values when used with the binary function.

Let's check it out in Scala:

<scala>
scala> 4 * 1
res16: Int = 4

scala> 1 * 9
res17: Int = 9

scala> List(1, 2, 3) ++ Nil
res18: List[Int] = List(1, 2, 3)

scala> Nil ++ List(0.5, 2.5)
res19: List[Double] = List(0.5, 2.5)
</scala>

Looks right.

LYAHFGG:

> It doesn't matter if we do `(3 * 4) * 5` or `3 * (4 * 5)`. Either way, the result is `60`. The same goes for `++`.
> ...
> We call this property *associativity*. `*` is associative, and so is `++`, but `-`, for example, is not.

Let's check this too:

<scala>
scala> (3 * 2) * (8 * 5) assert_=== 3 * (2 * (8 * 5))

scala> List("la") ++ (List("di") ++ List("da")) assert_=== (List("la") ++ List("di")) ++ List("da")
</scala>

No error means, they are equal. Apparently this is what monoid is.

### Monoid

LYAHFGG:

> A *monoid* is when you have an associative binary function and a value which acts as an identity with respect to that function. 

Let's see [the typeclass contract for `Monoid` in Scalaz](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Monoid.scala):

<scala>
trait Monoid[A] extends Semigroup[A] { self =>
  ////
  /** The identity element for `append`. */
  def zero: A
  
  ...
}
</scala>

### Semigroup

Looks like `Monoid` extends `Semigroup` so let's [look at its typeclass](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Semigroup.scala).

<scala>
trait Semigroup[A]  { self =>
  def append(a1: A, a2: => A): A
  ...
}
</scala>

Here are [the operators](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/SemigroupSyntax.scala):

<scala>
trait SemigroupOps[A] extends Ops[A] {
  final def |+|(other: => A): A = A.append(self, other)
  final def mappend(other: => A): A = A.append(self, other)
  final def ⊹(other: => A): A = A.append(self, other)
}
</scala>

It introduces `mappend` operator with symbolic alias `|+|` and `⊹`.

LYAHFGG:

> We have `mappend`, which, as you've probably guessed, is the binary function. It takes two values of the same type and returns a value of that type as well.

LYAHFGG also warns that just because it's named `mappend` it does not mean it's appending something, like in the case of `*`. Let's try using this.

<scala>
scala> List(1, 2, 3) mappend List(4, 5, 6)
res23: List[Int] = List(1, 2, 3, 4, 5, 6)

scala> "one" mappend "two"
res25: String = onetwo
</scala>

I think the idiomatic Scalaz way is to use `|+|`:

<scala>
scala> List(1, 2, 3) |+| List(4, 5, 6)
res26: List[Int] = List(1, 2, 3, 4, 5, 6)

scala> "one" |+| "two"
res27: String = onetwo
</scala>

This looks more concise.

### Back to Monoid

<scala>
trait Monoid[A] extends Semigroup[A] { self =>
  ////
  /** The identity element for `append`. */
  def zero: A
  
  ...
}
</scala>

LYAHFGG:

> `mempty` represents the identity value for a particular monoid.

Scalaz calls this `zero` instead.

<scala>
scala> Monoid[List[Int]].zero
res15: List[Int] = List()

scala> Monoid[String].zero
res16: String = ""
</scala>

### Tags.Multiplication

LYAHFGG:

> So now that there are two equally valid ways for numbers (addition and multiplication) to be monoids, which way do choose? Well, we don't have to.

This is where Scalaz 7 uses tagged type. The built-in tags are [Tags](http://halcat0x15a.github.com/scalaz/core/target/scala-2.9.2/api/#scalaz.Tags$). There are 8 tags for Monoids and 1 named `Zip` for `Applicative`. (Is this the Zip List I couldn't find yesterday?)

<scala>
scala> Tags.Multiplication(10) |+| Monoid[Int @@ Tags.Multiplication].zero
res21: scalaz.@@[Int,scalaz.Tags.Multiplication] = 10
</scala>

Nice! So we can multiply numbers using `|+|`. For addition, we use plain `Int`.

<scala>
scala> 10 |+| Monoid[Int].zero
res22: Int = 10
</scala>

### Tags.Disjunction and Tags.Conjunction

LYAHFGG:

> Another type which can act like a monoid in two distinct but equally valid ways is `Bool`. The first way is to have the or function `||` act as the binary function along with `False` as the identity value. 
> ...
> The other way for `Bool` to be an instance of `Monoid` is to kind of do the opposite: have `&&` be the binary function and then make `True` the identity value. 

In Scalaz 7 these are called `Boolean @@ Tags.Disjunction` and `Boolean @@ Tags.Conjunction` respectively.

<scala>
scala> Tags.Disjunction(true) |+| Tags.Disjunction(false)
res28: scalaz.@@[Boolean,scalaz.Tags.Disjunction] = true

scala> Monoid[Boolean @@ Tags.Disjunction].zero |+| Tags.Disjunction(true)
res29: scalaz.@@[Boolean,scalaz.Tags.Disjunction] = true

scala> Monoid[Boolean @@ Tags.Disjunction].zero |+| Monoid[Boolean @@ Tags.Disjunction].zero
res30: scalaz.@@[Boolean,scalaz.Tags.Disjunction] = false

scala> Monoid[Boolean @@ Tags.Conjunction].zero |+| Tags.Conjunction(true)
res31: scalaz.@@[Boolean,scalaz.Tags.Conjunction] = true

scala> Monoid[Boolean @@ Tags.Conjunction].zero |+| Tags.Conjunction(false)
res32: scalaz.@@[Boolean,scalaz.Tags.Conjunction] = false
</scala>

### Ordering as Monoid

LYAHFGG:

> With `Ordering`, we have to look a bit harder to recognize a monoid, but it turns out that its `Monoid` instance is just as intuitive as the ones we've met so far and also quite useful.

Sounds odd, but let's check it out.

<scala>
scala> Ordering.LT |+| Ordering.GT
<console>:14: error: value |+| is not a member of object scalaz.Ordering.LT
              Ordering.LT |+| Ordering.GT
                          ^

scala> (Ordering.LT: Ordering) |+| (Ordering.GT: Ordering)
res42: scalaz.Ordering = LT

scala> (Ordering.GT: Ordering) |+| (Ordering.LT: Ordering)
res43: scalaz.Ordering = GT

scala> Monoid[Ordering].zero |+| (Ordering.LT: Ordering)
res44: scalaz.Ordering = LT

scala> Monoid[Ordering].zero |+| (Ordering.GT: Ordering)
res45: scalaz.Ordering = GT
</scala>

LYAHFGG:

> OK, so how is this monoid useful? Let's say you were writing a function that takes two strings, compares their lengths, and returns an `Ordering`. But if the strings are of the same length, then instead of returning `EQ` right away, we want to compare them alphabetically. 

Because the left comparison is kept unless it's `Ordering.EQ` we can use this to compose two levels of comparisons. Let's try implementing `lengthCompare` using Scalaz:

<scala>
scala> def lengthCompare(lhs: String, rhs: String): Ordering =
         (lhs.length ?|? rhs.length) |+| (lhs ?|? rhs)
lengthCompare: (lhs: String, rhs: String)scalaz.Ordering

scala> lengthCompare("zen", "ants")
res46: scalaz.Ordering = LT

scala> lengthCompare("zen", "ant")
res47: scalaz.Ordering = GT
</scala>

It works. "zen" is `LT` compared to "ants" because it's shorter.

We still have more Monoids, but let's call it a day. We'll pick it up from here later.
