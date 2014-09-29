
### Functor

LYAHFGG:

> And now, we're going to take a look at the `Functor` typeclass, which is basically for things that can be mapped over.

Like the book let's look [how it's implemented]($scalazBaseUrl$/core/src/main/scala/scalaz/Functor.scala):

```scala
trait Functor[F[_]]  { self =>
  /** Lift `f` into `F` and apply to `F[A]`. */
  def map[A, B](fa: F[A])(f: A => B): F[B]

  ...
}
```

Here are the [injected operators]($scalazBaseUrl$/core/src/main/scala/scalaz/syntax/FunctorSyntax.scala) it enables:

```scala
trait FunctorOps[F[_],A] extends Ops[F[A]] {
  implicit def F: Functor[F]
  ////
  import Leibniz.===

  final def map[B](f: A => B): F[B] = F.map(self)(f)

  ...
}
```

So this defines `map` method, which accepts a function `A => B` and returns `F[B]`. We are quite familiar with `map` method for collections:

```scala
scala> List(1, 2, 3) map {_ + 1}
res15: List[Int] = List(2, 3, 4)
```

Scalaz defines `Functor` instances for `Tuple`s.

```scala
scala> (1, 2, 3) map {_ + 1}
res28: (Int, Int, Int) = (1,2,4)
```
Note that the operation is only applied to the last value in the Tuple, (see [scalaz group](https://groups.google.com/forum/#!topic/scalaz/lkrDLUV6HN4) discussion).

### Function as Functors

Scalaz also defines `Functor` instance for `Function1`.

```scala
scala> ((x: Int) => x + 1) map {_ * 7}
res30: Int => Int = <function1>

scala> res30(3)
res31: Int = 28
```

This is interesting. Basically `map` gives us a way to compose functions, except the order is in reverse from `f compose g`.
No wonder Scalaz provides `∘` as an alias of `map`. Another way of looking at `Function1` is that it's an infinite map from the domain to the range. Now let's skip the input and output stuff and go to [Functors, Applicative Functors and Monoids](http://learnyouahaskell.com/functors-applicative-functors-and-monoids).

> How are functions functors?
> ...
>
> What does the type `fmap :: (a -> b) -> (r -> a) -> (r -> b)` for this instance tell us? Well, we see that it takes a function from `a` to `b` and a function from `r` to `a` and returns a function from `r` to `b`. Does this remind you of anything? Yes! Function composition!

Oh man, LYAHFGG came to the same conclusion as I did about the function composition. But wait..

```haskell
ghci> fmap (*3) (+100) 1
303
ghci> (*3) . (+100) \$ 1  
303
```

In Haskell, the `fmap` seems to be working as the same order as `f compose g`. Let's check in Scala using the same numbers:

```scala
scala> (((_: Int) * 3) map {_ + 100}) (1)
res40: Int = 103
```

Something is not right. Let's compare the declaration of `fmap` and Scalaz's `map` operator:

```haskell
fmap :: (a -> b) -> f a -> f b

```

and here's Scalaz:

```scala
final def map[B](f: A => B): F[B] = F.map(self)(f)

```

So the order is completely different. Since `map` here's an injected method of `F[A]`, the data structure to be mapped over comes first, then the function comes next. Let's see `List`:

```haskell
ghci> fmap (*3) [1, 2, 3]
[3,6,9]
```

and

```scala
scala> List(1, 2, 3) map {3*}
res41: List[Int] = List(3, 6, 9)
```

The order is reversed here too.

> [We can think of `fmap` as] a function that takes a function and returns a new function that's just like the old one, only it takes a functor as a parameter and returns a functor as the result. It takes an `a -> b` function and returns a function `f a -> f b`. This is called *lifting* a function.

```haskell
ghci> :t fmap (*2)  
fmap (*2) :: (Num a, Functor f) => f a -> f a  
ghci> :t fmap (replicate 3)  
fmap (replicate 3) :: (Functor f) => f a -> f [a]  
```

Are we going to miss out on this lifting goodness? There are several neat functions under `Functor` typeclass. One of them is called `lift`:

```scala
scala> Functor[List].lift {(_: Int) * 2}
res45: List[Int] => List[Int] = <function1>

scala> res45(List(3))
res47: List[Int] = List(6)
```

Functor also enables some operators that overrides the values in the data structure like `>|`, `as`, `fpair`, `strengthL`, `strengthR`, and `void`:

```scala
scala> List(1, 2, 3) >| "x"
res47: List[String] = List(x, x, x)

scala> List(1, 2, 3) as "x"
res48: List[String] = List(x, x, x)

scala> List(1, 2, 3).fpair
res49: List[(Int, Int)] = List((1,1), (2,2), (3,3))

scala> List(1, 2, 3).strengthL("x")
res50: List[(String, Int)] = List((x,1), (x,2), (x,3))

scala> List(1, 2, 3).strengthR("x")
res51: List[(Int, String)] = List((1,x), (2,x), (3,x))

scala> List(1, 2, 3).void
res52: List[Unit] = List((), (), ())
```
