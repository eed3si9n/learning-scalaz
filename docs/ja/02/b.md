
### Applicative

LYAHFGG:

> ここまではファンクター値を写すために、もっぱら 1 引数関数を使ってきました。では、2 引数関数でファンクターを写すと何が起こるでしょう？

```scala
scala> List(1, 2, 3, 4) map {(_: Int) * (_:Int)}
<console>:14: error: type mismatch;
 found   : (Int, Int) => Int
 required: Int => ?
              List(1, 2, 3, 4) map {(_: Int) * (_:Int)}
                                             ^
```

おっと。これはカリー化する必要がある:

```scala
scala> List(1, 2, 3, 4) map {(_: Int) * (_:Int)}.curried
res11: List[Int => Int] = List(<function1>, <function1>, <function1>, <function1>)

scala> res11 map {_(9)}
res12: List[Int] = List(9, 18, 27, 36)
```

LYAHFGG:

> `Control.Applicative` モジュールにある型クラス `Applicative` に会いに行きましょう！型クラス `Applicative` は、2つの関数 `pure` と `<*>` を定義しています。

Scalaz の `Applicative` のコントラクトも見てみよう:

```scala
trait Applicative[F[_]] extends Apply[F] { self =>
  def point[A](a: => A): F[A]

  /** alias for `point` */
  def pure[A](a: => A): F[A] = point(a)

  ...
}
```

`Applicative` は別の型クラス `Apply` を継承し、それ自身も `point` とそのエイリアス `pure` を導入する。

LYAHFGG:

> `pure` は任意の型の引数を受け取り、それをアプリカティブ値の中に入れて返します。 ... アプリカティブ値は「箱」というよりも「文脈」と考えるほうが正確かもしれません。`pure` は、値を引数に取り、その値を何らかのデフォルトの文脈（元の値を再現できるような最小限の文脈）に置くのです。

Scalaz は `pure` のかわりに `point` という名前が好きみたいだ。見たところ `A` の値を受け取り `F[A]` を返すコンストラクタみたいだ。これは演算子こそは導入しないけど、全てのデータ型に `point` メソッドとシンボルを使ったエイリアス `η` を導入する。

```scala
scala> 1.point[List]
res14: List[Int] = List(1)

scala> 1.point[Option]
res15: Option[Int] = Some(1)

scala> 1.point[Option] map {_ + 2}
res16: Option[Int] = Some(3)

scala> 1.point[List] map {_ + 2}
res17: List[Int] = List(3)
```

ちょっとうまく説明できないけど、コンストラクタが抽象化されているのは何か可能性を感じるものがある。

### Apply

LYAHFGG:

> `<*>` は `fmap` の強化版なのです。`fmap` が普通の関数とファンクター値を引数に取って、関数をファンクター値の中の値に適用してくれるのに対し、`<*>` は関数の入っているファンクター値と値の入っているファンクター値を引数に取って、1つ目のファンクターの中身である関数を2つ目のファンクターの中身に適用するのです。

```scala
trait Apply[F[_]] extends Functor[F] { self =>
  def ap[A,B](fa: => F[A])(f: => F[A => B]): F[B]
}
```

`ap` を使って `Apply` は `<*>`、`*>`、`<*` 演算子を可能とする。

```scala
scala>  9.some <*> {(_: Int) + 3}.some
res20: Option[Int] = Some(12)
```

期待通りだ。

`*>` と `<*` は左辺項か右辺項のみ返すバリエーションだ。

```scala
scala> 1.some <* 2.some
res35: Option[Int] = Some(1)

scala> none <* 2.some
res36: Option[Nothing] = None

scala> 1.some *> 2.some
res38: Option[Int] = Some(2)

scala> none *> 2.some
res39: Option[Int] = None
```

### Apply としての Option

`<*>` を使えばいい。

```scala
scala> 9.some <*> {(_: Int) + 3}.some
res57: Option[Int] = Some(12)

scala> 3.some <*> { 9.some <*> {(_: Int) + (_: Int)}.curried.some }
res58: Option[Int] = Some(12)
```

### Applicative Style

もう 1つ見つけたのが、コンテナから値だけを抽出して 1つの関数を適用する新記法だ:

```scala
scala> ^(3.some, 5.some) {_ + _}
res59: Option[Int] = Some(8)

scala> ^(3.some, none[Int]) {_ + _}
res60: Option[Int] = None
```

これは 1関数の場合はいちいちコンテナに入れなくてもいいから便利そうだ。これは推測だけど、これのお陰で Scalaz 7 は `Applicative` そのものでは何も演算子を導入していないんだと思う。実際どうなのかはともかく、`Pointed` も `<\$>` もいらないみたいだ。

だけど、`^(f1, f2) {...}` スタイルに問題が無いわけではない。どうやら `Function1`、`Writer`、`Validation` のような 2つの型パラメータを取る Applicative を処理できないようだ。もう 1つ Applicative Builder という Scalaz 6 から使われていたらしい方法がある。 M3 で deprecated になったけど、`^(f1, f2) {...}` の問題のため、近い将来名誉挽回となるらしい。

こう使う:

```scala
scala> (3.some |@| 5.some) {_ + _}
res18: Option[Int] = Some(8)
```

今の所は `|@|` スタイルを使おう。

### Apply としての List

LYAHFGG:

> リスト（正確に言えばリスト型のコンストラクタ `[]`）もアプリカティブファンクターです。意外ですか？

`<*>` と `|@|` が使えるかみてみよう:

```scala
scala> List(1, 2, 3) <*> List((_: Int) * 0, (_: Int) + 100, (x: Int) => x * x)
res61: List[Int] = List(0, 0, 0, 101, 102, 103, 1, 4, 9)

scala> List(3, 4) <*> { List(1, 2) <*> List({(_: Int) + (_: Int)}.curried, {(_: Int) * (_: Int)}.curried) }
res62: List[Int] = List(4, 5, 5, 6, 3, 4, 6, 8)

scala> (List("ha", "heh", "hmm") |@| List("?", "!", ".")) {_ + _}
res63: List[String] = List(ha?, ha!, ha., heh?, heh!, heh., hmm?, hmm!, hmm.)
```

### Zip List

LYAHFGG:

> ところが、`[(+3),(*2)] <*> [1,2]` の挙動は、左辺の1つ目の関数を右辺の1つ目の値に適用し、左辺の2つ目の関数を右辺の2つ目の値に適用する、というのではまずいのでしょうか？それなら結果は `[4,4]` になるはずです。これは `[1 + 3, 2 * 2]` と考えることもできます。

これは Scalaz で書けるけど、簡単ではない。

```scala
scala> streamZipApplicative.ap(Tags.Zip(Stream(1, 2))) (Tags.Zip(Stream({(_: Int) + 3}, {(_: Int) * 2})))
res32: scala.collection.immutable.Stream[Int] with Object{type Tag = scalaz.Tags.Zip} = Stream(4, ?)

scala> res32.toList
res33: List[Int] = List(4, 4)
```

Tagged type を使った例は明日また詳しく説明する。

### Applicative の便利な関数

LYAHFGG:

> `Control.Applicative` には `liftA2` という、以下のような型を持つ関数があります。

```haskell
liftA2 :: (Applicative f) => (a -> b -> c) -> f a -> f b -> f c .
```

`Apply[F].lift2` というものがある:

```scala
scala> Apply[Option].lift2((_: Int) :: (_: List[Int]))
res66: (Option[Int], Option[List[Int]]) => Option[List[Int]] = <function2>

scala> res66(3.some, List(4).some)
res67: Option[List[Int]] = Some(List(3, 4))
```

LYAHFGG:

> では、「アプリカティブ値のリスト」を取って「リストを返り値として持つ1つのアプリカティブ値」を返す関数を実装してみましょう。これを `sequenceA` と呼ぶことにします。

```haskell
sequenceA :: (Applicative f) => [f a] -> f [a]  
sequenceA [] = pure []  
sequenceA (x:xs) = (:) <\$> x <*> sequenceA xs  
```

これを Scalaz でも実装できるか試してみよう!

```scala
scala> def sequenceA[F[_]: Applicative, A](list: List[F[A]]): F[List[A]] = list match {
         case Nil     => (Nil: List[A]).point[F]
         case x :: xs => (x |@| sequenceA(xs)) {_ :: _} 
       }
sequenceA: [F[_], A](list: List[F[A]])(implicit evidence\$1: scalaz.Applicative[F])F[List[A]]
```

テストしてみよう:

```scala
scala> sequenceA(List(1.some, 2.some))
res82: Option[List[Int]] = Some(List(1, 2))

scala> sequenceA(List(3.some, none, 1.some))
res85: Option[List[Int]] = None

scala> sequenceA(List(List(1, 2, 3), List(4, 5, 6)))
res86: List[List[Int]] = List(List(1, 4), List(1, 5), List(1, 6), List(2, 4), List(2, 5), List(2, 6), List(3, 4), List(3, 5), List(3, 6))
```

正しい答えが得られた。興味深いのは結局 `Pointed` が必要になったことと、`sequenceA` が型クラスに関してジェネリックなことだ。

`Function1` の片側が `Int` に固定された例は、残念ながら黒魔術を召喚する必要がある。

```scala
scala> type Function1Int[A] = ({type l[A]=Function1[Int, A]})#l[A]
defined type alias Function1Int

scala> sequenceA(List((_: Int) + 3, (_: Int) + 2, (_: Int) + 1): List[Function1Int[Int]])
res1: Int => List[Int] = <function1>

scala> res1(3)
res2: List[Int] = List(6, 5, 4)
```

結構長くなったけど、ここまでたどり着けて良かったと思う。続きはまたあとで。
