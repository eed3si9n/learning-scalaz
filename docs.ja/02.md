  [day1]: http://eed3si9n.com/ja/learning-scalaz-day1
  [tt]: http://learnyouahaskell.com/types-and-typeclasses

[昨日][day1]は[すごいHaskellたのしく学ぼう](http://www.amazon.co.jp/dp/4274068854)の原書 [Learn You a Haskell for Great Good][tt] を頼りに `Equal` などの Scalaz の型クラスを見てきた。

### Functor

LYAHFGG:

> And now, we're going to take a look at the `Functor` typeclass, which is basically for things that can be mapped over.

本のとおり、[実装がどうなってるか](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Functor.scala)をみてみよう:

<scala>
trait Functor[F[_]]  { self =>
  /** Lift `f` into `F` and apply to `F[A]`. */
  def map[A, B](fa: F[A])(f: A => B): F[B]

  ...
}
</scala>

これが可能とする[演算子](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/FunctorSyntax.scala)はこうなっている:

<scala>
trait FunctorOps[F[_],A] extends Ops[F[A]] {
  implicit def F: Functor[F]
  ////
  import Leibniz.===

  final def map[B](f: A => B): F[B] = F.map(self)(f)
  
  ...
}
</scala>

つまり、これは関数 `A => B` を受け取り `F[B]` を返す `map` メソッドを宣言する。コレクションの `map` メソッドなら得意なものだ。

<scala>
scala> List(1, 2, 3) map {_ + 1}
res15: List[Int] = List(2, 3, 4)
</scala>

Scalaz は `Tuple` などにも `Functor` のインスタンスを定義している。

<scala>
scala> (1, 2, 3) map {_ + 1}
res28: (Int, Int, Int) = (1,2,4)
</scala>

### Functor としての関数

Scalaz は `Function1` に対する `Functor` のインスタンスも定義する。

<scala>
scala> ((x: Int) => x + 1) map {_ * 7}
res30: Int => Int = <function1>

scala> res30(3)
res31: Int = 28
</scala>

これは興味深い。つまり、`map` は関数を合成する方法を与えてくれるが、順番が `f compose g` とは逆順だ! 通りで Scalaz は `map` のエイリアスとして ` ∘` を提供するわけだ。`Function1` のもう1つのとらえ方は、定義域 (domain) から値域 (range) への無限の写像だと考えることができる。入出力に関しては飛ばして [Functors, Applicative Functors and Monoids](http://learnyouahaskell.com/functors-applicative-functors-and-monoids) へ行こう (本だと、「ファンクターからアプリカティブファンクターへ」)。

> How are functions functors?
> ...
>
> What does the type `fmap :: (a -> b) -> (r -> a) -> (r -> b)` for this instance tell us? Well, we see that it takes a function from `a` to `b` and a function from `r` to `a` and returns a function from `r` to `b`. Does this remind you of anything? Yes! Function composition! 

あ、LYAHFGG も僕がさっき言ったように関数合成をしているという結論になったみたいだ。ちょっと待てよ。

<haskell>
ghci> fmap (*3) (+100) 1
303
ghci> (*3) . (+100) $ 1  
303 
</haskell>

Haskell では `fmap` は `f compose g` を同じ順序で動作してるみたいだ。Scala でも同じ数字を使って確かめてみる:

<scala>
scala> (((_: Int) * 3) map {_ + 100}) (1)
res40: Int = 103
</scala>

何かがおかしい。`fmap` の宣言と Scalaz の `map` 演算子を比べてみよう:

<haskell>
fmap :: (a -> b) -> f a -> f b

</haskell>

そしてこれが Scalaz:

<scala>
final def map[B](f: A => B): F[B] = F.map(self)(f)

</scala>

順番が完全に違っている。ここでの `map` は `F[A]` に注入されたメソッドのため、投射される側のデータ構造が最初に来て、次に関数が来る。`List` で考えると分かりやすい:

<haskell>
ghci> fmap (*3) [1, 2, 3]
[3,6,9]
</haskell>

で

<scala>
scala> List(1, 2, 3) map {3*}
res41: List[Int] = List(3, 6, 9)
</scala>

ここでも順番が逆なことが分かる。

> [We can think of `fmap` as] a function that takes a function and returns a new function that's just like the old one, only it takes a functor as a parameter and returns a functor as the result. It takes an `a -> b` function and returns a function `f a -> f b`. This is called *lifting* a function.

<haskell>
ghci> :t fmap (*2)  
fmap (*2) :: (Num a, Functor f) => f a -> f a  
ghci> :t fmap (replicate 3)  
fmap (replicate 3) :: (Functor f) => f a -> f [a]  
</haskell>

この関数の持ち上げ (lifting) は是非やってみたい。`Functor` の型クラス内に色々便利な関数が定義されていて、その中の 1つに `lift` がある:

<scala>
scala> Functor[List].lift {(_: Int) * 2}
res45: List[Int] => List[Int] = <function1>

scala> res45(List(3))
res47: List[Int] = List(6)
</scala>

`Functor` は他にもデータ構造の中身を書きかえる `>|`、`as`、`fpair`、`strengthL`、`strengthR`、そして `void` などの演算子を可能とする:

<scala>
scala> List(1, 2, 3) >| "x"
res47: List[String] = List(x, x, x)

scala> List(1, 2, 3) as "x"
res48: List[String] = List(x, x, x)

scala> List(1, 2, 3).fpair
res49: List[(Int, Int)] = List((1,1), (2,2), (3,3))

scala> List(1, 2, 3).strengthL("x")
res50: List[(String, Int)] = List((x,1), (x,2), (x,3))

scala> List(1, 2, 3).strengthR("x")
res51: List[(Int, String)] = List((1,x), (2,x), (3,x))

scala> List(1, 2, 3).void
res52: List[Unit] = List((), (), ())
</scala>

### Applicative

LYAHFGG:

> So far, when we were mapping functions over functors, we usually mapped functions that take only one parameter. But what happens when we map a function like `*`, which takes two parameters, over a functor?

<scala>
scala> List(1, 2, 3, 4) map {(_: Int) * (_:Int)}
<console>:14: error: type mismatch;
 found   : (Int, Int) => Int
 required: Int => ?
              List(1, 2, 3, 4) map {(_: Int) * (_:Int)}
                                             ^
</scala>

おっと。これはカリー化する必要がある:

<scala>
scala> List(1, 2, 3, 4) map {(_: Int) * (_:Int)}.curried
res11: List[Int => Int] = List(<function1>, <function1>, <function1>, <function1>)

scala> res11 map {_(9)}
res12: List[Int] = List(9, 18, 27, 36)
</scala>

LYAHFGG:

> Meet the `Applicative` typeclass. It lies in the `Control.Applicative` module and it defines two methods, `pure` and `<*>`. 

Scalaz の `Applicative` のコントラクトも見てみよう:

<scala>
trait Applicative[F[_]] extends Apply[F] with Pointed[F] { self =>
  ...
}
</scala>

つまり、`Applicative` は別の 2つの型クラス `Pointed` と `Apply` を継承するけど、それ自身は新しいコントラクトメソッドを導入しない。まずは `Pointed` から見ていく。

### Pointed

LYAHFGG:

> `pure` should take a value of any type and return an applicative value with that value inside it. ... A better way of thinking about `pure` would be to say that it takes a value and puts it in some sort of default (or pure) context—a minimal context that still yields that value.

<scala>
trait Pointed[F[_]] extends Functor[F] { self =>
  def point[A](a: => A): F[A]

  /** alias for `point` */
  def pure[A](a: => A): F[A] = point(a)
}
</scala>

Scalaz は `pure` のかわりに `point` という名前が好きみたいだ。見たところ `A` の値を受け取り `F[A]` を返すコンストラクタみたいだ。これは演算子こそは導入しないけど、全てのデータ型に `point` メソッドとシンボルを使ったエイリアス `η` を導入する。

<scala>
scala> 1.point[List]
res14: List[Int] = List(1)

scala> 1.point[Option]
res15: Option[Int] = Some(1)

scala> 1.point[Option] map {_ + 2}
res16: Option[Int] = Some(3)

scala> 1.point[List] map {_ + 2}
res17: List[Int] = List(3)
</scala>

ちょっとうまく説明できないけど、コンストラクタが抽象化されているのは何か可能性を感じるものがある。

### Apply

LYAHFGG:

> You can think of `<*>` as a sort of a beefed-up `fmap`. Whereas `fmap` takes a function and a functor and applies the function inside the functor value, `<*>` takes a functor that has a function in it and another functor and extracts that function from the first functor and then maps it over the second one. 

<scala>
trait Apply[F[_]] extends Functor[F] { self =>
  def ap[A,B](fa: => F[A])(f: => F[A => B]): F[B]
}
</scala>

`ap` を使って `Apply` は `<*>`、`*>`、`<*` 演算子を可能とする。

<scala>
scala> 9.some <*> {(_: Int) + 3}.some
res20: Option[(Int, Int => Int)] = Some((9,<function1>))
</scala>

`Some(12)` という結果を期待していたんだけど。Scalaz 7.0.0-M3 はタプルを `Some` に入れて返すみたいだ。これに関して作者らに問い合わせた所、Haskell、Scalaz 6、Scalaz 7.0.0-M2 同様の振る舞いに戻るらしい。これを 7.0.0-M2 で実行してみよう:

<scala>
scala>  9.some <*> {(_: Int) + 3}.some
res20: Option[Int] = Some(12)
</scala>

これはうまくいった。

`*>` と `<*` は左辺項か右辺項のみ返すバリエーションだ。

<scala>
scala> 1.some <* 2.some
res35: Option[Int] = Some(1)

scala> none <* 2.some
res36: Option[Nothing] = None

scala> 1.some *> 2.some
res38: Option[Int] = Some(2)

scala> none *> 2.some
res39: Option[Int] = None
</scala>

### Apply としての Option

<s>それはありがたいんだけど、コンテナから関数を抽出して、別に抽出した値を適用する `<*>` はどうなったのかな?</s> 7.0.0-M2 の `<*>` を使えばいい。

<scala>
scala> 9.some <*> {(_: Int) + 3}.some
res57: Option[Int] = Some(12)

scala> 3.some <*> { 9.some <*> {(_: Int) + (_: Int)}.curried.some }
res58: Option[Int] = Some(12)
</scala>

### Applicative Style

もう 1つ見つけたのが、コンテナから値だけを抽出して 1つの関数を適用する新記法だ:

<scala>
scala> ^(3.some, 5.some) {_ + _}
res59: Option[Int] = Some(8)

scala> ^(3.some, none: Option[Int]) {_ + _}
res60: Option[Int] = None
</scala>

これは 1関数の場合はいちいちコンテナに入れなくてもいいから便利そうだ。これは推測だけど、これのお陰で Scalaz 7 は `Applicative` そのものでは何も演算子を導入していないんだと思う。実際どうなのかはともかく、`Pointed` も `<$>` もいらないみたいだ。

だけど、`^(f1, f2) {...}` スタイルに問題が無いわけではない。どうやら `Function1`、`Writer`、`Validation` のような 2つの型パラメータを取る Applicative を処理できないようだ。もう 1つ Applicative Builder という Scalaz 6 から使われていたらしい方法がある。 M3 で deprecated になったけど、`^(f1, f2) {...}` の問題のため、近い将来名誉挽回となるらしい。

こう使う:

<scala>
scala> (3.some |@| 5.some) {_ + _}
res18: Option[Int] = Some(8)
</scala>

今の所は `|@|` スタイルを使おう。

### Apply としての List

LYAHFGG:

> Lists (actually the list type constructor, `[]`) are applicative functors. What a surprise!

`<*>` と `|@|` が使えるかみてみよう:

<scala>
scala> List(1, 2, 3) <*> List((_: Int) * 0, (_: Int) + 100, (x: Int) => x * x)
res61: List[Int] = List(0, 0, 0, 101, 102, 103, 1, 4, 9)

scala> List(3, 4) <*> { List(1, 2) <*> List({(_: Int) + (_: Int)}.curried, {(_: Int) * (_: Int)}.curried) }
res62: List[Int] = List(4, 5, 5, 6, 3, 4, 6, 8)

scala> (List("ha", "heh", "hmm") |@| List("?", "!", ".")) {_ + _}
res63: List[String] = List(ha?, ha!, ha., heh?, heh!, heh., hmm?, hmm!, hmm.)
</scala>

### Zip List

LYAHFGG:

> However, `[(+3),(*2)] <*> [1,2]` could also work in such a way that the first function in the left list gets applied to the first value in the right one, the second function gets applied to the second value, and so on. That would result in a list with two values, namely `[4,4]`. You could look at it as `[1 + 3, 2 * 2]`.

Scalaz で `ZipList` に対応するものは見つけられなかった。

### Applicative の便利な関数

LYAHFGG:

> `Control.Applicative` defines a function that's called `liftA2`, which has a type of

<haskell>
liftA2 :: (Applicative f) => (a -> b -> c) -> f a -> f b -> f c .
</haskell>

`Apply[F].lift2` というものがある:

<scala>
scala> Apply[Option].lift2((_: Int) :: (_: List[Int]))
res66: (Option[Int], Option[List[Int]]) => Option[List[Int]] = <function2>

scala> res66(3.some, List(4).some)
res67: Option[List[Int]] = Some(List(3, 4))
</scala>

LYAHFGG:

> Let's try implementing a function that takes a list of applicatives and returns an applicative that has a list as its result value. We'll call it `sequenceA`.

<haskell>
sequenceA :: (Applicative f) => [f a] -> f [a]  
sequenceA [] = pure []  
sequenceA (x:xs) = (:) <$> x <*> sequenceA xs  
</haskell>

これを Scalaz でも実装できるか試してみよう!

<scala>
scala> def sequenceA[F[_]: Applicative, A](list: List[F[A]]): F[List[A]] = list match {
         case Nil     => (Nil: List[A]).point[F]
         case x :: xs => (x |@| sequenceA(xs)) {_ :: _} 
       }
sequenceA: [F[_], A](list: List[F[A]])(implicit evidence$1: scalaz.Applicative[F])F[List[A]]
</scala>

テストしてみよう:

<scala>
scala> sequenceA(List(1.some, 2.some))
res82: Option[List[Int]] = Some(List(1, 2))

scala> sequenceA(List(3.some, none, 1.some))
res85: Option[List[Int]] = None

scala> sequenceA(List(List(1, 2, 3), List(4, 5, 6)))
res86: List[List[Int]] = List(List(1, 4), List(1, 5), List(1, 6), List(2, 4), List(2, 5), List(2, 6), List(3, 4), List(3, 5), List(3, 6))
</scala>

正しい答えが得られた。興味深いのは結局 `Pointed` が必要になったことと、`sequenceA` が型クラスに関してジェネリックなことだ。

`Function1` の片側が `Int` に固定された例は、残念ながら黒魔術を召喚する必要がある。

<scala>
scala> type Function1Int[A] = ({type l[A]=Function1[Int, A]})#l[A]
defined type alias Function1Int

scala> sequenceA(List((_: Int) + 3, (_: Int) + 2, (_: Int) + 1): List[Function1Int[Int]])
res1: Int => List[Int] = <function1>

scala> res1(3)
res2: List[Int] = List(6, 5, 4)
</scala>

結構長くなったけど、ここまでたどり着けて良かったと思う。続きはまたあとで。
