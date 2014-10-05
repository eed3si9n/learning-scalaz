---
out: Some-useful-monadic-functions.html
---

### 便利なモナディック関数特集

[すごいHaskellたのしく学ぼう](http://www.amazon.co.jp/dp/4274068854) 曰く:

> この節では、モナド値を操作したり、モナド値を返したりする関数（両方でも可！）をいくつか紹介します。そんな関数は**モナディック関数**と呼ばれます。

Scalaz の `Monad` は `Applicative` を継承しているため、全てのモナドが Functor であることが保証される。そのため、`map` や `<*>` 演算子も使える。

#### join メソッド

LYAHFGG:

> 実は、任意の入れ子になったモナドは平らにできるんです。そして実は、これはモナド特有の性質なのです。このために、`join` という関数が用意されています。

Scalaz では `join` メソッド (およびシンボルを使ったエイリアス `μ`) は `Bind` によって導入される:

```scala
trait BindOps[F[_],A] extends Ops[F[A]] {
  ...
  def join[B](implicit ev: A <~< F[B]): F[B] = F.bind(self)(ev(_))
  def μ[B](implicit ev: A <~< F[B]): F[B] = F.bind(self)(ev(_))
  ...
}
```

使ってみよう:

```scala
scala> (Some(9.some): Option[Option[Int]]).join
res9: Option[Int] = Some(9)

scala> (Some(none): Option[Option[Int]]).join
res10: Option[Int] = None

scala> List(List(1, 2, 3), List(4, 5, 6)).join
res12: List[Int] = List(1, 2, 3, 4, 5, 6)

scala> 9.right[String].right[String].join
res15: scalaz.Unapply[scalaz.Bind,scalaz.\/[String,scalaz.\/[String,Int]]]{type M[X] = scalaz.\/[String,X]; type A = scalaz.\/[String,Int]}#M[Int] = \/-(9)

scala> "boom".left[Int].right[String].join
res16: scalaz.Unapply[scalaz.Bind,scalaz.\/[String,scalaz.\/[String,Int]]]{type M[X] = scalaz.\/[String,X]; type A = scalaz.\/[String,Int]}#M[Int] = -\/(boom)
```

#### filterM メソッド

LYAHFGG:

> `Control.Monad` モジュールの `filterM` こそ、まさにそのための関数です！
> ...
> 述語は `Bool` を結果とするモナド値を返しています。

Scalaz では `filterM` はいくつかの箇所で実装されている。

```scala
trait ListOps[A] extends Ops[List[A]] {
  ...
  final def filterM[M[_] : Monad](p: A => M[Boolean]): M[List[A]] = l.filterM(self)(p)
  ...
}
```

```scala
scala> List(1, 2, 3) filterM { x => List(true, false) }
res19: List[List[Int]] = List(List(1, 2, 3), List(1, 2), List(1, 3), List(1), List(2, 3), List(2), List(3), List())

scala> Vector(1, 2, 3) filterM { x => Vector(true, false) }
res20: scala.collection.immutable.Vector[Vector[Int]] = Vector(Vector(1, 2, 3), Vector(1, 2), Vector(1, 3), Vector(1), Vector(2, 3), Vector(2), Vector(3), Vector())
```

#### foldLeftM メソッド

LYAHFGG:

> `foldl` のモナド版が `foldM` です。

Scalaz でこれは `Foldable` に `foldLeftM` として実装されていて、`foldRightM` もある。

```scala
scala> def binSmalls(acc: Int, x: Int): Option[Int] = {
         if (x > 9) (none: Option[Int])
         else (acc + x).some
       }
binSmalls: (acc: Int, x: Int)Option[Int]

scala> List(2, 8, 3, 1).foldLeftM(0) {binSmalls}
res25: Option[Int] = Some(14)

scala> List(2, 11, 3, 1).foldLeftM(0) {binSmalls}
res26: Option[Int] = None
```
