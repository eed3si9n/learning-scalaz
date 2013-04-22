  [day2]: http://eed3si9n.com/ja/learning-scalaz-day2
  [tt]: http://learnyouahaskell.com/types-and-typeclasses

[昨日][day2]は `map` 演算子を加える `Functor` から始めて、`Pointed[F].point` や Applicative な `^(f1, f2) {_ :: _}` 構文を使った多態的な関数 `sequenceA` にたどり着いた。

### 型を司るもの、カインド

[Making Our Own Types and Typeclasses][tt] の節で昨日のうちにカバーしておくべきだったけどしなかったのはカインドと型の話だ。Scalaz の理解には関係無いだろうと思ってたけど、関係あるので、座って聞いて欲しい。

[すごいHaskellたのしく学ぼう](http://www.amazon.co.jp/dp/4274068854) の原書 [Learn You a Haskell For Great Good][tt] 曰く:

> Types are little labels that values carry so that we can reason about the values. But types have their own little labels, called kinds. A kind is more or less the type of a type. 
> ...
> What are kinds and what are they good for? Well, let's examine the kind of a type by using the :k command in GHCI.

Scala REPL に `:k` コマンドが見つからなかったので Scala 2.10.0-M7 向けにひとつ書いてみた。<s>Haskell バージョンと違ってプロパーな型しか受け入れないけど、無いよりはいいでしょう!</s> 型コンストラクタに対してはコンパニオンの型を渡す。(アイディアは paulp さんから頂いた)

<scala>
def kind[A: scala.reflect.TypeTag]: String = {
  import scala.reflect.runtime.universe._
  def typeKind(sig: Type): String = sig match {
    case PolyType(params, resultType) =>
      (params map { p =>
        typeKind(p.typeSignature) match {
          case "*" => "*"
          case s   => "(" + s + ")"
        }
      }).mkString(" -> ") + " -> *"
    case _ => "*"
  }
  def typeSig(tpe: Type): Type = tpe match {
    case SingleType(pre, sym) => sym.companionSymbol.typeSignature
    case ExistentialType(q, TypeRef(pre, sym, args)) => sym.typeSignature
    case TypeRef(pre, sym, args) => sym.typeSignature
  }
  val sig = typeSig(typeOf[A])
  val s = typeKind(sig)
  sig.typeSymbol.name + "のカインドは " + s + "。" + (s match {
    case "*" =>
      "プロパーな型だ。"
    case x if !(x contains "(") =>
      "型コンストラクタ: 1階カインド型だ。"
    case x =>
      "型コンストラクタを受け取る型コンストラクタ: 高カインド型だ。"
  })
}
</scala>

1日目の `build.sbt` を使って `sbt console` を起動して上の関数をコピペする。使ってみよう:

<code>
scala> kind[Int]
res0: String = Intのカインドは *。プロパーな型だ。

scala> kind[Option.type]
res1: String = Optionのカインドは * -> *。型コンストラクタ: 1階カインド型だ。

scala> kind[Either.type]
res2: String = Eitherのカインドは * -> * -> *。型コンストラクタ: 1階カインド型だ

scala> kind[Equal.type]
res3: String = Equalのカインドは * -> *。型コンストラクタ: 1階カインド型だ。

scala> kind[Functor.type]
res4: String = Functorのカインドは (* -> *) -> *。型コンストラクタを受け取る型コンストラクタ: 高カインド型だ。
</code>

上から順番に。`Int` と他の全ての値を作ることのできる型はプロパーな型と呼ばれ `*` というシンボルで表記される (「型」と読む)。これは値レベルだと `1` に相当する。

1階値、つまり `(_: Int) + 3` のような値コンストラクタ、は普通関数と呼ばれる。同様に、1階カインド型はプロパーな型になるために他の型を受け取る型のことだ。これは普通型コンストラクタと呼ばれる。`Option`、`Either`、`Equal` などは全て1階カインドだ。これらが他の型を受け取ることを表記するのにカリー化した表記法を用いて `* -> *` や `* -> * -> *` などと書く。このとき `Option[Int]` は `*` で、`Option` が `* -> *` であることに注意。

`(f: Int => Int, list: List[Int]) => list map {f}` のような高階値、つまり関数を受け取る関数、は普通高階関数と呼ばれる。同様に、高カインド型は型コンストラクタを受け取る型コンストラクタだ。これは多分高カインド型コンストラクタと呼ばれるべきだが、その名前は使われていない。これらは `(* -> *) -> *` と表記される。

Scalaz 7 の場合、`Equal` その他は `* -> *` のカインドを持ち、`Functor` とその派生型は `(* -> *) -> *` カインドを持つ。注入された演算子を使っているぶんにはこれらの心配をする必要はない:

<scala>
scala> List(1, 2, 3).shows
res11: String = [1,2,3]
</scala>

だけど `Show[A].shows` を使いたければ、これが `Show[List[Int]]` であって `Show[List]` ではないことを理解している必要がある。同様に、関数を持ち上げ (lift) たければ `Functor[F]` (`F` は `Functor` の `F`) だと知っている必要がある:

<scala>
scala> Functor[List[Int]].lift((_: Int) + 2)
<console>:14: error: List[Int] takes no type parameters, expected: one
              Functor[List[Int]].lift((_: Int) + 2)
                      ^

scala> Functor[List].lift((_: Int) + 2)
res13: List[Int] => List[Int] = <function1>
</scala>

[チートシート](http://eed3si9n.com/scalaz-cheat-sheet) を始めたとき、Scalaz 7 のソースコードに合わせて `Equal[F]` と書いた。すると [Adam Rosien さん (@arosien)](http://twitter.com/arosien/status/241990437269815296) に `Equal[A]` と表記すべきと指摘された。これで理由がよく分かった!

### Tagged type

「すごいHaskellたのしく学ぼう」の本を持ってるひとは新しい章に進める。モノイドだ。ウェブサイトを読んでるひとは [Functors, Applicative Functors and Monoids](http://learnyouahaskell.com/functors-applicative-functors-and-monoids) の続きだ。

LYAHFGG:

> The *newtype* keyword in Haskell is made exactly for these cases when we want to just take one type and wrap it in something to present it as another type.

これは Haskell の言語レベルでの機能なので、Scala に移植するのは無理なんじゃないかと思うと思う。
ところが、約1年前 (2011年9月) [Miles Sabin さん (@milessabin)](https://twitter.com/milessabin) が [gist](https://gist.github.com/89c9b47a91017973a35f) を書き、それを `Tagged` と名付け、[Jason Zaugg さん (@retronym)](https://twitter.com/retronym) が `@@` という型エイリアスを加えた。

<scala>
type Tagged[U] = { type Tag = U }
type @@[T, U] = T with Tagged[U]
</scala>

これについて読んでみたいひとは [Eric Torreborre さん (@etorreborre)](http://twitter.com/etorreborre) が [Practical uses for Unboxed Tagged Types](http://etorreborre.blogspot.com/2011/11/practical-uses-for-unboxed-tagged-types.html)、それから [Tim Perrett さん (@timperrett)](http://es.twitter.com/timperrett) が [Unboxed new types within Scalaz7](http://timperrett.com/2012/06/15/unboxed-new-types-within-scalaz7/) を書いている。

例えば、体積をキログラムで表現したいとする。kg は国際的な標準単位だからだ。普通は `Double` を渡して終わる話だけど、それだと他の `Double` の値と区別が付かない。case class は使えるだろうか?

<scala>
case class KiloGram(value: Double)
</scala>

型安全性は加わったけど、使うたびに `x.value` というふうに値を取り出さなきゃいけないのが不便だ。Tagged type 登場。

<scala>
scala> sealed trait KiloGram
defined trait KiloGram

scala> def KiloGram[A](a: A): A @@ KiloGram = Tag[A, KiloGram](a)
KiloGram: [A](a: A)scalaz.@@[A,KiloGram]

scala> val mass = KiloGram(20.0)
mass: scalaz.@@[Double,KiloGram] = 20.0

scala> 2 * mass
res2: Double = 40.0
</scala>

補足しておくと、`A @@ KiloGram` は `scalaz.@@[A, KiloGram]` の中置記法だ。これで相対論的エネルギーを計算する関数を定義できる。

<scala>
scala> sealed trait JoulePerKiloGram
defined trait JoulePerKiloGram

scala> def JoulePerKiloGram[A](a: A): A @@ JoulePerKiloGram = Tag[A, JoulePerKiloGram](a)
JoulePerKiloGram: [A](a: A)scalaz.@@[A,JoulePerKiloGram]

scala> def energyR(m: Double @@ KiloGram): Double @@ JoulePerKiloGram =
     |   JoulePerKiloGram(299792458.0 * 299792458.0 * m)
energyR: (m: scalaz.@@[Double,KiloGram])scalaz.@@[Double,JoulePerKiloGram]

scala> energyR(mass)
res4: scalaz.@@[Double,JoulePerKiloGram] = 1.79751035747363533E18

scala> energyR(10.0)
<console>:18: error: type mismatch;
 found   : Double(10.0)
 required: scalaz.@@[Double,KiloGram]
    (which expands to)  Double with AnyRef{type Tag = KiloGram}
              energyR(10.0)
                      ^
</scala>

見ての通り、素の `Double` を `energyR` に渡すとコンパイル時に失敗する。これは `newtype` そっくりだけど、`Int @@ KiloGram` など定義できるからより強力だと言える。

### Monoid について

LYAHFGG:

> It seems that both `*` together with `1` and `++` along with `[]` share some common properties:
> - The function takes two parameters.
> - The parameters and the returned value have the same type.
> - There exists such a value that doesn't change other values when used with the binary function.

これを Scala で確かめてみる:

<scala>
scala> 4 * 1
res16: Int = 4

scala> 1 * 9
res17: Int = 9

scala> List(1, 2, 3) ++ Nil
res18: List[Int] = List(1, 2, 3)

scala> Nil ++ List(0.5, 2.5)
res19: List[Double] = List(0.5, 2.5)
</scala>

あってるみたいだ。

LYAHFGG:

> It doesn't matter if we do `(3 * 4) * 5` or `3 * (4 * 5)`. Either way, the result is `60`. The same goes for `++`.
> ...
> We call this property *associativity*. `*` is associative, and so is `++`, but `-`, for example, is not.

これも確かめよう:

<scala>
scala> (3 * 2) * (8 * 5) assert_=== 3 * (2 * (8 * 5))

scala> List("la") ++ (List("di") ++ List("da")) assert_=== (List("la") ++ List("di")) ++ List("da")
</scala>

エラーがないから等価ということだ。これを monoid と言うらしい。

### Monoid

LYAHFGG:

> A *monoid* is when you have an associative binary function and a value which acts as an identity with respect to that function. 

[Scalaz の `Monoid` の型クラスのコントラクト](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Monoid.scala)を見てみよう:

<scala>
trait Monoid[A] extends Semigroup[A] { self =>
  ////
  /** The identity element for `append`. */
  def zero: A
  
  ...
}
</scala>

### Semigroup

`Monoid` は `Semigroup` を継承するみたいなので[その型クラスも見てみる](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Semigroup.scala)。

<scala>
trait Semigroup[A]  { self =>
  def append(a1: A, a2: => A): A
  ...
}
</scala>

これが[演算子](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/SemigroupSyntax.scala)だ:

<scala>
trait SemigroupOps[A] extends Ops[A] {
  final def |+|(other: => A): A = A.append(self, other)
  final def mappend(other: => A): A = A.append(self, other)
  final def ⊹(other: => A): A = A.append(self, other)
}
</scala>

`mappend` 演算子とシンボルを使ったエイリアス `|+|` と `⊹` を導入する。

LYAHFGG:

> We have `mappend`, which, as you've probably guessed, is the binary function. It takes two values of the same type and returns a value of that type as well.

LYAHFGG は名前が `mappend` だからといって、`*` の場合のように必ずしも何かを追加 (append) してるわけじゃないと注意している。これを使ってみよう:

<scala>
scala> List(1, 2, 3) mappend List(4, 5, 6)
res23: List[Int] = List(1, 2, 3, 4, 5, 6)

scala> "one" mappend "two"
res25: String = onetwo
</scala>

`|+|` を使うのが Scalaz では一般的みたいだ: 

<scala>
scala> List(1, 2, 3) |+| List(4, 5, 6)
res26: List[Int] = List(1, 2, 3, 4, 5, 6)

scala> "one" |+| "two"
res27: String = onetwo
</scala>

より簡潔にみえる。

### Monoid に戻る

<scala>
trait Monoid[A] extends Semigroup[A] { self =>
  ////
  /** The identity element for `append`. */
  def zero: A
  
  ...
}
</scala>

LYAHFGG:

> `mempty` represents the identity value for a particular monoid.

これは Scalaz では `zero` と呼ばれている。

<scala>
scala> Monoid[List[Int]].zero
res15: List[Int] = List()

scala> Monoid[String].zero
res16: String = ""
</scala>

### Tags.Multiplication

LYAHFGG:

> So now that there are two equally valid ways for numbers (addition and multiplication) to be monoids, which way do choose? Well, we don't have to.

これが Scalaz 7 での Tagged type の出番だ。最初から定義済みのタグは [Tags](http://halcat0x15a.github.com/scalaz/core/target/scala-2.9.2/api/#scalaz.Tags$) にある。8つのタグが Monoid 用で、1つ `Zip` という名前のタグが `Applicative` 用にある。(もしかしてこれが昨日見つけられなかった Zip List?)

<scala>
scala> Tags.Multiplication(10) |+| Monoid[Int @@ Tags.Multiplication].zero
res21: scalaz.@@[Int,scalaz.Tags.Multiplication] = 10
</scala>

よし! `|+|` を使って数字を掛けることができた。加算には普通の `Int` を使う。

<scala>
scala> 10 |+| Monoid[Int].zero
res22: Int = 10
</scala>

### Tags.Disjunction and Tags.Conjunction

LYAHFGG:

> Another type which can act like a monoid in two distinct but equally valid ways is `Bool`. The first way is to have the or function `||` act as the binary function along with `False` as the identity value. 
> ...
> The other way for `Bool` to be an instance of `Monoid` is to kind of do the opposite: have `&&` be the binary function and then make `True` the identity value. 

Scalaz 7 でこれらはそれぞれ `Boolean @@ Tags.Disjunction`、`Boolean @@ Tags.Disjunction` と呼ばれている。

<scala>
scala> Tags.Disjunction(true) |+| Tags.Disjunction(false)
res28: scalaz.@@[Boolean,scalaz.Tags.Disjunction] = true

scala> Monoid[Boolean @@ Tags.Disjunction].zero |+| Tags.Disjunction(true)
res29: scalaz.@@[Boolean,scalaz.Tags.Disjunction] = true

scala> Monoid[Boolean @@ Tags.Disjunction].zero |+| Monoid[Boolean @@ Tags.Disjunction].zero
res30: scalaz.@@[Boolean,scalaz.Tags.Disjunction] = false

scala> Monoid[Boolean @@ Tags.Conjunction].zero |+| Tags.Conjunction(true)
res31: scalaz.@@[Boolean,scalaz.Tags.Conjunction] = true

scala> Monoid[Boolean @@ Tags.Conjunction].zero |+| Tags.Conjunction(false)
res32: scalaz.@@[Boolean,scalaz.Tags.Conjunction] = false
</scala>

### Monoid としての Ordering

LYAHFGG:

> With `Ordering`, we have to look a bit harder to recognize a monoid, but it turns out that its `Monoid` instance is just as intuitive as the ones we've met so far and also quite useful.

ちょっと変わっているが、確かめてみよう。

<scala>
scala> Ordering.LT |+| Ordering.GT
<console>:14: error: value |+| is not a member of object scalaz.Ordering.LT
              Ordering.LT |+| Ordering.GT
                          ^

scala> (Ordering.LT: Ordering) |+| (Ordering.GT: Ordering)
res42: scalaz.Ordering = LT

scala> (Ordering.GT: Ordering) |+| (Ordering.LT: Ordering)
res43: scalaz.Ordering = GT

scala> Monoid[Ordering].zero |+| (Ordering.LT: Ordering)
res44: scalaz.Ordering = LT

scala> Monoid[Ordering].zero |+| (Ordering.GT: Ordering)
res45: scalaz.Ordering = GT
</scala>

LYAHFGG:

> OK, so how is this monoid useful? Let's say you were writing a function that takes two strings, compares their lengths, and returns an `Ordering`. But if the strings are of the same length, then instead of returning `EQ` right away, we want to compare them alphabetically. 


`Ordering.EQ` 以外の場合は左辺の比較が保存されるため、これを使って2つのレベルの比較を合成することができる。Scalaz を使って `lengthCompare` を実装してみよう:

<scala>
scala> def lengthCompare(lhs: String, rhs: String): Ordering =
         (lhs.length ?|? rhs.length) |+| (lhs ?|? rhs)
lengthCompare: (lhs: String, rhs: String)scalaz.Ordering

scala> lengthCompare("zen", "ants")
res46: scalaz.Ordering = LT

scala> lengthCompare("zen", "ant")
res47: scalaz.Ordering = GT
</scala>

合ってる。"zen" は "ants" より短いため `LT` が返ってきた。

他にも `Monoid` があるけど、今日はこれでおしまいにしよう。また後でここから続ける。
