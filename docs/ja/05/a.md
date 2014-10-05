---
out: Monad.html
---

### モナドがいっぱい

今日は[すごいHaskellたのしく学ぼう](http://www.amazon.co.jp/dp/4274068854)の新しい章「モナドがいっぱい」を始めることができる。

> モナドはある願いを叶えるための、アプリカティブ値の自然な拡張です。その願いとは、「普通の値 `a` を取って文脈付きの値を返す関数に、文脈付きの値 `m a` を渡したい」というものです。

Scalaz でもモナドは `Monad` と呼ばれている。[型クラスのコントラクト]($scalazBaseUrl$/core/src/main/scala/scalaz/Monad.scala)はこれだ:

```scala
trait Monad[F[_]] extends Applicative[F] with Bind[F] { self =>
  ////
}
```

これは `Applicative` と `Bind` を拡張する。`Bind` を見てみよう。

### Bind

以下が [`Bind` のコントラクト]($scalazBaseUrl$/core/src/main/scala/scalaz/Bind.scala)だ:

```scala
trait Bind[F[_]] extends Apply[F] { self =>
  /** Equivalent to `join(map(fa)(f))`. */
  def bind[A, B](fa: F[A])(f: A => F[B]): F[B]
}
```

そして、以下が[演算子]($scalazBaseUrl$/core/src/main/scala/scalaz/syntax/BindSyntax.scala):

```scala
/** Wraps a value `self` and provides methods related to `Bind` */
trait BindOps[F[_],A] extends Ops[F[A]] {
  implicit def F: Bind[F]
  ////
  import Liskov.<~<

  def flatMap[B](f: A => F[B]) = F.bind(self)(f)
  def >>=[B](f: A => F[B]) = F.bind(self)(f)
  def ∗[B](f: A => F[B]) = F.bind(self)(f)
  def join[B](implicit ev: A <~< F[B]): F[B] = F.bind(self)(ev(_))
  def μ[B](implicit ev: A <~< F[B]): F[B] = F.bind(self)(ev(_))
  def >>[B](b: F[B]): F[B] = F.bind(self)(_ => b)
  def ifM[B](ifTrue: => F[B], ifFalse: => F[B])(implicit ev: A <~< Boolean): F[B] = {
    val value: F[Boolean] = Liskov.co[F, A, Boolean](ev)(self)
    F.ifM(value, ifTrue, ifFalse)
  }
  ////
}
```

`flatMap` 演算子とシンボルを使ったエイリアス `>>=` と `∗` を導入する。他の演算子に関しては後回しにしよう。とりあえず標準ライブラリで `flatMap` は慣れている:

```scala
scala> 3.some flatMap { x => (x + 1).some }
res2: Option[Int] = Some(4)

scala> (none: Option[Int]) flatMap { x => (x + 1).some }
res3: Option[Int] = None
```

### Monad

`Monad` に戻ろう:

```scala
trait Monad[F[_]] extends Applicative[F] with Bind[F] { self =>
  ////
}
```

Haskell と違って `Monad[F[_]]` は `Applicative[F[_]]` を継承するため、`return` と `pure` と名前が異なるという問題が生じていない。両者とも `point` だ。

```scala
scala> Monad[Option].point("WHAT")
res5: Option[String] = Some(WHAT)

scala> 9.some flatMap { x => Monad[Option].point(x * 10) }
res6: Option[Int] = Some(90)

scala> (none: Option[Int]) flatMap { x => Monad[Option].point(x * 10) }
res7: Option[Int] = None
```
