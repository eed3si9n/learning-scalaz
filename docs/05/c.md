
### List Monad

LYAHFGG:

> On the other hand, a value like `[3,8,9]` contains several results, so we can view it as one value that is actually many values at the same time. Using lists as applicative functors showcases this non-determinism nicely.

Let's look at using `List` as Applicatives again (This notation might require Scalaz 7.0.0-M3):

```scala
scala> ^(List(1, 2, 3), List(10, 100, 100)) {_ * _}
res29: List[Int] = List(10, 100, 100, 20, 200, 200, 30, 300, 300)
```

> let's try feeding a non-deterministic value to a function:

```scala
scala> List(3, 4, 5) >>= {x => List(x, -x)}
res30: List[Int] = List(3, -3, 4, -4, 5, -5)
```

So in this monadic view, `List` context represent mathematical value that could have multiple solutions. Other than that manipulating `List`s using `for` notation is just like plain Scala:

```scala
scala> for {
         n <- List(1, 2)
         ch <- List('a', 'b')
       } yield (n, ch)
res33: List[(Int, Char)] = List((1,a), (1,b), (2,a), (2,b))
```
