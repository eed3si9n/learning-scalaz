<div class="floatingimage">
<img src="http://eed3si9n.com/images/mementobw.jpg">
</div>


### Memo

Pure functions don't imply they are computationally cheap. For example, calcuate a list of SHA-1 hash for all permutations of ASCII character string up to 8 characters length. If we don't count the tab character there are 95 printable characters in ASCII, so let's round that up to 100. `100 ^ 8` is `10 ^ 16`. Even if we could handle 1000 hashing per second, it takes `10 ^ 13` secs, or 316888 years.

Given you have some space in RAM, we could trade some of the expensive calculations for space by caching the result. This is called memoization. Here's the contract for [`Memo`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Memo.scala):

```scala
sealed trait Memo[@specialized(Int) K, @specialized(Int, Long, Double) V] {
  def apply(z: K => V): K => V
}
```

We pass in a potentially expensive function as an input and you get back a function that behaves the same but may cache the result. Under `Memo` object there are some default implementations of `Memo` like `Memo.mutableHashMapMemo[K, V]`, `Memo.weakHashMapMemo[K, V]`, and `Memo.arrayMemo[V]`.

In general, we should be careful with any of these optimization techniques. First the overall performance should be profiled to see if it in fact would contribute to time savings, and second space trade-off needs to be analyzed so it doesn't grow endlessly.

Let's implement Fibonacci number example from the [Memoization tutorial](http://www.haskell.org/haskellwiki/Memoization):

```scala
scala> val slowFib: Int => Int = {
         case 0 => 0
         case 1 => 1
         case n => slowFib(n - 2) + slowFib(n - 1)
       }
slowFib: Int => Int = <function1>

scala> slowFib(30)
res0: Int = 832040

scala> slowFib(40)
res1: Int = 102334155

scala> slowFib(45)
res2: Int = 1134903170
```

The `slowFib(45)` took a while to return. Now the memoized version:

```scala
scala> val memoizedFib: Int => Int = Memo.mutableHashMapMemo {
         case 0 => 0
         case 1 => 1
         case n => memoizedFib(n - 2) + memoizedFib(n - 1)
       }
memoizedFib: Int => Int = <function1>

scala> memoizedFib(30)
res12: Int = 832040

scala> memoizedFib(40)
res13: Int = 102334155

scala> memoizedFib(45)
res14: Int = 1134903170
```

Now these numbers come back instantaneously. The neat thing is that for both creating and using the memoized function, it feels very transparently done. Adam Rosien brings up that point in his [Scalaz "For the Rest of Us" talk](https://github.com/arosien/scalaz-base-talk-201208) ([video](http://www.youtube.com/watch?v=kcfIH3GYXMI)).
