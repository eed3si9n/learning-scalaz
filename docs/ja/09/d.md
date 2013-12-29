---
out: Lawless-typeclasses.html
---

  [pc]: https://groups.google.com/d/msg/scalaz/7OE_Nsreqq0/vUs7-tyf1nsJ
  [why]: http://www.haskell.org/haskellwiki/Why_not_Pointed%3F

無法者の型クラス
--------------

Scalaz 7.0 は、今の Scalaz プロジェクトの考えでは無法 (lawless) だと烙印を押された型クラス `Length`、`Index`、`Each` を含む。これらに関する議論は [#278 What to do about lawless classes?](https://github.com/scalaz/scalaz/issues/278) や [(presumably) Bug in IndexedSeq Index typeclass](https://groups.google.com/d/msg/scalaz/aJx69eWMK6M/gAtne2v6RJYJ) を参照。それらの型クラスは 7.1 で廃止予定 (deprecated) 扱いされ、7.2 では削除される予定だ。

### Length

長さを表現した型クラス。以下が [`Length` 型クラスのコントラクト](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Length.scala)だ:

```scala
trait Length[F[_]]  { self =>
  def length[A](fa: F[A]): Int
}
```

これは `length` メソッドを導入する。Scala 標準ライブラリだと `SeqLike` で入ってくるため、`SeqLike` を継承しないけど長さを持つデータ構造があれば役に立つのかもしれない。

### Index

コンテナへのランダムアクセスを表すのが [`Index`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Index.scala) だ:

```scala
trait Index[F[_]]  { self =>
  def index[A](fa: F[A], i: Int): Option[A]
}
```

これは `index` と `indexOr` メソッドを導入する:

```scala
trait IndexOps[F[_],A] extends Ops[F[A]] {
  final def index(n: Int): Option[A] = F.index(self, n)
  final def indexOr(default: => A, n: Int): A = F.indexOr(self, default, n)
}
```

これは `List(n)` に似ているけど、範囲外の添字で呼び出すと `None` が返る:

```scala
scala> List(1, 2, 3)(3)
java.lang.IndexOutOfBoundsException: 3
        ...

scala> List(1, 2, 3) index 3
res62: Option[Int] = None
```

### Each

データ構造を走査して副作用のある関数を実行するために [`Each`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Each.scala) がある:

```scala
trait Each[F[_]]  { self =>
  def each[A](fa: F[A])(f: A => Unit)
}
```

これは `foreach` メソッドを導入する:

```scala
sealed abstract class EachOps[F[_],A] extends Ops[F[A]] {
  final def foreach(f: A => Unit): Unit = F.each(self)(f)
}
```

### Foldable かオレオレ型クラスを書くか?

上に挙げた機能のいくつかは `Foldable` を使ってエミュレートすることができるけども、[@nuttycom](https://github.com/scalaz/scalaz/issues/278#issuecomment-16748242)氏が指摘するように、それでは対象となるデータ構造が定数時間の `length` や `index` を実装していたとしても *O(n)* 時間を強要することになる。仮に `length` を抽象化して役に立つとした場合、恐らく自分で `Length` を書いてしまった方がいいだろう。

もし、一貫性に欠ける型クラスの実装が何らかの形で型安全性を劣化させているならライブラリから削除するのも止むを得ないと思うが、一見して `Length` や `Index` は `Vector` などのランダムアクセス可能なコンテナに対する合理的な抽象化のように見える。

### Pointed と Copointed

実は以前にも無法であるとして斬られた型クラスがあって `Pointed` と `Copointed` がそれだ。これに関しては参考になる議論が  [Pointed/Copointed][pc] や [Why not Pointed?][why] にある:

> `Pointed` は有用な法則を持たないし、皆が教えてくれる利用方法のほとんどが実際にはインスタンスそのものが提供している関係を ad hoc に乱用したものであることが多い。

これは興味深い指摘で、僕にも理解できるものだ。別の言い方をすると、もしどんなコンテナでも `Pointed` になることができるなら、それを使っているコードはあまり役に立たないものか、もしくはそれが何らかの特定のインスタンスであると暗に仮定したものではないかということだ。

続きはまた後で。
