  [day12]: http://eed3si9n.com/ja/learning-scalaz-day12

<div class="floatingimage">
<img src="http://eed3si9n.com/images/hambeifutenbw.jpg">
<div class="credit">e.e d3si9n</div>
</div>

[昨日][day12]は、Jeremy Gibbons さんによる論文を 2本飛ばし読みしておりがみプログラミングと applicative な走査をみた。今日は何かを読む代わりに、Scalaz の使い方に焦点を当ててみる。

### implicit のまとめ

Scalaz は implicit を使い倒している。ライブラリを使う側としても、拡張する側としても何がどこから来てるかという一般的な勘を作っていくのは大切だ。Scala の import と implicit を手早く復習しよう!

Scala では import は 2つの目的で使われる:
1. 値や型の名前をスコープに取り込むため。
2. implicit をスコープに取り込むため。

implicit には僕が考えられる限り 4つの使い方がある:
1. 型クラスインスタンスを提供するため。
2. メソッドや演算子を注入するため。(静的モンキーパッチ)
3. 型制約を宣言するため。
4. 型の情報をコンパイラから取得するため。

implicit は以下の優先順位で選択される:
1. プレフィックス無しでアクセスできる暗黙の値や変換子。ローカル宣言、import、外のスコープ、継承、および現在のパッケージオブジェクトから取り込まれる。同名の暗黙の値があった場合は内側のスコープのものが外側のものを shadow する。
2. 暗黙のスコープ。型、その部分、および親型のコンパニオンオブジェクトおよびパッケージオブジェクト内で宣言された暗黙の値や変換子。

### import scalaz._

まずは `import scalaz._` で何が import されるのかみてみよう。

まずは、名前だ。`Equal[A]` や `Functor[F[_]]` のような型クラスは trait として実装されていて、`scalaz` パッケージ内で定義されている。だから、`scalaz.Equal[A]` と書くかわりに `Equal[A]` と書ける。

次も、名前だけど、これは型エイリアス。`scalaz` のパッケージオブジェクトは `@@[T, Tag]` や `Reader[E, A]` (`ReaderT` モナド変換子を特殊化したものという扱い) のような主な型エイリアスを宣言する。これも `scalaz.Reader[E, A]` というふうにアクセスすることができる。

最後に、`Id[A]` の `Traverse[F[_]]` や `Monad[F[_]]` その他への型クラスインスタンスとして `idInstance` が定義されているけど、気にしなくてもいい。パッケージオブジェクトに入っているというだけで暗黙のスコープに入るので、これは import しても結果は変わらない。確かめてみよう:

<scala>
scala> scalaz.Monad[scalaz.Id.Id]
res1: scalaz.Monad[scalaz.Id.Id] = scalaz.IdInstances$$anon$1@fc98c94
</scala>

import は必要なしということで、うまくいった。つまり、`import scalaz._` の効果はあくまで便宜のためであって、省略可能だ。

### import Scalaz._

だとすると、`import Scalaz._` は一体何をやっているんだろう? 以下が [`Scalaz` object](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Scalaz.scala) の定義だ:

<scala>
package scalaz

object Scalaz
  extends StateFunctions        // Functions related to the state monad
  with syntax.ToTypeClassOps    // syntax associated with type classes
  with syntax.ToDataOps         // syntax associated with Scalaz data structures
  with std.AllInstances         // Type class instances for the standard library types
  with std.AllFunctions         // Functions related to standard library types
  with syntax.std.ToAllStdOps   // syntax associated with standard library types
  with IdInstances              // Identity type and instances
</scala>

これは import をまとめるのに便利な方法だ。`Scalaz` object そのものは何も定義せずに、trait をミックスインしている。以下にそれぞれの trait を詳しくみていくけど、飲茶スタイルでそれぞれ別々に import することもできる。フルコースに戻ろう。

#### StateFunctions

import は名前と implicit を取り込む。まずは、名前だ。`StateFunctions` はいくつかの関数を定義する:

<scala>
package scalaz

trait StateFunctions {
  def constantState[S, A](a: A, s: => S): State[S, A] = ...
  def state[S, A](a: A): State[S, A] = ...
  def init[S]: State[S, S] = ...
  def get[S]: State[S, S] = ...
  def gets[S, T](f: S => T): State[S, T] = ...
  def put[S](s: S): State[S, Unit] = ...
  def modify[S](f: S => S): State[S, Unit] = ...
  def delta[A](a: A)(implicit A: Group[A]): State[A, A] = ...
}
</scala>

これらの関数を取り込むことで、`get` や `put` がグローバル関数であるかのように扱うことができる。何で? これが [7日目](http://eed3si9n.com/learning-scalaz-day7)に見た DSL を可能にしている:

<scala>
for {
  xs <- get[List[Int]]
  _ <- put(xs.tail)
} yield xs.head
</scala>

#### std.AllFunctions

次も名前だ。[`std.AllFunctions`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/std/AllFunctions.scala) もそれ自体は trait のミックスインだ:

<scala>
package scalaz
package std

trait AllFunctions
  extends ListFunctions
  with OptionFunctions
  with StreamFunctions
  with math.OrderingFunctions
  with StringFunctions

object AllFunctions extends AllFunctions
</scala>

上のそれぞれの trait はグローバル関数として振る舞う様々な関数をスコープに取り込む。例えば、`ListFunctions` はある特定の要素を 1つおきに挟み込む `intersperse` 関数を定義する:

<scala>
scala> intersperse(List(1, 2, 3), 7)
res3: List[Int] = List(1, 7, 2, 7, 3)
</scala>

微妙だ。個人的には注入されたメソッドを使うので、これらの関数は僕は使っていない。

#### IdInstances

`IdInstances` という名前だけど、これは `Id[A]` の型エイリアスも以下のように宣言する:

<scala>
  type Id[+X] = X
</scala>

名前はこれでおしまい。import は implicit も取り込むけど、implicit には 4つの使い方があると言った。特に最初の 2つ、型クラスインスタンスとメソッドや演算子の注入が大切だ。

#### std.AllInstances

これまでの所、僕は意図的に型クラスインスタンスという概念とメソッド注入 (別名 enrich my library) という概念をあたかも同じ事のように扱ってきた。だけど、`List` が `Monad` であることと、`Monad` が `>>=` 演算子を導入することは 2つの異なる事柄だ。

Scalaz 7 の設計方針で最も興味深いことの 1つとしてこれらの概念が徹底して "instance" (インスタンス) と "syntax" (構文) として区別されていることが挙げられる。たとえどれだけ一部のユーザにとって論理的に筋が通ったとしても、ライブラリがシンボルを使った演算子を導入すると議論の火種となる。 sbt、dispatch、specs などのライブラリやツールはそれぞれ独自の DSL を導入し、それらの効用に関して何度も議論が繰り広げられた。事を難しくするのが、複数の DSL を同時に使うと注入されたメソッドが衝突する可能性だ。

[`std.AllInstances`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/std/AllInstances.scala) は標準 (`std`) データ構造に対する型クラスのインスタンスのミックスインだ:

<scala>
package scalaz.std

trait AllInstances
  extends AnyValInstances with FunctionInstances with ListInstances with MapInstances
  with OptionInstances with SetInstances with StringInstances with StreamInstances with TupleInstances
  with EitherInstances with PartialFunctionInstances with TypeConstraintInstances
  with scalaz.std.math.BigDecimalInstances with scalaz.std.math.BigInts
  with scalaz.std.math.OrderingInstances
  with scalaz.std.util.parsing.combinator.Parsers
  with scalaz.std.java.util.MapInstances
  with scalaz.std.java.math.BigIntegerInstances
  with scalaz.std.java.util.concurrent.CallableInstances
  with NodeSeqInstances
  // Intentionally omitted: IterableInstances

object AllInstances extends AllInstances
</scala>

#### syntax.ToTypeClassOps

次は注入されるメソッドと演算子。これらは全て `scalaz.syntax` パッケージ下に入る。[`syntax.ToTypeClassOps`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/Syntax.scala) は型クラスに対して注入されるメソッドを全て導入する:

<scala>
package scalaz
package syntax

trait ToTypeClassOps
  extends ToSemigroupOps with ToMonoidOps with ToGroupOps with ToEqualOps with ToLengthOps with ToShowOps
  with ToOrderOps with ToEnumOps with ToMetricSpaceOps with ToPlusEmptyOps with ToEachOps with ToIndexOps
  with ToFunctorOps with ToPointedOps with ToContravariantOps with ToCopointedOps with ToApplyOps
  with ToApplicativeOps with ToBindOps with ToMonadOps with ToCojoinOps with ToComonadOps
  with ToBifoldableOps with ToCozipOps
  with ToPlusOps with ToApplicativePlusOps with ToMonadPlusOps with ToTraverseOps with ToBifunctorOps
  with ToBitraverseOps with ToArrIdOps with ToComposeOps with ToCategoryOps
  with ToArrowOps with ToFoldableOps with ToChoiceOps with ToSplitOps with ToZipOps with ToUnzipOps with ToMonadWriterOps with ToListenableMonadWriterOps
</scala>

例えば、[`syntax.ToBindOps`] は `[F: Bind]` である `F[A]` を `BindOps[F, A]` に暗黙に変換して、それは `>>=` 演算子を実装する。

#### syntax.ToDataOps

[`syntax.ToDataOps`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/Syntax.scala) は Scalz で定義されるデータ構造のために注入される演算子を導入する:

<scala>
trait ToDataOps extends ToIdOps with ToTreeOps with ToWriterOps with ToValidationOps with ToReducerOps with ToKleisliOps
</scala>

[`IdOps`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/IdOps.scala) メソッドは全ての型に注入され、便宜のためにある:

<scala>
package scalaz.syntax

trait IdOps[A] extends Ops[A] {
  final def ??(d: => A)(implicit ev: Null <:< A): A = ...
  final def |>[B](f: A => B): B = ...
  final def squared: (A, A) = ...
  def left[B]: (A \/ B) = ...
  def right[B]: (B \/ A) = ...
  final def wrapNel: NonEmptyList[A] = ...
  def matchOrZero[B: Monoid](pf: PartialFunction[A, B]): B = ...
  final def doWhile(f: A => A, p: A => Boolean): A = ...
  final def whileDo(f: A => A, p: A => Boolean): A = ...
  def visit[F[_] : Pointed](p: PartialFunction[A, F[A]]): F[A] = ...
}

trait ToIdOps {
  implicit def ToIdOps[A](a: A): IdOps[A] = new IdOps[A] {
    def self: A = a
  }
}
</scala>

興味深い事に、`ToTreeOps` も全てのデータ型を [`TreeV[A]`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/ToTreeOps.scala) に変換して 2つのメソッドを注入する:

<scala>
package scalaz
package syntax

trait TreeV[A] extends Ops[A] {
  def node(subForest: Tree[A]*): Tree[A] = ...
  def leaf: Tree[A] = ...
}

trait ToTreeOps {
  implicit def ToTreeV[A](a: A) = new TreeV[A]{ def self = a }
}
</scala>

つまり、これらのメソッドは `Tree` を作るためにある。

<scala>
scala> 1.node(2.leaf)
res7: scalaz.Tree[Int] = <tree>
</scala>

[`WriterV[A]`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/ToWriterOps.scala)、[`ValidationV[A]`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/ValidationV.scala)、[`ReducerV[A]`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/ReducerV.scala)、そして [`KleisliIdOps[A]`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/KleisliV.scala) も同様だ:

<scala>
scala> 1.set("log1")
res8: scalaz.Writer[String,Int] = scalaz.WriterTFunctions$$anon$26@2375d245

scala> "log2".tell
res9: scalaz.Writer[String,Unit] = scalaz.WriterTFunctions$$anon$26@699289fb

scala> 1.success[String]
res11: scalaz.Validation[String,Int] = Success(1)

scala> "boom".failureNel[Int]
res12: scalaz.ValidationNEL[String,Int] = Failure(NonEmptyList(boom))
</scala>

つまり、`syntax.ToDataOps` にミックスインされた trait の多くは全ての型にメソッドを導入して Scalaz のデータ構造を作る。

#### syntax.std.ToAllStdOps

最後に、[`syntax.std.ToAllStdOps`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/std/ToAllStdOps.scala) があって、これは Scala の標準型にメソッドや演算子を導入する。

<scala>
package scalaz
package syntax
package std

trait ToAllStdOps
  extends ToBooleanOps with ToOptionOps with ToOptionIdOps with ToListOps with ToStreamOps
  with ToFunction2Ops with ToFunction1Ops with ToStringOps with ToTupleOps with ToMapOps with ToEitherOps
</scala>

これは色々面白い事をやっている。[`BooleanOps`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/std/BooleanOps.scala) には様々な事への略記法が導入されている:

<scala>
scala> false /\ true
res14: Boolean = false

scala> false \/ true
res15: Boolean = true

scala> true option "foo"
res16: Option[String] = Some(foo)

scala> (1 > 10)? "foo" | "bar"
res17: String = bar

scala> (1 > 10)?? {List("foo")}
res18: List[String] = List()
</scala>

`option` 演算子はとても便利だ。3項演算子は if-else よりも短い記法に見える。

[`OptionOps`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/std/OptionOps.scala) も似たようなものを導入する:

<scala>
scala> 1.some? "foo" | "bar"
res28: String = foo

scala> 1.some | 2
res30: Int = 1
</scala>

一方 [`ListOps`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/std/ListOps.scala) はより伝統的な Monad 関連のものが多い:

<scala>
scala> List(1, 2) filterM {_ => List(true, false)}
res37: List[List[Int]] = List(List(1, 2), List(1), List(2), List())
</scala>

### アラカルト形式

僕は、飲茶スタイルという名前の方がいいと思うけど、カートで点心が運ばれてきて好きなものを選んで取る飲茶でピンと来なければ、カウンターに座って好きなものを頼む焼き鳥屋だと考えてもいい。

もし何らかの理由で `Scalaz._` を全て import したくなければ、好きなものを選ぶことができる。

#### 型クラスインスタンスと関数

型クラスはデータ構造ごとに分かれている。以下が `Option` のための全ての型クラスインスタンスを導入する方法だ:

<scala>
// fresh REPL
scala> import scalaz.std.option._
import scalaz.std.option._

scala> scalaz.Monad[Option].point(0)
res0: Option[Int] = Some(0)
</scala>

これは `Option` に関連する「グローバル」ヘルパー関数も取り込む。Scala 標準のデータ構造は `scalaz.std` パッケージの下にある。

全てのインスタンスが欲しければ、以下が全て取り込む方法だ:

<scala>
scala> import scalaz.std.AllInstances._
import scalaz.std.AllInstances._

scala> scalaz.Monoid[Int]
res2: scalaz.Monoid[Int] = scalaz.std.AnyValInstances$$anon$3@784e6f7c
</scala>

演算子の注入を一切行なっていないので、ヘルパー関数や型クラスインスタンスに定義された関数を使う必要がある (そっちの方が好みという人もいる)。

#### Scalaz 型クラス syntax

型クラスの syntax は型クラスごとに分かれている。以下が `Monad` のためのメソッドや演算子を注入する方法だ:

<scala>
scala> import scalaz.syntax.monad._
import scalaz.syntax.monad._

scala> import scalaz.std.option._
import scalaz.std.option._

scala> 0.point[Option]
res0: Option[Int] = Some(0)
</scala>

見ての通り、`Monad` メソッドだけじゃくて、`Pointed` のメソッドも取り込まれた。

`Tree` などの Scalaz のデータ構造のための syntax も `scalaz.syntax` パッケージ以下にある。以下が型クラスと Scalaz データ構造のための全ての syntax を取り込む方法だ:

<scala>
scala> import scalaz.syntax.all._
import scalaz.syntax.all._

scala> 1.leaf
res0: scalaz.Tree[Int] = <tree>
</scala>

#### 標準データ構造の syntax

標準データ構造の syntax はデータ構造ごとに分かれている。以下が `Boolean` に注入されるメソッドや演算子を取り込む方法だ:

<scala>
// fresh REPL
scala> import scalaz.syntax.std.boolean._
import scalaz.syntax.std.boolean._

scala> (1 > 10)? "foo" | "bar"
res0: String = bar
</scala>

標準データ構造のための全ての syntax を取り込むには:

<scala>
// fresh REPL
scala> import scalaz.syntax.std.all._
import scalaz.syntax.std.all._

scala> 1.some | 2
res1: Int = 1
</scala>

手早く書くつもりが、記事をまるごと使うことになった。
続きはまた、後で。
