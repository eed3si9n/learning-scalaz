---
out: Monoid.html
---

  [tags]: $docBaseUrl$#scalaz.Tags\$

### Monoid について

LYAHFGG:

> どうやら、`*` に `1` という組み合わせと、`++` に `[]` という組み合わせは、共通の性質を持っているようですね。
>
> - 関数は引数を2つ取る。
> - 2つの引数および返り値の型はすべて等しい。
> - 2引数関数を施して相手を変えないような特殊な値が存在する。

これを Scala で確かめてみる:

```scala
scala> 4 * 1
res16: Int = 4

scala> 1 * 9
res17: Int = 9

scala> List(1, 2, 3) ++ Nil
res18: List[Int] = List(1, 2, 3)

scala> Nil ++ List(0.5, 2.5)
res19: List[Double] = List(0.5, 2.5)
```

あってるみたいだ。

LYAHFGG:

> 例えば、`(3 * 4) * 5` も `3 * (4 * 5)` も、答は `60` です。`++` についてもこの性質は成り立ちます。
> ...
> この性質を**結合的** (associativity) と呼びます。演算 `*` と `++` は結合的であると言います。結合的でない演算の例は `-` です。

これも確かめよう:

```scala
scala> (3 * 2) * (8 * 5) assert_=== 3 * (2 * (8 * 5))

scala> List("la") ++ (List("di") ++ List("da")) assert_=== (List("la") ++ List("di")) ++ List("da")
```

エラーがないから等価ということだ。これを monoid と言うらしい。

### Monoid

LYAHFGG:

> **モノイド**は、結合的な二項演算子（2引数関数）と、その演算に関する単位元からなる構造です。

[Scalaz の `Monoid` の型クラスのコントラクト](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Monoid.scala)を見てみよう:

```scala
trait Monoid[A] extends Semigroup[A] { self =>
  ////
  /** The identity element for `append`. */
  def zero: A
  
  ...
}
```

### Semigroup

`Monoid` は `Semigroup` を継承するみたいなので[その型クラスも見てみる](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Semigroup.scala)。

```scala
trait Semigroup[A]  { self =>
  def append(a1: A, a2: => A): A
  ...
}
```

これが[演算子](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/SemigroupSyntax.scala)だ:

```scala
trait SemigroupOps[A] extends Ops[A] {
  final def |+|(other: => A): A = A.append(self, other)
  final def mappend(other: => A): A = A.append(self, other)
  final def ⊹(other: => A): A = A.append(self, other)
}
```

`mappend` 演算子とシンボルを使ったエイリアス `|+|` と `⊹` を導入する。

LYAHFGG:

> 次は `mappend` です。これは、お察しのとおり、モノイド固有の二項演算です。`mappend` は同じ型の引数を2つ取り、その型の別の値を返します。

すごい Haskell は名前が `mappend` だからといって、`*` の場合のように必ずしも何かを追加 (append) してるわけじゃないと注意している。これを使ってみよう:

```scala
scala> List(1, 2, 3) mappend List(4, 5, 6)
res23: List[Int] = List(1, 2, 3, 4, 5, 6)

scala> "one" mappend "two"
res25: String = onetwo
```

`|+|` を使うのが Scalaz では一般的みたいだ: 

```scala
scala> List(1, 2, 3) |+| List(4, 5, 6)
res26: List[Int] = List(1, 2, 3, 4, 5, 6)

scala> "one" |+| "two"
res27: String = onetwo
```

より簡潔にみえる。

### Monoid に戻る

```scala
trait Monoid[A] extends Semigroup[A] { self =>
  ////
  /** The identity element for `append`. */
  def zero: A
  
  ...
}
```

LYAHFGG:

> `mempty` は、そのモノイドの単位元を表わします。

これは Scalaz では `zero` と呼ばれている。

```scala
scala> Monoid[List[Int]].zero
res15: List[Int] = List()

scala> Monoid[String].zero
res16: String = ""
```

### Tags.Multiplication

LYAHFGG:

> さて、数をモノイドにする2つの方法は、どちらも素晴らしく優劣つけがたいように思えます。一体どちらを選べまよいのでしょう？実は、1つだけ選ぶ必要はないのです。

これが Scalaz 7.1 での Tagged type の出番だ。最初から定義済みのタグは [Tags][tags] にある。8つのタグが Monoid 用で、1つ `Zip` という名前のタグが `Applicative` 用にある。(もしかしてこれが昨日見つけられなかった Zip List?)

```scala
scala> Tags.Multiplication(10) |+| Monoid[Int @@ Tags.Multiplication].zero
res21: scalaz.@@[Int,scalaz.Tags.Multiplication] = 10
```

よし! `|+|` を使って数字を掛けることができた。加算には普通の `Int` を使う。

```scala
scala> 10 |+| Monoid[Int].zero
res22: Int = 10
```

### Tags.Disjunction and Tags.Conjunction

LYAHFGG:

> モノイドにする方法が2通りあって、どちらも捨てがたいような型は、`Num a` 以外にもあります。`Bool` です。1つ目の方法は `||` をモノイド演算とし、`False` を単位元とする方法です。
> ...
> `Bool` を `Monoid` のインスタンスにするもう1つの方法は、`Any` のいわば真逆です。`&&` をモノイド演算とし、`True` を単位元とする方法です。

Scalaz 7 でこれらはそれぞれ `Boolean @@ Tags.Disjunction`、`Boolean @@ Tags.Conjunction` と呼ばれている。

```scala
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
```

### Monoid としての Ordering

LYAHFGG:

> `Ordering` の場合、モノイドを見抜くのはちょっと難しいです。しかし `Ordering` の `Monoid` インスタンスは、分かってみれば今までのモノイドと同じくごく自然な定義で、しかも便利なんです。

ちょっと変わっているが、確かめてみよう。

```scala
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
```

LYAHFGG:

> では、このモノイドはどういうときに便利なのでしょう？例えば、2つの文字列を引数に取り、その長さを比較して　`Ordering` を返す関数を書きたいとしましょう。だたし、2つの文字列の長さが等しいときは、直ちに `EQ` を返すのではなくて、2つの文字列の辞書順比較することとします。

`Ordering.EQ` 以外の場合は左辺の比較が保存されるため、これを使って2つのレベルの比較を合成することができる。Scalaz を使って `lengthCompare` を実装してみよう:

```scala
scala> def lengthCompare(lhs: String, rhs: String): Ordering =
         (lhs.length ?|? rhs.length) |+| (lhs ?|? rhs)
lengthCompare: (lhs: String, rhs: String)scalaz.Ordering

scala> lengthCompare("zen", "ants")
res46: scalaz.Ordering = LT

scala> lengthCompare("zen", "ant")
res47: scalaz.Ordering = GT
```

合ってる。"zen" は "ants" より短いため `LT` が返ってきた。

他にも `Monoid` があるけど、今日はこれでおしまいにしよう。また後でここから続ける。
