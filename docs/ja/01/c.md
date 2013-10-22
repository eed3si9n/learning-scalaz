
### Order

LYAHFGG:

> `Ord` は、何らかの順序を付けられる型のための型クラスです。`Ord` はすべての標準的な大小比較関数、`>`、`<`、`>=`、 `<=` をサポートします。

Scalaz で `Ord` に対応する型クラスは `Order` だ:

```scala
scala> 1 > 2.0
res8: Boolean = false

scala> 1 gt 2.0
<console>:14: error: could not find implicit value for parameter F0: scalaz.Order[Any]
              1 gt 2.0
              ^

scala> 1.0 ?|? 2.0
res10: scalaz.Ordering = LT

scala> 1.0 max 2.0
res11: Double = 2.0
```

`Order` は `Ordering` (`LT`, `GT`, `EQ`) を返す `?|?` 演算を可能とする。また、`order` メソッドを宣言することで `lt`、`gt`、`lte`、`gte`、`min`、そして `max` 演算子を可能とする。`Equal` 同様 `Int` と `Double` の比較はコンパイルを失敗させる。
