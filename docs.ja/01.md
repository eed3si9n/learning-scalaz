これまでいくつのプログラミング言語が羊の衣を着た Lisp に喩えられただろうか? Java は馴染み親しんだ C++ のような文法に GC を持ち込んだ。それまで他にも GC を載せた言語はあったけども、現実的に C++ の代替となりうる言語に GC が載ったことは 1996年には画期的に思われた。やがて時は経ち、人々は自分でメモリ管理をしないことに慣れていった。JavaScript と Ruby の両言語もその第一級関数 (first-class function) やブロック構文を持つことから羊の衣を着た Lisp と呼ばれたことがある。S式の同図像性がマクロに適することから Lisp系の言語はまだ面白いと思う。

近年の言語はもう少し新しい関数型言語から概念を借りるようになってきた。型推論やパターンマッチングは ML にさかのぼることができると思う。時が経てば、人々はこれらの機能もまた当然と思うようになるだろう。Lisp が 1958年、ML が 1973年に発表されたことを考えると、良いアイディアが一般受けするには何十年かの時間がかかっている。その寒々しい何十年かの間、これらの言語は教義に異を唱える異端者、またはより酷く「真剣じゃない」と思われたことだろう。

ここで僕たち Scala のコミュニティーを振り返って、Scalaz を嘲笑っている人を見ると残念な気持ちになる。別にこれが次に大流行すると言っているわけじゃない。だいたい僕は Scalaz のことをまだ分かってもいない。ただ確信を持っているのはこれを使っている奴らは彼らの問題を真剣になって解いているということだ。または、残りの Scala コミュニティーがパターンマッチングを使っているのと同じぐらい学術的なことをやっている。Haskell が 1990年に発表されたことを考えると、この魔女裁判はしばらく続くだろうが、僕はオープンマインドでありたい。

### 型クラス初級講座

  [tt]: http://learnyouahaskell.com/types-and-typeclasses
  [moott]: http://learnyouahaskell.com/making-our-own-types-and-typeclasses
  [z7]: https://github.com/scalaz/scalaz/tree/scalaz-seven
  [start]: http://halcat0x15a.github.com/slide/start_scalaz/out/#4
  [z7docs]: http://halcat0x15a.github.com/scalaz/core/target/scala-2.9.2/api/

[すごいHaskellたのしく学ぼう](http://www.amazon.co.jp/dp/4274068854) の原書 [Learn You a Haskell for Great Good][tt] 曰く:

> A typeclass is a sort of interface that defines some behavior. If a type is a part of a typeclass, that means that it supports and implements the behavior the typeclass describes.

[Scalaz][z7] 曰く:

> It provides purely functional data structures to complement those from the Scala standard library. It defines a set of foundational type classes (e.g. `Functor`, `Monad`) and corresponding instances for a large number of data structures.

> Scala 標準ライブラリを補完する純粋関数型データ構造を提供する。(`Functor` や `Monad` など) 基本的な型クラスを定義し、また多くのデータ構造に対して対応するインスタンスを提供する。

Haskell を楽しく学びながら Scalaz も学べるかためしてみよう。

### sbt

以下が Scalaz 7 を試すための build.sbt だ。これは、ねこはる先生の講義で使われた[スライド][start]を少し更新したものだ:

<scala>
scalaVersion := "2.10.0-M7"

resolvers += "Typesafe Snapshots" at "http://repo.typesafe.com/typesafe/snapshots/"

libraryDependencies ++= Seq(
  "org.scalaz" % "scalaz-core" % "7.0.0-M3" cross CrossVersion.full
)

scalacOptions += "-feature"

initialCommands in console := "import scalaz._, Scalaz._"
</scala>

あとは sbt 0.12.0 から REPL を開くだけだ:

<code>
$ sbt console
...
[info] downloading http://repo1.maven.org/maven2/org/scalaz/scalaz-core_2.10.0-M7/7.0.0-M3/scalaz-core_2.10.0-M7-7.0.0-M3.jar ...
import scalaz._
import Scalaz._
Welcome to Scala version 2.10.0-M7 (Java HotSpot(TM) 64-Bit Server VM, Java 1.6.0_33).
Type in expressions to have them evaluated.
Type :help for more information.

scala> 
</code>

氏が Scalaz 7.0.0 M1 から生成した [API ドキュメント][z7docs]もある。

### Equal

LYAHFGG:

> `Eq` is used for types that support equality testing. The functions its members implement are `==` and `/=`.

Scalaz で `Eq` 型クラスと同じものは `Equal` と呼ばれている:

<code>
scala> 1 === 1
res0: Boolean = true

scala> 1 === "foo"
<console>:14: error: could not find implicit value for parameter F0: scalaz.Equal[Object]
              1 === "foo"
              ^

scala> 1 == "foo"
<console>:14: warning: comparing values of types Int and String using `==' will always yield false
              1 == "foo"
                ^
res2: Boolean = false

scala> 1.some =/= 2.some
res3: Boolean = true

scala> 1 assert_=== 2
java.lang.RuntimeException: 1 ≠ 2
</code>

標準の `==` のかわりに、`Equal` は `equal` メソッドを宣言することで `===`、`=/=`、と `assert_===` 演算を可能とする。主な違いは `Int` と `String` と比較すると `===` はコンパイルに失敗することだ。

注意: 初出ではここで `=/=` じゃなくて `/==` を使っていたけども、[Eiríkr Åsheim さん (@d6)](http://twitter.com/d6/status/243557748091011074) に以下の通り教えてもらった:
> you should encourage people to use =/= and not /== since the latter has bad precedence.
>
> /== は優先順位壊れてるから =/= を推奨すべき。

通常、`!=` のような比較演算子は `&&` や通常の文字列などに比べて高い優先順位を持つ。ところが、`/==` は `=` で終わるが `=` で始まらないため代入演算子のための特殊ルールが発動し、優先順位の最底辺に落ちてしまう:

<scala>
scala> 1 != 2 && false
res4: Boolean = false

scala> 1 /== 2 && false
<console>:14: error: value && is not a member of Int
              1 /== 2 && false
                      ^

scala> 1 =/= 2 && false
res6: Boolean = false
</scala>

### Order

LYAHFGG:

> `Ord` is for types that have an ordering. `Ord` covers all the standard comparing functions such as `>`, `<`, `>=` and `<=`.

Scalaz で `Ord` に対応する型クラスは `Order` だ:

<code>
scala> 1 > 2.0
res8: Boolean = false

scala> 1 gt 2.0
<console>:14: error: could not find implicit value for parameter F0: scalaz.Order[Any]
              1 gt 2.0
              ^

scala> 1.0 ?|? 2.0
res10: scalaz.Ordering = LT

scala> 1.0 max 2.0
res11: Double = 2.0
</code>

`Order` は `Ordering` (`LT`, `GT`, `EQ`) を返す `?|?` 演算を可能とする。また、`order` メソッドを宣言することで `lt`、`gt`、`lte`、`gte`、`min`、そして `max` 演算子を可能とする。`Equal` 同様 `Int` と `Double` の比較はコンパイルを失敗させる。

### Show

LYAHFGG:

> Members of `Show` can be presented as strings.

Scalaz で `Show` に対応する型クラスは `Show` だ:

<code>
scala> 3.show
res14: scalaz.Cord = 3

scala> 3.shows
res15: String = 3

scala> "hello".println
"hello"
</code>

`Cord` というのは潜在的に長い可能性のある文字列を保持できる純粋関数型データ構造のことらしい。

### Read

LYAHFGG:

> `Read` is sort of the opposite typeclass of `Show`. The `read` function takes a string and returns a type which is a member of `Read`.

これは対応する Scalaz での型クラスを見つけることが出来なかった。

### Enum

LYAHFGG:

> `Enum` members are sequentially ordered types — they can be enumerated. The main advantage of the `Enum` typeclass is that we can use its types in list ranges. They also have defined successors and predecesors, which you can get with the `succ` and `pred` functions.

Scalaz で `Enum` に対応する型クラスは `Enum` だ:

<code>
scala> 'a' to 'e'
res30: scala.collection.immutable.NumericRange.Inclusive[Char] = NumericRange(a, b, c, d, e)

scala> 'a' |-> 'e'
res31: List[Char] = List(a, b, c, d, e)

scala> 3 |=> 5
res32: scalaz.EphemeralStream[Int] = scalaz.EphemeralStreamFunctions$$anon$4@6a61c7b6

scala> 'B'.succ
res33: Char = C
</code>

`Order` 型クラスの上に `pred` と `succ` メソッドを宣言することで、標準の `to` のかわりに、`Enum` は `List` を返す `|->` を可能とする。他にも `-+-`、`---`、`from`、 `fromStep`、`pred`、`predx`、`succ`、`succx`、`|-->`、`|->`、`|==>`、`|=>` など多くの演算があるが、全て前後にステップ移動するか範囲を返すものだ。

### Bounded

> `Bounded` members have an upper and a lower bound.

Scalaz で `Bounded` に対応する型クラスは再び `Enum` みたいだ:

<code>
scala> implicitly[Enum[Char]].min
res43: Option[Char] = Some(?)

scala> implicitly[Enum[Char]].max
res44: Option[Char] = Some(￿)

scala> implicitly[Enum[Double]].max
res45: Option[Double] = Some(1.7976931348623157E308)

scala> implicitly[Enum[Int]].min
res46: Option[Int] = Some(-2147483648)

scala> implicitly[Enum[(Boolean, Int, Char)]].max
<console>:14: error: could not find implicit value for parameter e: scalaz.Enum[(Boolean, Int, Char)]
              implicitly[Enum[(Boolean, Int, Char)]].max
                        ^
</code>

`Enum` 型クラスのインスタンスは最大値に対して `Option[T]` を返す。

### Num

> `Num` is a numeric typeclass. Its members have the property of being able to act like numbers.

Scalaz で `Num`、`Floating`、`Integral` に対応する型クラスは見つけることが出来なかった。

### 型クラス中級講座

Haskell の文法に関しては飛ばして第8章の [Making Our Own Types and Typeclasses][moott] まで行こう (本を持っている人は第7章)。

### 信号の型クラス

<haskell>
data TrafficLight = Red | Yellow | Green
</haskell>

これを Scala で書くと:

<scala>
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait TrafficLight
case object Red extends TrafficLight
case object Yellow extends TrafficLight
case object Green extends TrafficLight
</scala>

これに `Equal` のインスタンスを定義する。

<scala>
scala> implicit val TrafficLightEqual: Equal[TrafficLight] = Equal.equal(_ == _)
TrafficLightEqual: scalaz.Equal[TrafficLight] = scalaz.Equal$$anon$7@2457733b
</scala>

使えるかな?

<scala>
scala> Red === Yellow
<console>:18: error: could not find implicit value for parameter F0: scalaz.Equal[Product with Serializable with TrafficLight]
              Red === Yellow
</scala>

`Equal` が不変 (invariant) なサブタイプ `Equal[F]` を持つせいで、`Equal[TrafficLight]` が検知されないみたいだ。`TrafficLight` を case class にして `Red` と `Yellow` が同じ型を持つようになるけど、厳密なパターンマッチングができなくなる。#ダメじゃん

<scala>
scala> :paste
// Entering paste mode (ctrl-D to finish)

case class TrafficLight(name: String)
val red = TrafficLight("red")
val yellow = TrafficLight("yellow")
val green = TrafficLight("green")
implicit val TrafficLightEqual: Equal[TrafficLight] = Equal.equal(_ == _)
red === yellow

// Exiting paste mode, now interpreting.

defined class TrafficLight
red: TrafficLight = TrafficLight(red)
yellow: TrafficLight = TrafficLight(yellow)
green: TrafficLight = TrafficLight(green)
TrafficLightEqual: scalaz.Equal[TrafficLight] = scalaz.Equal$$anon$7@42988fee
res3: Boolean = false
</scala>

### Yes と No の型クラス

Scalaz 流に truthy 値の型クラスを作れるか試してみよう。ただし、命名規則は我流でいく。Scalaz は `Show`、`show`、`show` というように 3つや 4つの異なるものに型クラスの名前を使っているため分かりづらい所があると思う。

`CanBuildFrom` に倣って型クラスは `Can` で始めて、sjson/sbinary に倣って型クラスのメソッドは動詞 + `s` と命名するのが僕の好みだ。`yesno` というのは意味不明なので、`truthy`  と呼ぶ。ゴールは `1.truthy` が `true` を返すことだ。この欠点は型クラスのインスタンスを `CanTruthy[Int].truthys(1)` のように関数として呼ぶと名前に `s` が付くことだ。

<scala>
scala> :paste
// Entering paste mode (ctrl-D to finish)

trait CanTruthy[A] { self =>
  /** @return true, if `a` is truthy. */
  def truthys(a: A): Boolean
}
object CanTruthy {
  def apply[A](implicit ev: CanTruthy[A]): CanTruthy[A] = ev
  def truthys[A](f: A => Boolean): CanTruthy[A] = new CanTruthy[A] {
    def truthys(a: A): Boolean = f(a)
  }
}
trait CanTruthyOps[A] {
  def self: A
  implicit def F: CanTruthy[A]
  final def truthy: Boolean = F.truthys(self)
}
object ToCanIsTruthyOps {
  implicit def toCanIsTruthyOps[A](v: A)(implicit ev: CanTruthy[A]) =
    new CanTruthyOps[A] {
      def self = v
      implicit def F: CanTruthy[A] = ev
    }
}

// Exiting paste mode, now interpreting.

defined trait CanTruthy
defined module CanTruthy
defined trait CanTruthyOps
defined module ToCanIsTruthyOps

scala> import ToCanIsTruthyOps._
import ToCanIsTruthyOps._
</scala>

`Int` への型クラスのインスタンスを定義する:

<scala>
scala> implicit val intCanTruthy: CanTruthy[Int] = CanTruthy.truthys({
         case 0 => false
         case _ => true
       })
intCanTruthy: CanTruthy[Int] = CanTruthy$$anon$1@71780051

scala> 10.truthy
res6: Boolean = true
</scala>

次が `List[A]`:

<scala>
scala> implicit def listCanTruthy[A]: CanTruthy[List[A]] = CanTruthy.truthys({
         case Nil => false
         case _   => true  
       })
listCanTruthy: [A]=> CanTruthy[List[A]]

scala> List("foo").truthy
res7: Boolean = true

scala> Nil.truthy
<console>:23: error: could not find implicit value for parameter ev: CanTruthy[scala.collection.immutable.Nil.type]
              Nil.truthy
</scala>

またしても不変な型パラメータのせいで `Nil` を特殊扱いしなくてはいけない。

<scala>
scala> implicit val nilCanTruthy: CanTruthy[scala.collection.immutable.Nil.type] = CanTruthy.truthys(_ => false)
nilCanTruthy: CanTruthy[collection.immutable.Nil.type] = CanTruthy$$anon$1@1e5f0fd7

scala> Nil.truthy
res8: Boolean = false
</scala>

`Boolean` は `identity` を使える:

<scala>
scala> implicit val booleanCanTruthy: CanTruthy[Boolean] = CanTruthy.truthys(identity)
booleanCanTruthy: CanTruthy[Boolean] = CanTruthy$$anon$1@334b4cb

scala> false.truthy
res11: Boolean = false
</scala>

LYAHFGG 同様に `CanTruthy` 型クラスを使って `truthyIf` を定義しよう:

> Now let's make a function that mimics the `if` statement, but that works with `YesNo` values.

名前渡しを使って渡された引数の評価を遅延する:

<scala>
scala> :paste
// Entering paste mode (ctrl-D to finish)

def truthyIf[A: CanTruthy, B, C](cond: A)(ifyes: => B)(ifno: => C) =
  if (cond.truthy) ifyes
  else ifno

// Exiting paste mode, now interpreting.

truthyIf: [A, B, C](cond: A)(ifyes: => B)(ifno: => C)(implicit evidence$1: CanTruthy[A])Any
</scala>

使用例はこうなる:

<scala>
scala> truthyIf (Nil) {"YEAH!"} {"NO!"}
res12: Any = NO!

scala> truthyIf (2 :: 3 :: 4 :: Nil) {"YEAH!"} {"NO!"}
res13: Any = YEAH!

scala> truthyIf (true) {"YEAH!"} {"NO!"}
res14: Any = YEAH!
</scala>

続きはまたあとで。
