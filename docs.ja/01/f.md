
### Enum

LYAHFGG:

> `Enum` のインスタンスは、順番に並んだ型、つまり要素の値を列挙できる型です。`Enum` 型クラスの主な利点は、その値をレンジの中で使えることです。また、`Enum` のインスタンスの型には後者関数 `succ` と前者関数 `pred` も定義されます。

Scalaz で `Enum` に対応する型クラスは `Enum` だ:

```scala
scala> 'a' to 'e'
res30: scala.collection.immutable.NumericRange.Inclusive[Char] = NumericRange(a, b, c, d, e)

scala> 'a' |-> 'e'
res31: List[Char] = List(a, b, c, d, e)

scala> 3 |=> 5
res32: scalaz.EphemeralStream[Int] = scalaz.EphemeralStreamFunctions$$anon$4@6a61c7b6

scala> 'B'.succ
res33: Char = C
```

`Order` 型クラスの上に `pred` と `succ` メソッドを宣言することで、標準の `to` のかわりに、`Enum` は `List` を返す `|->` を可能とする。他にも `-+-`、`---`、`from`、 `fromStep`、`pred`、`predx`、`succ`、`succx`、`|-->`、`|->`、`|==>`、`|=>` など多くの演算があるが、全て前後にステップ移動するか範囲を返すものだ。
