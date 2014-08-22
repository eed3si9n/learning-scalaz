---
out: Monoid.html
---

  [tags]: https://github.com/scalaz/scalaz/blob/series/7.1.x/core/src/main/scala/scalaz/Tags.scala

### About those Monoids

LYAHFGG:

> It seems that both `*` together with `1` and `++` along with `[]` share some common properties:
> - The function takes two parameters.
> - The parameters and the returned value have the same type.
> - There exists such a value that doesn't change other values when used with the binary function.

Let's check it out in Scala:

```scala
scala> 4 * 1
res16: Int = 4

scala> 1 * 9
res17: Int = 9

scala> List(1, 2, 3) ++ Nil
res18: List[Int] = List(1, 2, 3)

scala> Nil ++ List(0.5, 2.5)
res19: List[Double] = List(0.5, 2.5)
```

Looks right.

LYAHFGG:

> It doesn't matter if we do `(3 * 4) * 5` or `3 * (4 * 5)`. Either way, the result is `60`. The same goes for `++`.
> ...
> We call this property *associativity*. `*` is associative, and so is `++`, but `-`, for example, is not.

Let's check this too:

```scala
scala> (3 * 2) * (8 * 5) assert_=== 3 * (2 * (8 * 5))

scala> List("la") ++ (List("di") ++ List("da")) assert_=== (List("la") ++ List("di")) ++ List("da")
```

No error means, they are equal. Apparently this is what monoid is.

### Monoid

LYAHFGG:

> A *monoid* is when you have an associative binary function and a value which acts as an identity with respect to that function.

Let's see [the typeclass contract for `Monoid` in Scalaz](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Monoid.scala):

```scala
trait Monoid[A] extends Semigroup[A] { self =>
  ////
  /** The identity element for `append`. */
  def zero: A

  ...
}
```

### Semigroup

Looks like `Monoid` extends `Semigroup` so let's [look at its typeclass](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Semigroup.scala).

```scala
trait Semigroup[A]  { self =>
  def append(a1: A, a2: => A): A
  ...
}
```

Here are [the operators](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/SemigroupSyntax.scala):

```scala
trait SemigroupOps[A] extends Ops[A] {
  final def |+|(other: => A): A = A.append(self, other)
  final def mappend(other: => A): A = A.append(self, other)
  final def ⊹(other: => A): A = A.append(self, other)
}
```

It introduces `mappend` operator with symbolic alias `|+|` and `⊹`.

LYAHFGG:

> We have `mappend`, which, as you've probably guessed, is the binary function. It takes two values of the same type and returns a value of that type as well.

LYAHFGG also warns that just because it's named `mappend` it does not mean it's appending something, like in the case of `*`. Let's try using this.

```scala
scala> List(1, 2, 3) mappend List(4, 5, 6)
res23: List[Int] = List(1, 2, 3, 4, 5, 6)

scala> "one" mappend "two"
res25: String = onetwo
```

I think the idiomatic Scalaz way is to use `|+|`:

```scala
scala> List(1, 2, 3) |+| List(4, 5, 6)
res26: List[Int] = List(1, 2, 3, 4, 5, 6)

scala> "one" |+| "two"
res27: String = onetwo
```

This looks more concise.

### Back to Monoid

```scala
trait Monoid[A] extends Semigroup[A] { self =>
  ////
  /** The identity element for `append`. */
  def zero: A

  ...
}
```

LYAHFGG:

> `mempty` represents the identity value for a particular monoid.

Scalaz calls this `zero` instead.

```scala
scala> Monoid[List[Int]].zero
res15: List[Int] = List()

scala> Monoid[String].zero
res16: String = ""
```

### Tags.Multiplication

LYAHFGG:

> So now that there are two equally valid ways for numbers (addition and multiplication) to be monoids, which way do choose? Well, we don't have to.

This is where Scalaz 7.1 uses tagged type. The built-in tags are [Tags][tags]. There are 8 tags for Monoids and 1 named `Zip` for `Applicative`. (Is this the Zip List I couldn't find yesterday?)

```scala
scala> Tags.Multiplication(10) |+| Monoid[Int @@ Tags.Multiplication].zero
res21: scalaz.@@[Int,scalaz.Tags.Multiplication] = 10
```

Nice! So we can multiply numbers using `|+|`. For addition, we use plain `Int`.

```scala
scala> 10 |+| Monoid[Int].zero
res22: Int = 10
```

### Tags.Disjunction and Tags.Conjunction

LYAHFGG:

> Another type which can act like a monoid in two distinct but equally valid ways is `Bool`. The first way is to have the or function `||` act as the binary function along with `False` as the identity value.
> ...
> The other way for `Bool` to be an instance of `Monoid` is to kind of do the opposite: have `&&` be the binary function and then make `True` the identity value.

In Scalaz 7 these are called `Boolean @@ Tags.Disjunction` and `Boolean @@ Tags.Conjunction` respectively.

```scala
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
```

### Ordering as Monoid

LYAHFGG:

> With `Ordering`, we have to look a bit harder to recognize a monoid, but it turns out that its `Monoid` instance is just as intuitive as the ones we've met so far and also quite useful.

Sounds odd, but let's check it out.

```scala
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
```

LYAHFGG:

> OK, so how is this monoid useful? Let's say you were writing a function that takes two strings, compares their lengths, and returns an `Ordering`. But if the strings are of the same length, then instead of returning `EQ` right away, we want to compare them alphabetically.

Because the left comparison is kept unless it's `Ordering.EQ` we can use this to compose two levels of comparisons. Let's try implementing `lengthCompare` using Scalaz:

```scala
scala> def lengthCompare(lhs: String, rhs: String): Ordering =
         (lhs.length ?|? rhs.length) |+| (lhs ?|? rhs)
lengthCompare: (lhs: String, rhs: String)scalaz.Ordering

scala> lengthCompare("zen", "ants")
res46: scalaz.Ordering = LT

scala> lengthCompare("zen", "ant")
res47: scalaz.Ordering = GT
```

It works. "zen" is `LT` compared to "ants" because it's shorter.

We still have more Monoids, but let's call it a day. We'll pick it up from here later.
