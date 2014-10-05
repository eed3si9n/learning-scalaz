
### Foldable

LYAHFGG:

> 畳み込み相性の良いデータ構造は実にたくさんあるので、`Foldable` 型クラスが導入されました。`Functor` が関数で写せるものを表すように、`Foldable` は畳み込みできるものを表しています。

Scalaz でこれに対応するものも `Foldable` と呼ばれている。[型クラスのコントラクト](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Foldable.scala#L10-14)も見てみよう:

```scala
trait Foldable[F[_]] { self =>
  /** Map each element of the structure to a [[scalaz.Monoid]], and combine the results. */
  def foldMap[A,B](fa: F[A])(f: A => B)(implicit F: Monoid[B]): B

  /**Right-associative fold of a structure. */
  def foldRight[A, B](fa: F[A], z: => B)(f: (A, => B) => B): B

  ...
}
```

[演算子](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/FoldableSyntax.scala)はこれだ:

```scala
/** Wraps a value `self` and provides methods related to `Foldable` */
trait FoldableOps[F[_],A] extends Ops[F[A]] {
  implicit def F: Foldable[F]
  ////
  final def foldMap[B: Monoid](f: A => B = (a: A) => a): B = F.foldMap(self)(f)
  final def foldRight[B](z: => B)(f: (A, => B) => B): B = F.foldRight(self, z)(f)
  final def foldLeft[B](z: B)(f: (B, A) => B): B = F.foldLeft(self, z)(f)
  final def foldRightM[G[_], B](z: => B)(f: (A, => B) => G[B])(implicit M: Monad[G]): G[B] = F.foldRightM(self, z)(f)
  final def foldLeftM[G[_], B](z: B)(f: (B, A) => G[B])(implicit M: Monad[G]): G[B] = F.foldLeftM(self, z)(f)
  final def foldr[B](z: => B)(f: A => (=> B) => B): B = F.foldr(self, z)(f)
  final def foldl[B](z: B)(f: B => A => B): B = F.foldl(self, z)(f)
  final def foldrM[G[_], B](z: => B)(f: A => ( => B) => G[B])(implicit M: Monad[G]): G[B] = F.foldrM(self, z)(f)
  final def foldlM[G[_], B](z: B)(f: B => A => G[B])(implicit M: Monad[G]): G[B] = F.foldlM(self, z)(f)
  final def foldr1(f: (A, => A) => A): Option[A] = F.foldr1(self)(f)
  final def foldl1(f: (A, A) => A): Option[A] = F.foldl1(self)(f)
  final def sumr(implicit A: Monoid[A]): A = F.foldRight(self, A.zero)(A.append)
  final def suml(implicit A: Monoid[A]): A = F.foldLeft(self, A.zero)(A.append(_, _))
  final def toList: List[A] = F.toList(self)
  final def toIndexedSeq: IndexedSeq[A] = F.toIndexedSeq(self)
  final def toSet: Set[A] = F.toSet(self)
  final def toStream: Stream[A] = F.toStream(self)
  final def all(p: A => Boolean): Boolean = F.all(self)(p)
  final def ∀(p: A => Boolean): Boolean = F.all(self)(p)
  final def allM[G[_]: Monad](p: A => G[Boolean]): G[Boolean] = F.allM(self)(p)
  final def anyM[G[_]: Monad](p: A => G[Boolean]): G[Boolean] = F.anyM(self)(p)
  final def any(p: A => Boolean): Boolean = F.any(self)(p)
  final def ∃(p: A => Boolean): Boolean = F.any(self)(p)
  final def count: Int = F.count(self)
  final def maximum(implicit A: Order[A]): Option[A] = F.maximum(self)
  final def minimum(implicit A: Order[A]): Option[A] = F.minimum(self)
  final def longDigits(implicit d: A <:< Digit): Long = F.longDigits(self)
  final def empty: Boolean = F.empty(self)
  final def element(a: A)(implicit A: Equal[A]): Boolean = F.element(self, a)
  final def splitWith(p: A => Boolean): List[List[A]] = F.splitWith(self)(p)
  final def selectSplit(p: A => Boolean): List[List[A]] = F.selectSplit(self)(p)
  final def collapse[X[_]](implicit A: ApplicativePlus[X]): X[A] = F.collapse(self)
  final def concatenate(implicit A: Monoid[A]): A = F.fold(self)
  final def traverse_[M[_]:Applicative](f: A => M[Unit]): M[Unit] = F.traverse_(self)(f)

  ////
}
```

これはスゴい。コレクションライブラリさながらだけど、`Order` などの型クラスを駆使している。畳込みをやってみよう:

```scala
scala> List(1, 2, 3).foldRight (1) {_ * _}
res49: Int = 6

scala> 9.some.foldLeft(2) {_ + _}
res50: Int = 11
```

これらは標準ライブラリにも入っている。`foldMap` 演算子も試してみよう。`Monoid[A]` が `zero` と `|+|` を提供するから、畳込みに十分な情報がある。`Foldable` がいつも `Monoid` を持っているとは限らないので、`[B: Monoid]` である `A => B` 関数が必要だ:

```scala
scala> List(1, 2, 3) foldMap {identity}
res53: Int = 6

scala> List(true, false, true, true) foldMap {Tags.Disjunction.apply}
res56: scalaz.@@[Boolean,scalaz.Tags.Disjunction] = true
```

`Tags.Disjunction(true)` と一つ一つ書きだして `|+|` でつなぐよりずっと楽だ。

続きはまた後で。今週は出張なので、ちょっとペースは落ちるかも。
