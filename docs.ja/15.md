  [day14]: http://eed3si9n.com/ja/learning-scalaz-day14

<div class="floatingimage">
<img src="http://eed3si9n.com/images/openphoto-9038bw.jpg">
<div class="credit">Rodolfo Cartas for openphoto.net</div>
</div>

[14日目][day14]に Scalaz をハックし始めた。まず、`Vector` の型クラスインスタンスが `import Scalaz._` に含まれるようにした。次に、`<*>` を `ap` の中置記法に振り戻した。最後に、コンパイラが `Applicative[({type λ[α]=Int})#λ]` を見つけられるように `A` を `[α]A` に展開する暗黙の変換子を追加した。

3つの pull request とも上流に取り込んでもらえた! 以下の方法で早速同期する:

<div style="clear: both;"
<code>
$ git co scalaz-seven
$ git pull --rebase
</code>

一度落ち着いて僕らがいじった型クラスをみてみよう。

### Arrow

射とは、圏論の用語で関数っぽい振る舞いをするものの抽象概念だ。Scalaz だと `Function1[A, B]`、`PartialFunction[A, B]`、`Kleisli[F[_], A, B]`、そして `CoKleisli[F[_], A, B]` がこれにあたる。他の型クラスがコンテナを抽象化するのと同様に `Arrow` はこれらを抽象化する。

以下が [`Arrow`](https://github.com/eed3si9n/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Arrow.scala) の型クラスコントラクトだ:

<scala>
trait Arrow[=>:[_, _]] extends Category[=>:] { self =>
  def id[A]: A =>: A
  def arr[A, B](f: A => B): A =>: B
  def first[A, B, C](f: (A =>: B)): ((A, C) =>: (B, C))
}
</scala>

`Arrow[=>:[_, _]]` は `Category[=>:]` を継承するみたいだ。

### Category、ArrId、そして Compose

以下が [`Category[=>:[_, _]]`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Category.scala) だ:

<scala>
trait Category[=>:[_, _]] extends ArrId[=>:] with Compose[=>:] { self =>
  // no contract function
} 
</scala>

これは [`ArrId[=>:]`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/ArrId.scala) を継承する:

<scala>
trait ArrId[=>:[_, _]]  { self =>
  def id[A]: A =>: A
}
</scala>

`Category[=>:[_, _]]` はまた [`Compose[=>:]`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Compose.scala) も継承する:

<scala>
trait Compose[=>:[_, _]]  { self =>
  def compose[A, B, C](f: B =>: C, g: A =>: B): (A =>: C)
}
</scala>

`compose` 関数は 2つの射を合成する。`Compose` は以下の[演算子](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/ComposeSyntax.scala)を導入する:

<scala>
trait ComposeOps[F[_, _],A, B] extends Ops[F[A, B]] {
  final def <<<[C](x: F[C, A]): F[C, B] = F.compose(self, x)
  final def >>>[C](x: F[B, C]): F[A, C] = F.compose(x, self)
}
</scala>

`>>>` と `<<<` の意味は射に依存するけど、関数の場合は `andThen` と `compose` と同じだ:

<scala>
scala> val f = (_:Int) + 1
f: Int => Int = <function1>

scala> val g = (_:Int) * 100
g: Int => Int = <function1>

scala> (f >>> g)(2)
res0: Int = 300

scala> (f <<< g)(2)
res1: Int = 201
</scala> 

### Arrow、再び

`Arrow[=>:[_, _]]` の型宣言は少し変わってみえるけど、これは `Arrow[M[_, _]]` と言っているのと変わらない。2つのパラメータを取る型コンストラクタで便利なのは `=>:[A, B]` を `A =>: B` のように中置記法で書けることだ。

`arr` 関数は普通の関数から射を作り、`id` は恒等射を返し、`first` は既存の射の出入力をペアに拡張した新しい射を返す。

上記の関数を使って、`Arrow` は以下の[演算子](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/ArrowSyntax.scala)を導入する:

<scala>
trait ArrowOps[F[_, _],A, B] extends Ops[F[A, B]] {
  final def ***[C, D](k: F[C, D]): F[(A, C), (B, D)] = F.splitA(self, k)
  final def &&&[C](k: F[A, C]): F[A, (B, C)] = F.combine(self, k)
  ...
}
</scala>

Haskell の [Arrow tutorial](http://www.haskell.org/haskellwiki/Arrow_tutorial) を読んでみる:

> `(***)` combines two arrows into a new arrow by running the two arrows on a pair of values (one arrow on the first item of the pair and one arrow on the second item of the pair).
>
> `(***)` は 2つの射を値のペアに対して (1つの射はペアの最初の項で、もう 1つの射はペアの 2つめの項で) 実行することで 1つの新しいに射へと組み合わせる。

具体例で説明すると:

<scala>
scala> (f *** g)(1, 2)
res3: (Int, Int) = (2,200)
</scala>

> `(&&&)` は 2つの射を両方とも同じ値に対して実行することで 1つの新しい射へと組み合わせる:

以下が `&&&` の例:

<scala>
scala> (f &&& g)(2)
res4: (Int, Int) = (3,200)
</scala>

関数やペアにらんらかのコンテキストを与えたい場合は射が便利かもしれない。

### Unapply

Scala コンパイラで苦労させられているのは、例えば `F[M[_, _]]` と `F[M[_]]` や `M[_]` と `F[M[_]]` など異なるカインド付けされた型の間での型推論が無いことだ。

具体的には、`Applicative[M[_]]` のインスタンスは `(* -> *) -> *` (ただ 1つの型を受け取る型コンストラクタを受け取る型コンストラクタ) だ。`Int => Int` を `Int => A` として扱うことで applicative として扱えることが知られている:

<scala>
scala> Applicative[Function1[Int, Int]]
<console>:14: error: Int => Int takes no type parameters, expected: one
              Applicative[Function1[Int, Int]]
                          ^

scala> Applicative[({type l[A]=Function1[Int, A]})#l]
res14: scalaz.Applicative[[A]Int => A] = scalaz.std.FunctionInstances$$anon$2@56ae78ac
</scala>

これは `Validation` のような `M[_,_]` で面倒になる。Scalaz が手伝ってくれる 1つの方法として [`Unapply`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Unapply.scala) というメタインスタンスがある。

<scala>
trait Unapply[TC[_[_]], MA] {
  /** The type constructor */
  type M[_]
  /** The type that `M` was applied to */
  type A
  /** The instance of the type class */
  def TC: TC[M]
  /** Evidence that MA =:= M[A] */
  def apply(ma: MA): M[A]
}
</scala>

`traverse` などの Scalaz のメソッドが `Applicative[M[_]]` を要請するとき、代わりに `Unapply[Applicative, X]` を要請できる。コンパイル時に Scalac は `Function1[Int, Int]` を `M[A]` に強制できないかをパラメータを固定したり、追加したり、もちろん既存の型クラスインスタンスを利用したりして暗黙の変換子を全て試す。

<scala>
scala> implicitly[Unapply[Applicative, Function1[Int, Int]]]
res15: scalaz.Unapply[scalaz.Applicative,Int => Int] = scalaz.Unapply_0$$anon$9@2e86566f
</scala>

僕が昨日追加したのは型 `A` に偽の型コンストラクタをつけて `M[A]` に昇進させる方法だ。これによって `Int` を `Applicative` として扱いやすくなる。だけど、`TC0: TC[({type λ[α] = A0})#λ]` を暗黙に要請するから、どの型でも `Applicative` に昇進できるというわけではない。

<scala>
scala> implicitly[Unapply[Applicative, Int]]
res0: scalaz.Unapply[scalaz.Applicative,Int] = scalaz.Unapply_3$$anon$1@5179dc20

scala> implicitly[Unapply[Applicative, Any]]
<console>:14: error: Unable to unapply type `Any` into a type constructor of kind `M[_]` that is classified by the type class `scalaz.Applicative`
1) Check that the type class is defined by compiling `implicitly[scalaz.Applicative[<type constructor>]]`.
2) Review the implicits in object Unapply, which only cover common type 'shapes'
(implicit not found: scalaz.Unapply[scalaz.Applicative, Any])
              implicitly[Unapply[Applicative, Any]]
                        ^
</scala>

動いた。これらの結果として以下のようなコードが少しきれいに書けるようになる:

<scala>
scala> val failedTree: Tree[Validation[String, Int]] = 1.success[String].node(
         2.success[String].leaf, "boom".failure[Int].leaf)
failedTree: scalaz.Tree[scalaz.Validation[String,Int]] = <tree>

scala> failedTree.sequence[({type l[X]=Validation[String, X]})#l, Int]
res2: scalaz.Validation[java.lang.String,scalaz.Tree[Int]] = Failure(boom)
<scala>

以下が `sequenceU` を用いたもの:

<scala>
scala> failedTree.sequenceU
res3: scalaz.Validation[String,scalaz.Tree[Int]] = Failure(boom)
</scala>

ブーム。

### 並列合成

`Unapply` に加えた変更で monoidal applicative functor は動くようになったけど、組み合わせはまだできない:

<scala>
scala> val f = { (x: Int) => x + 1 }
f: Int => Int = <function1>

scala> val g = { (x: Int) => List(x, 5) }
g: Int => List[Int] = <function1>

scala> val h = f &&& g
h: Int => (Int, List[Int]) = <function1>

scala> List(1, 2, 3) traverseU f
res0: Int = 9

scala> List(1, 2, 3) traverseU g
res1: List[List[Int]] = List(List(1, 2, 3), List(1, 2, 5), List(1, 5, 3), List(1, 5, 5), List(5, 2, 3), List(5, 2, 5), List(5, 5, 3), List(5, 5, 5))

scala> List(1, 2, 3) traverseU h
res2: (Int, List[List[Int]]) = (9,List(List(1, 5), List(2, 5), List(3, 5)))
</scala>

`f` と `g` は動く。問題は `traverseU` のペアの解釈だ。`f` と `g` を手で組み合わせるとこうなる:

<scala>
scala> val h = { (x: Int) => (f(x), g(x)) }
h: Int => (Int, List[Int]) = <function1>
</scala>

これが `Tuple2Functor` だ:

<scala>
private[scalaz] trait Tuple2Functor[A1] extends Functor[({type f[x] = (A1, x)})#f] {
  override def map[A, B](fa: (A1, A))(f: A => B) =
    (fa._1, f(fa._2))
}
</scala>

Scalaz には確かに applicative functor の積という概念はあって `Apply` 型クラスに `product` メソッドがあるんだけど、ペアを使ってエンコードしているせいで implicits が提供されていない。現時点では、Scalaz に EIP に記述されているように applicative 関数 (`A => M[B]`) の積を実装する方法があるかは不明だ:

<haskell>
data (m ⊠ n) a = Prod {pfst ::m a,psnd :: n a}
(⊗)::(Functor m,Functor n) ⇒ (a → m b) → (a → n b) → (a → (m ⊠ n) b)
(f ⊗ g) x = Prod (f x) (g x)
</haskell>

これは合成に関しても言えることだ。`scalaz-seven` ブランチからブランチする:

<code>
$ git co scalaz-seven
Already on 'scalaz-seven'
$ git branch topic/appcompose
$ git co topic/appcompose
Switched to branch 'topic/appcompose'
</code>

とりあえず実際の型に保存してみて、きれいに直す心配は後にしよう。

<scala>
package scalaz

import Id._

trait XProduct[A, B] {
  def _1: A
  def _2: B
  override def toString: String = "XProduct(" + _1.toString + ", " + _2.toString + ")"
}

trait XProductInstances {
  implicit def productSemigroup[A1, A2](implicit A1: Semigroup[A1], A2: Semigroup[A2]): Semigroup[XProduct[A1, A2]] = new XProductSemigroup[A1, A2] {
    implicit def A1 = A1
    implicit def A2 = A2
  }  
  implicit def productFunctor[F[_], G[_]](implicit F0: Functor[F], G0: Functor[G]): Functor[({type λ[α] = XProduct[F[α], G[α]]})#λ] = new XProductFunctor[F, G] {
    def F = F0
    def G = G0
  }
  implicit def productPointed[F[_], G[_]](implicit F0: Pointed[F], G0: Pointed[G]): Pointed[({type λ[α] = XProduct[F[α], G[α]]})#λ] = new XProductPointed[F, G] {
    def F = F0
    def G = G0
  }
  implicit def productApply[F[_], G[_]](implicit F0: Apply[F], G0: Apply[G]): Apply[({type λ[α] = XProduct[F[α], G[α]]})#λ] = new XProductApply[F, G] {
    def F = F0
    def G = G0
  }
  implicit def productApplicativeFG[F[_], G[_]](implicit F0: Applicative[F], G0: Applicative[G]): Applicative[({type λ[α] = XProduct[F[α], G[α]]})#λ] = new XProductApplicative[F, G] {
    def F = F0
    def G = G0
  }
  implicit def productApplicativeFB[F[_], B](implicit F0: Applicative[F], B0: Applicative[({type λ[α] = B})#λ]): Applicative[({type λ[α] = XProduct[F[α], B]})#λ] = new XProductApplicative[F, ({type λ[α] = B})#λ] {
    def F = F0
    def G = B0
  }
  implicit def productApplicativeAG[A, G[_]](implicit A0: Applicative[({type λ[α] = A})#λ], G0: Applicative[G]): Applicative[({type λ[α] = XProduct[A, G[α]]})#λ] = new XProductApplicative[({type λ[α] = A})#λ, G] {
    def F = A0
    def G = G0
  }
  implicit def productApplicativeAB[A, B](implicit A0: Applicative[({type λ[α] = A})#λ], B0: Applicative[({type λ[α] = B})#λ]): Applicative[({type λ[α] = XProduct[A, B]})#λ] = new XProductApplicative[({type λ[α] = A})#λ, ({type λ[α] = B})#λ] {
    def F = A0
    def G = B0
  }  
}

trait XProductFunctions {
  def product[A, B](a1: A, a2: B): XProduct[A, B] = new XProduct[A, B] {
    def _1 = a1
    def _2 = a2
  }
}

object XProduct extends XProductFunctions with XProductInstances {
  def apply[A, B](a1: A, a2: B): XProduct[A, B] = product(a1, a2)
}
private[scalaz] trait XProductSemigroup[A1, A2] extends Semigroup[XProduct[A1, A2]] {
  implicit def A1: Semigroup[A1]
  implicit def A2: Semigroup[A2]
  def append(f1: XProduct[A1, A2], f2: => XProduct[A1, A2]) = XProduct(
    A1.append(f1._1, f2._1),
    A2.append(f1._2, f2._2)
    )
}
private[scalaz] trait XProductFunctor[F[_], G[_]] extends Functor[({type λ[α] = XProduct[F[α], G[α]]})#λ] {
  implicit def F: Functor[F]
  implicit def G: Functor[G]
  override def map[A, B](fa: XProduct[F[A], G[A]])(f: (A) => B): XProduct[F[B], G[B]] =
    XProduct(F.map(fa._1)(f), G.map(fa._2)(f))
}

private[scalaz] trait XProductPointed[F[_], G[_]] extends Pointed[({type λ[α] = XProduct[F[α], G[α]]})#λ] with XProductFunctor[F, G] {
  implicit def F: Pointed[F]
  implicit def G: Pointed[G]
  def point[A](a: => A): XProduct[F[A], G[A]] = XProduct(F.point(a), G.point(a))
}

private[scalaz] trait XProductApply[F[_], G[_]] extends Apply[({type λ[α] = XProduct[F[α], G[α]]})#λ] with XProductFunctor[F, G] {
  implicit def F: Apply[F]
  implicit def G: Apply[G]
  def ap[A, B](fa: => XProduct[F[A], G[A]])(f: => XProduct[F[A => B], G[A => B]]): XProduct[F[B], G[B]] =
    XProduct(F.ap(fa._1)(f._1), G.ap(fa._2)(f._2))
}

private[scalaz] trait XProductApplicative[F[_], G[_]] extends Applicative[({type λ[α] = XProduct[F[α], G[α]]})#λ] with XProductPointed[F, G] {
  implicit def F: Applicative[F]
  implicit def G: Applicative[G]
  def ap[A, B](fa: => XProduct[F[A], G[A]])(f: => XProduct[F[(A) => B], G[(A) => B]]): XProduct[F[B], G[B]] =
    XProduct(F.ap(fa._1)(f._1), G.ap(fa._2)(f._2))
}
</scala>

実装のほとんどは `Tuple2` を使ってる `Product.scala` から奪ってきた。これが `XProduct` を使った最初の試みだ:

<scala>
scala> XProduct(1.some, 2.some) map {_ + 1}
<console>:14: error: Unable to unapply type `scalaz.XProduct[Option[Int],Option[Int]]` into a type constructor of kind `M[_]` that is classified by the type class `scalaz.Functor`
1) Check that the type class is defined by compiling `implicitly[scalaz.Functor[<type constructor>]]`.
2) Review the implicits in object Unapply, which only cover common type 'shapes'
(implicit not found: scalaz.Unapply[scalaz.Functor, scalaz.XProduct[Option[Int],Option[Int]]])
              XProduct(1.some, 2.some) map {_ + 1}
                      ^
</scala>

解読できれば、このエラーメッセージは実際役に立つものだ。これは `Unapply` メタインスタンスを探している。おそらくこの形のものがまだ定義されていないんだと思う。以下が新しい unapply だ:

<scala>
  implicit def unapplyMFGA[TC[_[_]], F[_], G[_], M0[_, _], A0](implicit TC0: TC[({type λ[α] = M0[F[α], G[α]]})#λ]): Unapply[TC, M0[F[A0], G[A0]]] {
    type M[X] = M0[F[X], G[X]]
    type A = A0
  } = new Unapply[TC, M0[F[A0], G[A0]]] {
    type M[X] = M0[F[X], G[X]]
    type A = A0
    def TC = TC0
    def apply(ma: M0[F[A0], G[A0]]) = ma
  }
</scala>

もう 1度。

<scala>
scala> XProduct(1.some, 2.some) map {_ + 1}
res0: scalaz.Unapply[scalaz.Functor,scalaz.XProduct[Option[Int],Option[Int]]]{type M[X] = scalaz.XProduct[Option[X],Option[X]]; type A = Int}#M[Int] = XProduct(Some(2), Some(3))
</scala>

普通の applicative としても使える:

<scala>
scala> (XProduct(1, 2.some) |@| XProduct(3, none[Int])) {_ |+| (_: XProduct[Int, Option[Int]]) }
res1: scalaz.Unapply[scalaz.Apply,scalaz.XProduct[Int,Option[Int]]]{type M[X] = scalaz.XProduct[Int,Option[Int]]; type A = scalaz.XProduct[Int,Option[Int]]}#M[scalaz.XProduct[Int,Option[Int]]] = XProduct(4, Some(2))
</scala>

EIP の word count の例題を書き換えてみる。

<scala>
scala> val text = "the cat in the hat\n sat on the mat\n".toList
text: List[Char] = 
List(t, h, e,  , c, a, t,  , i, n,  , t, h, e,  , h, a, t, 
,  , s, a, t,  , o, n,  , t, h, e,  , m, a, t, 
)

scala> def count[A] = (a: A) => 1
count: [A]=> A => Int

scala> val charCount = count[Char]
charCount: Char => Int = <function1>

scala> text traverseU charCount
res10: Int = 35

scala> import scalaz.std.boolean.test
import scalaz.std.boolean.test

scala> val lineCount = (c: Char) => test(c === '\n')
lineCount: Char => Int = <function1>

scala> text traverseU lineCount
res11: Int = 2

scala> val wordCount = (c: Char) => for {
         x <- get[Boolean]
         val y = c =/= ' '
         _ <- put(y)
       } yield test(y /\ !x)
wordCount: Char => scalaz.StateT[scalaz.Id.Id,Int,Int] = <function1>

scala> (text traverseU wordCount) eval false count(_ > 0)
res25: Int = 9

scala> text traverseU { (c: Char) => XProduct(charCount(c), lineCount(c)) }
res26: scalaz.XProduct[Int,Int] = XProduct(35, 2)
</scala>

これで applicative 関数を並列に組み合わせることができた。ペアを使ったとしたらどうなるって?

<scala>
scala> text traverseU { (c: Char) => (charCount(c), lineCount(c)) }
res27: (Int, List[Int]) = (35,List(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1))
</scala>

笑! だけど、`Unapply` の問題はより複雑な構造には対応できないことだ:

<scala>
scala> text traverseU { (c: Char) => XProduct(charCount(c), wordCount(c)) }
<console>:19: error: Unable to unapply type `scalaz.XProduct[Int,scalaz.StateT[scalaz.Id.Id,Boolean,Int]]` into a type constructor of kind `M[_]` that is classified by the type class `scalaz.Applicative`
1) Check that the type class is defined by compiling `implicitly[scalaz.Applicative[<type constructor>]]`.
2) Review the implicits in object Unapply, which only cover common type 'shapes'
(implicit not found: scalaz.Unapply[scalaz.Applicative, scalaz.XProduct[Int,scalaz.StateT[scalaz.Id.Id,Boolean,Int]]])
              text traverseU { (c: Char) => XProduct(charCount(c), wordCount(c)) }
                   ^
</scala>

これらが解決できれば、`Arrow` か `Function1` に `@>>>` と `@&&&` 演算子があって EIP で書かれているような applicative の合成ができれば便利だと思う。

次回からはまた別のトピックをカバーしよう。
