
### Bounded

> `Bounded` 型クラスのインスタンスは上限と下限を持ち、それぞれ `minBound` と `maxBound` 関数で調べることができます。

Scalaz で `Bounded` に対応する型クラスは再び `Enum` みたいだ:

```scala
scala> implicitly[Enum[Char]].min
res43: Option[Char] = Some(?)

scala> implicitly[Enum[Char]].max
res44: Option[Char] = Some( )

scala> implicitly[Enum[Double]].max
res45: Option[Double] = Some(1.7976931348623157E308)

scala> implicitly[Enum[Int]].min
res46: Option[Int] = Some(-2147483648)

scala> implicitly[Enum[(Boolean, Int, Char)]].max
<console>:14: error: could not find implicit value for parameter e: scalaz.Enum[(Boolean, Int, Char)]
              implicitly[Enum[(Boolean, Int, Char)]].max
                        ^
```

`Enum` 型クラスのインスタンスは最大値に対して `Option[T]` を返す。
