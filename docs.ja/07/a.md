
### Applicative Builder

実はリーダーモナドの話をしながらこっそり Applicative builder `|@|` を使った。[2日目](http://eed3si9n.com/ja/learning-scalaz-day2) に 7.0.0-M3 から新しく導入された `^(f1, f2) {...}` スタイルを紹介したけど、関数などの 2つの型パラメータを取る型コンストラクタでうまく動作しないみたいことが分かった。

Scalaz のメーリングリストを見ると `|@|` は deprecate 状態から復活するらしいので、これからはこのスタイルを使おう:

```scala
scala> (3.some |@| 5.some) {_ + _}
res18: Option[Int] = Some(8)

scala> val f = ({(_: Int) * 2} |@| {(_: Int) + 10}) {_ + _}
f: Int => Int = <function1>
```
