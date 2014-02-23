
### Walk the line

LYAHFGG:

> Let's say that [Pierre] keeps his balance if the number of birds on the left side of the pole and on the right side of the pole is within three. So if there's one bird on the right side and four birds on the left side, he's okay. But if a fifth bird lands on the left side, then he loses his balance and takes a dive.

Now let's try implementing `Pole` example from the book.

```scala
scala> type Birds = Int
defined type alias Birds

scala> case class Pole(left: Birds, right: Birds)
defined class Pole
```

I don't think it's common to alias `Int` like this in Scala, but we'll go with the flow. I am going to turn `Pole` into a case class so I can implement `landLeft` and `landRight` as methods:

```scala
scala> case class Pole(left: Birds, right: Birds) {
         def landLeft(n: Birds): Pole = copy(left = left + n)
         def landRight(n: Birds): Pole = copy(right = right + n) 
       }
defined class Pole
```

I think it looks better with some OO:

```scala
scala> Pole(0, 0).landLeft(2)
res10: Pole = Pole(2,0)

scala> Pole(1, 2).landRight(1)
res11: Pole = Pole(1,3)

scala> Pole(1, 2).landRight(-1)
res12: Pole = Pole(1,1)
```

We can chain these too:

```scala
scala> Pole(0, 0).landLeft(1).landRight(1).landLeft(2)
res13: Pole = Pole(3,1)

scala> Pole(0, 0).landLeft(1).landRight(4).landLeft(-1).landRight(-2)
res15: Pole = Pole(0,2)
```

As the book says, an intermediate value have failed but the calculation kept going. Now let's introduce failures as `Option[Pole]`:

```scala
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
```

Let's try the chaining using `flatMap`:

```scala
scala> Pole(0, 0).landRight(1) flatMap {_.landLeft(2)}
res18: Option[Pole] = Some(Pole(2,1))

scala> (none: Option[Pole]) flatMap {_.landLeft(2)}
res19: Option[Pole] = None

scala> Monad[Option].point(Pole(0, 0)) flatMap {_.landRight(2)} flatMap {_.landLeft(2)} flatMap {_.landRight(2)}
res21: Option[Pole] = Some(Pole(2,4))
```

Note the use of `Monad[Option].point(...)` here to start the initial value in `Option` context. We can also try the `>>=` alias to make it look more monadic:

```scala
scala> Monad[Option].point(Pole(0, 0)) >>= {_.landRight(2)} >>= {_.landLeft(2)} >>= {_.landRight(2)}
res22: Option[Pole] = Some(Pole(2,4))
```

Let's see if monadic chaining simulates the pole balancing better:

```scala
scala> Monad[Option].point(Pole(0, 0)) >>= {_.landLeft(1)} >>= {_.landRight(4)} >>= {_.landLeft(-1)} >>= {_.landRight(-2)}
res23: Option[Pole] = None
```

It works.

### Banana on wire

LYAHFGG:

> We may also devise a function that ignores the current number of birds on the balancing pole and just makes Pierre slip and fall. We can call it `banana`.

Here's the `banana` that always fails:

```scala
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
```

LYAHFGG:

> Instead of making functions that ignore their input and just return a predetermined monadic value, we can use the `>>` function.

Here's how `>>` behaves with `Option`:

```scala
scala> (none: Option[Int]) >> 3.some
res25: Option[Int] = None

scala> 3.some >> 4.some
res26: Option[Int] = Some(4)

scala> 3.some >> (none: Option[Int])
res27: Option[Int] = None
```

Let's try replacing `banana` with `>> (none: Option[Pole])`:

```scala
scala> Monad[Option].point(Pole(0, 0)) >>= {_.landLeft(1)} >> (none: Option[Pole]) >>= {_.landRight(1)}
<console>:26: error: missing parameter type for expanded function ((x\$1) => x\$1.landLeft(1))
              Monad[Option].point(Pole(0, 0)) >>= {_.landLeft(1)} >> (none: Option[Pole]) >>= {_.landRight(1)}
                                                   ^
```

The type inference broke down all the sudden. The problem is likely the operator precedence. [Programming in Scala](http://www.artima.com/pins1ed/basic-types-and-operations.html) says:

> The one exception to the precedence rule, alluded to above, concerns assignment operators, which end in an equals character. If an operator ends in an equals character (`=`), and the operator is not one of the comparison operators `<=`, `>=`, `==`, or `!=`, then the precedence of the operator is the same as that of simple assignment (`=`). That is, it is lower than the precedence of any other operator.

Note: The above description is incomplete. Another exception from the assignment operator rule is if it starts with (`=`) like `===`.

Because `>>=` (bind) ends in equals character, its precedence is the lowest, which forces `({_.landLeft(1)} >> (none: Option[Pole]))` to evaluate first. There are a few unpalatable work arounds. First we can use dot-and-parens like normal method calls:

```scala
scala> Monad[Option].point(Pole(0, 0)).>>=({_.landLeft(1)}).>>(none: Option[Pole]).>>=({_.landRight(1)})
res9: Option[Pole] = None
```

Or recognize the precedence issue and place parens around just the right place:

```scala
scala> (Monad[Option].point(Pole(0, 0)) >>= {_.landLeft(1)}) >> (none: Option[Pole]) >>= {_.landRight(1)}
res10: Option[Pole] = None
```

Both yield the right result. By the way, changing `>>=` to `flatMap` is not going to help since `>>` still has higher precedence.

### for syntax

LYAHFGG:

> Monads in Haskell are so useful that they got their own special syntax called `do` notation.

First, let write the nested lambda:

```scala
scala> 3.some >>= { x => "!".some >>= { y => (x.shows + y).some } }
res14: Option[String] = Some(3!)
```

By using `>>=`, any part of the calculation can fail:

```scala
scala> 3.some >>= { x => (none: Option[String]) >>= { y => (x.shows + y).some } }
res17: Option[String] = None

scala> (none: Option[Int]) >>= { x => "!".some >>= { y => (x.shows + y).some } }
res16: Option[String] = None

scala> 3.some >>= { x => "!".some >>= { y => (none: Option[String]) } }
res18: Option[String] = None
```

Instead of the `do` notation in Haskell, Scala has `for` syntax, which does the same thing:

```scala
scala> for {
         x <- 3.some
         y <- "!".some
       } yield (x.shows + y)
res19: Option[String] = Some(3!)
```

LYAHFGG:

> In a `do` expression, every line that isn't a `let` line is a monadic value. 

I think this applies true for Scala's `for` syntax too.

### Pierre returns

LYAHFGG:

> Our tightwalker's routine can also be expressed with `do` notation.

```scala
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
```

We had to extract `third` since `yield` expects `Pole` not `Option[Pole]`.

LYAHFGG:

> If we want to throw the Pierre a banana peel in `do` notation, we can do the following:

```scala
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
```

### Pattern matching and failure

LYAHFGG:

> In `do` notation, when we bind monadic values to names, we can utilize pattern matching, just like in let expressions and function parameters.

```scala
scala> def justH: Option[Char] =
         for {
           (x :: xs) <- "hello".toList.some
         } yield x
justH: Option[Char]

scala> justH
res25: Option[Char] = Some(h)
```

> When pattern matching fails in a do expression, the `fail` function is called. It's part of the `Monad` type class and it enables failed pattern matching to result in a failure in the context of the current monad instead of making our program crash. 

```scala
scala> def wopwop: Option[Char] =
         for {
           (x :: xs) <- "".toList.some
         } yield x
wopwop: Option[Char]

scala> wopwop
res28: Option[Char] = None
```

The failed pattern matching returns `None` here. This is an interesting aspect of `for` syntax that I haven't thought about, but totally makes sense.
