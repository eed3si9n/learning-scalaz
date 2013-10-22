
### Show

LYAHFGG:

> ある値は、その値が `Show` 型クラスのインスタンスになっていれば、文字列として表現できます。

Scalaz で `Show` に対応する型クラスは `Show` だ:

```scala
scala> 3.show
res14: scalaz.Cord = 3

scala> 3.shows
res15: String = 3

scala> "hello".println
"hello"
```

`Cord` というのは潜在的に長い可能性のある文字列を保持できる純粋関数型データ構造のことらしい。
