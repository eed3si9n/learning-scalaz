<div class="floatingimage">
<img src="http://eed3si9n.com/images/openphoto-19035bw.jpg">
<div class="credit">reynaldo f. tamayo for openphoto.net</div>
</div>


今日は論文をいくつか飛ばし読みしてみよう。まずは Jeremy Gibbons さんの [Origami programming](http://www.cs.ox.ac.uk/jeremy.gibbons/publications/origami.pdf) だ。

### Origami programming

Gibbons さん曰く:

> In this chapter we will look at folds and unfolds as abstractions. In a precise technical sense, folds and unfolds are the natural patterns of computation over recursive datatypes; unfolds generate data structures and folds consume them.

`foldLeft` は [4日目](http://eed3si9n.com/ja/learning-scalaz-day4)に `Foldable` を使ったときにみたけど、unfold って何だろう?

> The dual of folding is unfolding. The Haskell standard List library deﬁnes the function `unfoldr` for generating lists.

Hoogle には以下の例がある:

```haskell
Prelude Data.List> unfoldr (\b -> if b == 0 then Nothing else Just (b, b-1)) 10
[10,9,8,7,6,5,4,3,2,1]
```

### DList

`DList` というデータ構造があって、それは `DList.unfoldr` をサポートする。`DList` もしくは差分リスト (difference list) は定数時間での追加をサポートするデータ構造だ。

```scala
scala> DList.unfoldr(10, { (x: Int) => if (x == 0) none else (x, x - 1).some })
res50: scalaz.DList[Int] = scalaz.DListFunctions$$anon$3@70627153

scala> res50.toList
res51: List[Int] = List(10, 9, 8, 7, 6, 5, 4, 3, 2, 1)
```

### Stream の畳込み

Scalaz では `StreamFunctions` で定義されている `unfold` が `import Scalaz._` で導入される:

```scala
scala> unfold(10) { (x) => if (x == 0) none else (x, x - 1).some }
res36: Stream[Int] = Stream(10, ?)

scala> res36.toList
res37: List[Int] = List(10, 9, 8, 7, 6, 5, 4, 3, 2, 1)
```

論文にある選択ソートの例を実装してみる:

```scala
scala> def minimumS[A: Order](stream: Stream[A]) = stream match {
         case x #:: xs => xs.foldLeft(x) {_ min _}
       }
minimumS: [A](stream: Stream[A])(implicit evidence$1: scalaz.Order[A])A

scala> def deleteS[A: Equal](y: A, stream: Stream[A]): Stream[A] = (y, stream) match {
         case (_, Stream()) => Stream()
         case (y, x #:: xs) =>
           if (y === x) xs
           else x #:: deleteS(y, xs) 
       }
deleteS: [A](y: A, stream: Stream[A])(implicit evidence$1: scalaz.Equal[A])Stream[A]

scala> def delmin[A: Order](stream: Stream[A]): Option[(A, Stream[A])] = stream match {
         case Stream() => none
         case xs =>
           val y = minimumS(xs)
           (y, deleteS(y, xs)).some
       }
delmin: [A](stream: Stream[A])(implicit evidence$1: scalaz.Order[A])Option[(A, Stream[A])]

scala> def ssort[A: Order](stream: Stream[A]): Stream[A] = unfold(stream){delmin[A]}
ssort: [A](stream: Stream[A])(implicit evidence$1: scalaz.Order[A])Stream[A]

scala> ssort(Stream(1, 3, 4, 2)).toList
res55: List[Int] = List(1, 2, 3, 4)
```

これは `foldLeft` と `unfold` を使っているからおりがみ (origami) プログラミングということなんだろうか? これは [The Fun of Programming](http://www.cs.ox.ac.uk/publications/books/fop/) (邦訳は[関数プログラミングの楽しみ](http://www.amazon.co.jp/dp/4274068056)) の 1章として 2003年に書かれたけど、おりがみプログラミングがその後流行ったかどうかは僕は知らない。
