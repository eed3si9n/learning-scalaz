
### Tagged type

If you have the book Learn You a Haskell for Great Good you get to start a new chapter: Monoids. For the website, it's still [Functors, Applicative Functors and Monoids](http://learnyouahaskell.com/functors-applicative-functors-and-monoids).

LYAHFGG:

> The *newtype* keyword in Haskell is made exactly for these cases when we want to just take one type and wrap it in something to present it as another type.

This is a language-level feature in Haskell, so one would think we can't port it over to Scala.
About an year ago (September 2011) [Miles Sabin (@milessabin)](https://twitter.com/milessabin) wrote [a gist](https://gist.github.com/89c9b47a91017973a35f) and called it `Tagged` and [Jason Zaugg (@retronym)](https://twitter.com/retronym) added `@@` type alias.

```scala
type Tagged[U] = { type Tag = U }
type @@[T, U] = T with Tagged[U]
```

[Eric Torreborre (@etorreborre)](http://twitter.com/etorreborre) wrote [Practical uses for Unboxed Tagged Types](http://etorreborre.blogspot.com/2011/11/practical-uses-for-unboxed-tagged-types.html) and [Tim Perrett](http://es.twitter.com/timperrett) wrote [Unboxed new types within Scalaz7](http://timperrett.com/2012/06/15/unboxed-new-types-within-scalaz7/) if you want to read up on it.

Suppose we want a way to express mass using kilogram, because kg is the international standard of unit. Normally we would pass in `Double` and call it a day, but we can't distinguish that from other `Double` values. Can we use case class for this?

```scala
case class KiloGram(value: Double)
```

Although it does adds type safety, it's not fun to use because we have to call `x.value` every time we need to extract the value out of it. Tagged type to the rescue.

```scala
scala> sealed trait KiloGram
defined trait KiloGram

scala> def KiloGram[A](a: A): A @@ KiloGram = Tag[A, KiloGram](a)
KiloGram: [A](a: A)scalaz.@@[A,KiloGram]

scala> val mass = KiloGram(20.0)
mass: scalaz.@@[Double,KiloGram] = 20.0

scala> 2 * Tag.unwrap(mass)
res2: Double = 40.0
```

Note: As of scalaz 7.1 we need to explicitly unwrap tags. Previously we could just do `2 * mass`.
Just to be clear, `A @@ KiloGram` is an infix notation of `scalaz.@@[A, KiloGram]`. We can now define a function that calculates relativistic energy.

```scala
scala> sealed trait JoulePerKiloGram
defined trait JoulePerKiloGram

scala> def JoulePerKiloGram[A](a: A): A @@ JoulePerKiloGram = Tag[A, JoulePerKiloGram](a)
JoulePerKiloGram: [A](a: A)scalaz.@@[A,JoulePerKiloGram]

scala> def energyR(m: Double @@ KiloGram): Double @@ JoulePerKiloGram =
     |   JoulePerKiloGram(299792458.0 * 299792458.0 * Tag.unwrap(m))
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
```

As you can see, passing in plain `Double` to `energyR` fails at compile-time. This sounds exactly like `newtype` except it's even better because we can define `Int @@ KiloGram` if we want.
