
### Reader

LYAHFGG:

> In the chapter about applicatives, we saw that the function type, `(->) r` is an instance of `Functor`.

```scala
scala> val f = (_: Int) * 5
f: Int => Int = <function1>

scala> val g = (_: Int) + 3
g: Int => Int = <function1>

scala> (g map f)(8)
res22: Int = 55
```

> We've also seen that functions are applicative functors. They allow us to operate on the eventual results of functions as if we already had their results. 

```scala
scala> val f = ({(_: Int) * 2} |@| {(_: Int) + 10}) {_ + _}
warning: there were 1 deprecation warnings; re-run with -deprecation for details
f: Int => Int = <function1>

scala> f(3)
res35: Int = 19
```

> Not only is the function type `(->) r a` functor and an applicative functor, but it's also a monad. Just like other monadic values that we've met so far, a function can also be considered a value with a context. The context for functions is that that value is not present yet and that we have to apply that function to something in order to get its result value.

Let's try implementing the example:

```scala
scala> val addStuff: Int => Int = for {
         a <- (_: Int) * 2
         b <- (_: Int) + 10
       } yield a + b
addStuff: Int => Int = <function1>

scala> addStuff(3)
res39: Int = 19
```

> Both `(*2)` and `(+10)` get applied to the number `3` in this case. `return (a+b)` does as well, but it ignores it and always presents `a+b` as the result. For this reason, the function monad is also called the *reader* monad. All the functions read from a common source. 

Essentially, the reader monad lets us pretend the value is already there. I am guessing that this works only for functions that accepts one parameter. Unlike `Option` and `List` monads, neither `Writer` nor reader monad is available in the standard library. And they look pretty useful.

Let's pick it up from here later.
