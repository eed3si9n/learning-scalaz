
### Applicative

LYAHFGG:

> So far, when we were mapping functions over functors, we usually mapped functions that take only one parameter. But what happens when we map a function like `*`, which takes two parameters, over a functor?

```scala
scala> List(1, 2, 3, 4) map {(_: Int) * (_:Int)}
<console>:14: error: type mismatch;
 found   : (Int, Int) => Int
 required: Int => ?
              List(1, 2, 3, 4) map {(_: Int) * (_:Int)}
                                             ^
```

Oops. We have to curry this:

```scala
scala> List(1, 2, 3, 4) map {(_: Int) * (_:Int)}.curried
res11: List[Int => Int] = List(<function1>, <function1>, <function1>, <function1>)

scala> res11 map {_(9)}
res12: List[Int] = List(9, 18, 27, 36)
```

LYAHFGG:

> Meet the `Applicative` typeclass. It lies in the `Control.Applicative` module and it defines two methods, `pure` and `<*>`. 

Let's see the contract for Scalaz's `Applicative`:

```scala
trait Applicative[F[_]] extends Apply[F] { self =>
  def point[A](a: => A): F[A]

  /** alias for `point` */
  def pure[A](a: => A): F[A] = point(a)

  ...
}
```

So `Applicative` extends another typeclass `Apply`, and introduces `point` and its alias `pure`.

LYAHFGG:

> `pure` should take a value of any type and return an applicative value with that value inside it. ... A better way of thinking about `pure` would be to say that it takes a value and puts it in some sort of default (or pure) context—a minimal context that still yields that value.

Scalaz likes the name `point` instead of `pure`, and it seems like it's basically a constructor that takes value `A` and returns `F[A]`. It doesn't introduce an operator, but it intoduces `point` method and its symbolic alias `η` to all data types.

```scala
scala> 1.point[List]
res14: List[Int] = List(1)

scala> 1.point[Option]
res15: Option[Int] = Some(1)

scala> 1.point[Option] map {_ + 2}
res16: Option[Int] = Some(3)

scala> 1.point[List] map {_ + 2}
res17: List[Int] = List(3)
```

I can't really express it in words yet, but there's something cool about the fact that constructor is abstracted out.

### Apply

LYAHFGG:

> You can think of `<*>` as a sort of a beefed-up `fmap`. Whereas `fmap` takes a function and a functor and applies the function inside the functor value, `<*>` takes a functor that has a function in it and another functor and extracts that function from the first functor and then maps it over the second one. 

```scala
trait Apply[F[_]] extends Functor[F] { self =>
  def ap[A,B](fa: => F[A])(f: => F[A => B]): F[B]
}
```

Using `ap`, `Apply` enables `<*>`, `*>`, and `<*` operator.

```scala
scala> 9.some <*> {(_: Int) + 3}.some
res20: Option[(Int, Int => Int)] = Some((9,<function1>))
```

I was hoping for `Some(12)` here. Scalaz 7.0.0-M3 creates a tuple in `Some`. I've asked the authors and it looks like it will be changed back to the same behavior as Haskell, Scalaz 6 and Scalaz 7.0.0-M2. Let's run it again using 7.0.0-M2:

```scala
scala>  9.some <*> {(_: Int) + 3}.some
res20: Option[Int] = Some(12)
```

This is much better.

`*>` and `<*` are variations that returns only the rhs or lhs.

```scala
scala> 1.some <* 2.some
res35: Option[Int] = Some(1)

scala> none <* 2.some
res36: Option[Nothing] = None

scala> 1.some *> 2.some
res38: Option[Int] = Some(2)

scala> none *> 2.some
res39: Option[Int] = None
```

### Option as Apply

<s>Thanks, but what happened to the `<*>` that can extract functions out of containers, and apply the extracted values to it?</s> We can use `<*>` in 7.0.0-M2:

```scala
scala> 9.some <*> {(_: Int) + 3}.some
res57: Option[Int] = Some(12)

scala> 3.some <*> { 9.some <*> {(_: Int) + (_: Int)}.curried.some }
res58: Option[Int] = Some(12)
```

### Applicative Style

Another thing I found in 7.0.0-M3 is a new notation that extracts values from containers and apply them to a single function:

```scala
scala> ^(3.some, 5.some) {_ + _}
res59: Option[Int] = Some(8)

scala> ^(3.some, none: Option[Int]) {_ + _}
res60: Option[Int] = None
```

This is actually useful because for one-function case, we no longer need to put it into the container. I am guessing that this is why Scalaz 7 does not introduce any operator from `Applicative` itself. Whatever the case, it seems like we no longer need `Pointed` or `<\$>`.

The new `^(f1, f2) {...}` style is not without the problem though. It doesn't seem to handle Applicatives that takes two type parameters like `Function1`, `Writer`, and `Validation`. There's another way called Applicative Builder, which apparently was the way it worked in Scalaz 6, got deprecated in M3, but will be vindicated again because of `^(f1, f2) {...}`'s issues.

Here's how it looks:

```scala
scala> (3.some |@| 5.some) {_ + _}
res18: Option[Int] = Some(8)
```

We will use `|@|` style for now.

### Lists as Apply

LYAHFGG:

> Lists (actually the list type constructor, `[]`) are applicative functors. What a surprise!

Let's see if we can use `<*>` and `|@|`:

```scala
scala> List(1, 2, 3) <*> List((_: Int) * 0, (_: Int) + 100, (x: Int) => x * x)
res61: List[Int] = List(0, 0, 0, 101, 102, 103, 1, 4, 9)

scala> List(3, 4) <*> { List(1, 2) <*> List({(_: Int) + (_: Int)}.curried, {(_: Int) * (_: Int)}.curried) }
res62: List[Int] = List(4, 5, 5, 6, 3, 4, 6, 8)

scala> (List("ha", "heh", "hmm") |@| List("?", "!", ".")) {_ + _}
res63: List[String] = List(ha?, ha!, ha., heh?, heh!, heh., hmm?, hmm!, hmm.)
```

### Zip Lists

LYAHFGG:

> However, `[(+3),(*2)] <*> [1,2]` could also work in such a way that the first function in the left list gets applied to the first value in the right one, the second function gets applied to the second value, and so on. That would result in a list with two values, namely `[4,4]`. You could look at it as `[1 + 3, 2 * 2]`.

This can be done in Scalaz, but not easily.

```scala
scala> streamZipApplicative.ap(Tags.Zip(Stream(1, 2))) (Tags.Zip(Stream({(_: Int) + 3}, {(_: Int) * 2})))
res32: scala.collection.immutable.Stream[Int] with Object{type Tag = scalaz.Tags.Zip} = Stream(4, ?)

scala> res32.toList
res33: List[Int] = List(4, 4)
```

We'll see more examples of tagged type tomorrow.

### Useful functions for Applicatives

LYAHFGG:

> `Control.Applicative` defines a function that's called `liftA2`, which has a type of

```haskell
liftA2 :: (Applicative f) => (a -> b -> c) -> f a -> f b -> f c .
```

There's `Apply[F].lift2`:

```scala
scala> Apply[Option].lift2((_: Int) :: (_: List[Int]))
res66: (Option[Int], Option[List[Int]]) => Option[List[Int]] = <function2>

scala> res66(3.some, List(4).some)
res67: Option[List[Int]] = Some(List(3, 4))
```

LYAHFGG:

> Let's try implementing a function that takes a list of applicatives and returns an applicative that has a list as its result value. We'll call it `sequenceA`.

```haskell
sequenceA :: (Applicative f) => [f a] -> f [a]  
sequenceA [] = pure []  
sequenceA (x:xs) = (:) <\$> x <*> sequenceA xs  
```

Let's try implementing this in Scalaz!

```scala
scala> def sequenceA[F[_]: Applicative, A](list: List[F[A]]): F[List[A]] = list match {
         case Nil     => (Nil: List[A]).point[F]
         case x :: xs => (x |@| sequenceA(xs)) {_ :: _} 
       }
sequenceA: [F[_], A](list: List[F[A]])(implicit evidence\$1: scalaz.Applicative[F])F[List[A]]
```

Let's test it:

```scala
scala> sequenceA(List(1.some, 2.some))
res82: Option[List[Int]] = Some(List(1, 2))

scala> sequenceA(List(3.some, none, 1.some))
res85: Option[List[Int]] = None

scala> sequenceA(List(List(1, 2, 3), List(4, 5, 6)))
res86: List[List[Int]] = List(List(1, 4), List(1, 5), List(1, 6), List(2, 4), List(2, 5), List(2, 6), List(3, 4), List(3, 5), List(3, 6))
```

We got the right answers. What's interesting here is that we did end up needing `Pointed` after all, and `sequenceA` is generic in typeclassy way.

For `Function1` with `Int` fixed example, we have to unfortunately invoke a dark magic.

```scala
scala> type Function1Int[A] = ({type l[A]=Function1[Int, A]})#l[A]
defined type alias Function1Int

scala> sequenceA(List((_: Int) + 3, (_: Int) + 2, (_: Int) + 1): List[Function1Int[Int]])
res1: Int => List[Int] = <function1>

scala> res1(3)
res2: List[Int] = List(6, 5, 4)
```

It took us a while, but I am glad we got this far. We'll pick it up from here later.
