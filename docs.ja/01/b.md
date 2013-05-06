
### Equal

LYAHFGG:

> `Eq` は等値性をテストできる型に使われます。Eq のインスタンスが定義すべき関数は `==` と `/=` です。

Scalaz で `Eq` 型クラスと同じものは `Equal` と呼ばれている:

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

標準の `==` のかわりに、`Equal` は `equal` メソッドを宣言することで `===`、`=/=`、と `assert_===` 演算を可能とする。主な違いは `Int` と `String` と比較すると `===` はコンパイルに失敗することだ。

注意: 初出ではここで `=/=` じゃなくて `/==` を使っていたけども、Eiríkr Åsheim さんに以下の通り教えてもらった:

<blockquote class="twitter-tweet"><p>@<a href="https://twitter.com/eed3si9n">eed3si9n</a> hey, was reading your scalaz tutorials. you should encourage people to use =/= and not /== since the latter has bad precedence.</p>&mdash; Eiríkr Åsheim (@d6) <a href="https://twitter.com/d6/status/243557748091011074">September 6, 2012</a></blockquote>
<script async src="//platform.twitter.com/widgets.js" charset="utf-8"></script>

> /== は優先順位壊れてるから =/= を推奨すべき。

通常、`!=` のような比較演算子は `&&` や通常の文字列などに比べて高い優先順位を持つ。ところが、`/==` は `=` で終わるが `=` で始まらないため代入演算子のための特殊ルールが発動し、優先順位の最底辺に落ちてしまう:

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
