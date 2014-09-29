
### Option as Monoid

LYAHFGG:

> One way is to treat `Maybe a` as a monoid only if its type parameter a is a monoid as well and then implement mappend in such a way that it uses the mappend operation of the values that are wrapped with `Just`.

Let's see if this is how Scalaz does it. See [`std/Option.scala`]($scalazBaseUrl$/core/src/main/scala/scalaz/std/Option.scala#L54-63):

```scala
  implicit def optionMonoid[A: Semigroup]: Monoid[Option[A]] = new Monoid[Option[A]] {
    def append(f1: Option[A], f2: => Option[A]) = (f1, f2) match {
      case (Some(a1), Some(a2)) => Some(Semigroup[A].append(a1, a2))
      case (Some(a1), None)     => f1
      case (None, Some(a2))     => f2
      case (None, None)         => None
    }

    def zero: Option[A] = None
  }
```

The implementation is nice and simple. Context bound `A: Semigroup` says that `A` must support `|+|`. The rest is pattern matching. Doing exactly what the book says.

```scala
scala> (none: Option[String]) |+| "andy".some
res23: Option[String] = Some(andy)

scala> (Ordering.LT: Ordering).some |+| none
res25: Option[scalaz.Ordering] = Some(LT)
```

It works.

LYAHFGG:

> But if we don't know if the contents are monoids, we can't use `mappend` between them, so what are we to do? Well, one thing we can do is to just discard the second value and keep the first one. For this, the `First a` type exists.

Haskell is using `newtype` to implement `First` type constructor. Scalaz 7 does it using mightly Tagged type:

```scala
scala> Tags.First('a'.some) |+| Tags.First('b'.some)
res26: scalaz.@@[Option[Char],scalaz.Tags.First] = Some(a)

scala> Tags.First(none: Option[Char]) |+| Tags.First('b'.some)
res27: scalaz.@@[Option[Char],scalaz.Tags.First] = Some(b)

scala> Tags.First('a'.some) |+| Tags.First(none: Option[Char])
res28: scalaz.@@[Option[Char],scalaz.Tags.First] = Some(a)
```

LYAHFGG:

> If we want a monoid on `Maybe a` such that the second parameter is kept if both parameters of `mappend` are `Just` values, `Data.Monoid` provides a the `Last a` type.

This is `Tags.Last`:

```scala
scala> Tags.Last('a'.some) |+| Tags.Last('b'.some)
res29: scalaz.@@[Option[Char],scalaz.Tags.Last] = Some(b)

scala> Tags.Last(none: Option[Char]) |+| Tags.Last('b'.some)
res30: scalaz.@@[Option[Char],scalaz.Tags.Last] = Some(b)

scala> Tags.Last('a'.some) |+| Tags.Last(none: Option[Char])
res31: scalaz.@@[Option[Char],scalaz.Tags.Last] = Some(a)
```
