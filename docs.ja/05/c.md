---
out: List-Monad.html
---

### List モナド

LYAHFGG:

> 一方、`[3,8,9]` のような値は複数の計算結果を含んでいるとも、複数の候補値を同時に重ね合わせたような1つの値であるとも解釈できます。リストをアプリカティブ・スタイルで使うと、非決定性を表現していることがはっきりします。

まずは Applicative としての `List` を復習する (この記法は Scalaz 7.0.0 が必要かもしれない):

```scala
scala> ^(List(1, 2, 3), List(10, 100, 100)) {_ * _}
res29: List[Int] = List(10, 100, 100, 20, 200, 200, 30, 300, 300)
```

> それでは、非決定的値を関数に食わせてみましょう。

```scala
scala> List(3, 4, 5) >>= {x => List(x, -x)}
res30: List[Int] = List(3, -3, 4, -4, 5, -5)
```

モナディックな視点に立つと、`List` というコンテキストは複数の解がありうる数学的な値を表す。それ以外は、`for` を使って `List` を操作するなどは素の Scala と変わらない:

```scala
scala> for {
         n <- List(1, 2)
         ch <- List('a', 'b')
       } yield (n, ch)
res33: List[(Int, Char)] = List((1,a), (1,b), (2,a), (2,b))
```

### MonadPlus と guard 関数

Scala の `for` 構文はフィルタリングができる:

```scala
scala> for {
         x <- 1 |-> 50 if x.shows contains '7'
       } yield x
res40: List[Int] = List(7, 17, 27, 37, 47)
```

LYAHFGG:

> `MonadPlus` は、モノイドの性質をあわせ持つモナドを表す型クラスです。

以下が [`MonadPlus` の型クラスのコントラクト](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/MonadPlus.scala)だ:

```scala
trait MonadPlus[F[_]] extends Monad[F] with ApplicativePlus[F] { self =>
  ...
}
```

### Plus、PlusEmpty、と ApplicativePlus

これは [`ApplicativePlus`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/ApplicativePlus.scala) を継承している:

```scala
trait ApplicativePlus[F[_]] extends Applicative[F] with PlusEmpty[F] { self =>
  ...
}
```

そして、それは [`PlusEmpty`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/PlusEmpty.scala) を継承している:

```scala
trait PlusEmpty[F[_]] extends Plus[F] { self =>
  ////
  def empty[A]: F[A]
}
```

そして、それは [`Plus`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/PlusEmpty.scala) を継承している:

```scala
trait Plus[F[_]]  { self =>
  def plus[A](a: F[A], b: => F[A]): F[A]
}
```


`Semigroup[A]` と `Monoid[A]` 同様に、`Plus[F[_]]` と `PlusEmpty[F[_]]` はそれらのインスタンスが `plus` と `empty` を実装することを要請する。違いはこれが型コンストラクタ (`F[_]`) レベルであることだ。

`Plus` は 2つのコンテナを連結する `<+>` 演算子を導入する:

```scala
scala> List(1, 2, 3) <+> List(4, 5, 6)
res43: List[Int] = List(1, 2, 3, 4, 5, 6)
```

### MonadPlus 再び

`MonadPlus` は `filter` 演算を導入する。

```scala
scala> (1 |-> 50) filter { x => x.shows contains '7' }
res46: List[Int] = List(7, 17, 27, 37, 47)
```
