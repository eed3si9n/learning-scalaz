
### Length

長さを表現した型クラスもある。以下が [`Length` 型クラスのコントラクト](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Length.scala)だ:

```scala
trait Length[F[_]]  { self =>
  def length[A](fa: F[A]): Int
}
```

これは `length` メソッドを導入する。Scala 標準ライブラリだと `SeqLike` で入ってくるため、`SeqLike` を継承しないけど長さを持つデータ構造があれば役に立つのかもしれない。
