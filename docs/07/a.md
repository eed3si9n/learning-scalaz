
### Applicative Builder

One thing I snuck in while covering the reader monad is the Applicative builder `|@|`. On [day 2](http://eed3si9n.com/learning-scalaz-day2) we introduced `^(f1, f2) {...}` style that was introduced in 7.0.0-M3, but that does not seem to work for functions or any type constructor with two parameters.

The discussion on the Scalaz mailing list seems to suggest that `|@|` will be undeprecated, so that's the style we will be using, which looks like this:

```scala
scala> (3.some |@| 5.some) {_ + _}
res18: Option[Int] = Some(8)

scala> val f = ({(_: Int) * 2} |@| {(_: Int) + 10}) {_ + _}
f: Int => Int = <function1>
```
