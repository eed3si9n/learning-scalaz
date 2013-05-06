
### Equal

LYAHFGG:

> `Eq` is used for types that support equality testing. The functions its members implement are `==` and `/=`.

Scalaz equivalent for the `Eq` typeclass is called `Equal`:

```scala
scala> 1 === 1
res0: Boolean = true

scala> 1 === "foo"
<console>:14: error: could not find implicit value for parameter F0: scalaz.Equal[Object]
              1 === "foo"
              ^

scala> 1 == "foo"
<console>:14: warning: comparing values of types Int and String using `==' will always yield false
              1 == "foo"
                ^
res2: Boolean = false

scala> 1.some =/= 2.some
res3: Boolean = true

scala> 1 assert_=== 2
java.lang.RuntimeException: 1 ≠ 2
```

Instead of the standard `==`, `Equal` enables `===`, `=/=`, and `assert_===` syntax by declaring `equal` method. The main difference is that `===` would fail compilation if you tried to compare `Int` and `String`.

Note: I originally had `/==` instead of `=/=`, but Eiríkr Åsheim pointed out to me:

<blockquote class="twitter-tweet"><p>@<a href="https://twitter.com/eed3si9n">eed3si9n</a> hey, was reading your scalaz tutorials. you should encourage people to use =/= and not /== since the latter has bad precedence.</p>&mdash; Eiríkr Åsheim (@d6) <a href="https://twitter.com/d6/status/243557748091011074">September 6, 2012</a></blockquote>
<script async src="//platform.twitter.com/widgets.js" charset="utf-8"></script>

Normally comparison operators like `!=` have lower higher precedence than `&&`, all letters, etc. Due to special precedence rule `/==` is recognized as an assignment operator because it ends with `=` and does not start with `=`, which drops to the bottom of the precedence:

```scala
scala> 1 != 2 && false
res4: Boolean = false

scala> 1 /== 2 && false
<console>:14: error: value && is not a member of Int
              1 /== 2 && false
                      ^

scala> 1 =/= 2 && false
res6: Boolean = false
```
