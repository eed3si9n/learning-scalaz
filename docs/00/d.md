---
out: method-injection.html
---

### Method injection (enrich my library)

> If we were to write a function that sums two types using the `Monoid`, we need to call it like this.

```scala
scala> def plus[A: Monoid](a: A, b: A): A = implicitly[Monoid[A]].mappend(a, b)
plus: [A](a: A, b: A)(implicit evidence\$1: Monoid[A])A

scala> plus(3, 4)
res25: Int = 7
```

We would like to provide an operator. But we don't want to enrich just one type, but enrich all types that has an instance for `Monoid`. Let me do this in Scalaz 7 style.

```scala
scala> trait MonoidOp[A] {
         val F: Monoid[A]
         val value: A
         def |+|(a2: A) = F.mappend(value, a2)
       }
defined trait MonoidOp

scala> implicit def toMonoidOp[A: Monoid](a: A): MonoidOp[A] = new MonoidOp[A] {
         val F = implicitly[Monoid[A]]
         val value = a
       }
toMonoidOp: [A](a: A)(implicit evidence\$1: Monoid[A])MonoidOp[A]

scala> 3 |+| 4
res26: Int = 7

scala> "a" |+| "b"
res28: String = ab
```

We were able to inject `|+|` to both `Int` and `String` with just one definition.

### Standard type syntax

Using the same technique, Scalaz also provides method injections for standard library types like `Option` and `Boolean`:

```scala
scala> 1.some | 2
res0: Int = 1

scala> Some(1).getOrElse(2)
res1: Int = 1

scala> (1 > 10)? 1 | 2
res3: Int = 2

scala> if (1 > 10) 1 else 2
res4: Int = 2
```

I hope you could get some feel on where Scalaz is coming from.
