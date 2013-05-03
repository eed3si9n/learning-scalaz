---
out: MonadPlus.html
---

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
