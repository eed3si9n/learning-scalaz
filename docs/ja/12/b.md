
### The Essence of the Iterator Pattern

2006年に同じ著者は、[The Essence of the Iterator Pattern](http://www.cs.ox.ac.uk/jeremy.gibbons/publications/iterator.pdf) を発表した。リンクしたのは改訂された 2009年版。この論文は GoF の Iterator パターンを投射 (mapping) と累積 (accumulating) に分解することで applicative スタイルに関する議論を展開している。

論文の前半は関数型の反復と applicative スタイルの報告となっている。applicative functor に関しては、以下の 3種類あるとしている:
1. Monadic applicative functors
2. Naperian applicative functors
3. Monoidal applicative functors

全てのモナドが applicative であることは何回か話した。Naperian applicative functor は固定された形のデータ構造を zip するものだ。また、applicative functor は最初は *idiom* と名付けられたらしいので、この論文で *idiomatic* という言葉が出てきたら *applicative* という意味だ。

### Monoidal applicatives

Scalaz は `Monoid[m].applicative` を実装して、どんな Monoid でも applicative に変換できる。

```scala
scala> Monoid[Int].applicative.ap2(1, 1)(0)
res99: Int = 2

scala> Monoid[List[Int]].applicative.ap2(List(1), List(1))(Nil)
res100: List[Int] = List(1, 1)
```

### Applicative functor の組み合わせ

EIP:

> Like monads, applicative functors are closed under products; so two independent idiomatic effects can generally be fused into one, their product.

Scalaz では、`Applicative` 型クラスに `product` が実装されている:

```scala
trait Applicative[F[_]] extends Apply[F] with Pointed[F] { self =>
  ...
  /**The product of Applicatives `F` and `G`, `[x](F[x], G[x]])`, is an Applicative */
  def product[G[_]](implicit G0: Applicative[G]): Applicative[({type λ[α] = (F[α], G[α])})#λ] = new ProductApplicative[F, G] {
    implicit def F = self
    implicit def G = G0
  }
  ...
}
```

これを使って `List` と `Option` の積を作ってみる。

```scala
scala> Applicative[List].product[Option]
res0: scalaz.Applicative[[α](List[α], Option[α])] = scalaz.Applicative\$\$anon\$2@211b3c6a

scala> Applicative[List].product[Option].point(1)
res1: (List[Int], Option[Int]) = (List(1),Some(1))
```

積は `Tuple2` として実装されているみたいだ。Applicative スタイルを使って append してみよう:

```scala
scala> ((List(1), 1.some) |@| (List(1), 1.some)) {_ |+| _}
res2: (List[Int], Option[Int]) = (List(1, 1),Some(2))

scala> ((List(1), 1.success[String]) |@| (List(1), "boom".failure[Int])) {_ |+| _}
res6: (List[Int], scalaz.Validation[String,Int]) = (List(1, 1),Failure(boom))
```

EIP:

> Unlike monads in general, applicative functors are also closed under composition; so two sequentially-dependent idiomatic effects can generally be fused into one, their composition.

これは `Applicative` では `compose` と呼ばれている:

```scala
trait Applicative[F[_]] extends Apply[F] with Pointed[F] { self =>
...
  /**The composition of Applicatives `F` and `G`, `[x]F[G[x]]`, is an Applicative */
  def compose[G[_]](implicit G0: Applicative[G]): Applicative[({type λ[α] = F[G[α]]})#λ] = new CompositionApplicative[F, G] {
    implicit def F = self
    implicit def G = G0
  }
...
}
```

`List` と `Option` を合成してみる。

```scala
scala> Applicative[List].compose[Option]
res7: scalaz.Applicative[[α]List[Option[α]]] = scalaz.Applicative\$\$anon\$1@461800f1

scala> Applicative[List].compose[Option].point(1)
res8: List[Option[Int]] = List(Some(1))
```

EIP:

> The two operators `⊗` and `⊙` allow us to combine idiomatic computations in two different ways; we call them *parallel* and *sequential composition*, respectively.

Applicative を合成しても applicative が得られるのは便利だ。この特性を利用してこの論文ではモジュール性の話をしているんだと思う。

### Idiomatic traversal

EIP:

> *Traversal* involves iterating over the elements of a data structure, in the style of a `map`, but interpreting certain function applications idiomatically.

これに対応する Scalaz 7 での型クラスは [`Traverse`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Traverse.scala) と呼ばれている:

```scala
trait Traverse[F[_]] extends Functor[F] with Foldable[F] { self =>
  def traverseImpl[G[_]:Applicative,A,B](fa: F[A])(f: A => G[B]): G[F[B]]
}
```

これは `traverse` 演算子を導入する:

```scala
trait TraverseOps[F[_],A] extends Ops[F[A]] {
  final def traverse[G[_], B](f: A => G[B])(implicit G: Applicative[G]): G[F[B]] =
    G.traverse(self)(f)
  ...
}
```

`List` に対して使ってみる:

```scala
scala> List(1, 2, 3) traverse { x => (x > 0) option (x + 1) }
res14: Option[List[Int]] = Some(List(2, 3, 4))

scala> List(1, 2, 0) traverse { x => (x > 0) option (x + 1) }
res15: Option[List[Int]] = None
```

`Boolean` に注入された `option` 演算子は `(x > 0) option (x + 1)` を `if (x > 0) Some(x + 1) else None` に展開する。

EIP:

> In the case of a monadic applicative functor, traversal specialises to monadic map, and has the same uses.

確かに `flatMap` 的な感じがする、ただし渡された関数が `List` を返すことを要請する代わりに `[G: Applicative]` であるときの `G[B]` を要請する。

EIP:

> For a monoidal applicative functor, traversal accumulates values. The function *reduce* performs that accumulation, given an argument that assigns a value to each element.

```scala
scala> Monoid[Int].applicative.traverse(List(1, 2, 3)) {_ + 1}
res73: Int = 9
```

`traverse` 演算子を使って書きたかったんだけど、僕には分からなかった。

### 形と内容

EIP:

> In addition to being parametrically polymorphic in the collection elements, the generic *traverse* operation is parametrised along two further dimensions: the datatype being traversed, and the applicative functor in which the traversal is interpreted. Specialising the latter to lists as a monoid yields a generic *contents* operation.

```scala
scala> def contents[F[_]: Traverse, A](f: F[A]): List[A] =
         Monoid[List[A]].applicative.traverse(f) {List(_)}
contents: [F[_], A](f: F[A])(implicit evidence\$1: scalaz.Traverse[F])List[A]

scala> contents(List(1, 2, 3))
res87: List[Int] = List(1, 2, 3)

scala> contents(NonEmptyList(1, 2, 3))
res88: List[Int] = List(1, 2, 3)

scala> val tree: Tree[Char] = 'P'.node('O'.leaf, 'L'.leaf)
tree: scalaz.Tree[Char] = <tree>

scala> contents(tree)
res90: List[Char] = List(P, O, L)
```

これで `Traverse` をサポートするデータ構造ならばなんでも `List` へと変換できるようになった。`contents` は以下のようにも書ける:

```scala
scala> def contents[F[_]: Traverse, A](f: F[A]): List[A] =
         f.traverse[({type l[X]=List[A]})#l, A] {List(_)}
contents: [F[_], A](f: F[A])(implicit evidence\$1: scalaz.Traverse[F])List[A]
```

> The other half of the decomposition is obtained simply by a map, which is to say, a traversal interpreted in the identity idiom.

ここでの "identity idiom" とは Scalaz の `Id` モナドのことだ。

```scala
scala> def shape[F[_]: Traverse, A](f: F[A]): F[Unit] =
  f traverse {_ => ((): Id[Unit])}
shape: [F[_], A](f: F[A])(implicit evidence\$1: scalaz.Traverse[F])F[Unit]

scala> shape(List(1, 2, 3))
res95: List[Unit] = List((), (), ())

scala> shape(tree).drawTree
res98: String = 
"()
|
()+- 
|
()`- 
"
```

EIP:

> This pair of traversals nicely illustrates the two aspects of iterations that we are focussing on, namely mapping and accumulation.

`decompose` も実装してみよう:

```scala
scala> def decompose[F[_]: Traverse, A](f: F[A]) = (shape(f), contents(f))
decompose: [F[_], A](f: F[A])(implicit evidence\$1: scalaz.Traverse[F])(F[Unit], List[A])

scala> decompose(tree)
res110: (scalaz.Tree[Unit], List[Char]) = (<tree>,List(P, O, L))
```

これは動作したけど、木構造を 2回ループしている。Applicative の積は applicative なことを覚えているよね?

```scala
scala> def decompose[F[_]: Traverse, A](f: F[A]) =
         Applicative[Id].product[({type l[X]=List[A]})#l].traverse(f) { x => (((): Id[Unit]), List(x)) }
decompose: [F[_], A](f: F[A])(implicit evidence\$1: scalaz.Traverse[F])(scalaz.Scalaz.Id[F[Unit]], List[A])

scala> decompose(List(1, 2, 3, 4))
res135: (scalaz.Scalaz.Id[List[Unit]], List[Int]) = (List((), (), (), ()),List(1, 2, 3, 4))

scala> decompose(tree)
res136: (scalaz.Scalaz.Id[scalaz.Tree[Unit]], List[Char]) = (<tree>,List(P, O, L))
```

上の実装は monoidal applicative functor を得るのに型注釈に頼っているから Haskell のように綺麗には書けない:

```haskell
decompose = traverse (shapeBody ⊗ contentsBody)
```

### Sequence

`Traverse` は `sequence` という便利なメソッドも導入する。これは Haskell の `sequence` 関数に由来する命名だから、Hoogle してみる:

> ```haskell sequence :: Monad m => [m a] -> m [a]```
> Evaluate each action in the sequence from left to right, and collect the results.

これが `sequence` メソッドだ:

```scala
  /** Traverse with the identity function */
  final def sequence[G[_], B](implicit ev: A === G[B], G: Applicative[G]): G[F[B]] = {
    val fgb: F[G[B]] = ev.subst[F](self)
    F.sequence(fgb)
  }
```

`Monad` の代わりに要請が `Applicative` に緩められている。使ってみよう:

```scala
scala> List(1.some, 2.some).sequence
res156: Option[List[Int]] = Some(List(1, 2))

scala> List(1.some, 2.some, none).sequence
res157: Option[List[Int]] = None
```

これは使えそうだ。さらに `Traverse` のメソッドなため、他のデータ構造でも動く:

```scala
scala> val validationTree: Tree[Validation[String, Int]] = 1.success[String].node(
         2.success[String].leaf, 3.success[String].leaf)
validationTree: scalaz.Tree[scalaz.Validation[String,Int]] = <tree>

scala> validationTree.sequence[({type l[X]=Validation[String, X]})#l, Int]
res162: scalaz.Validation[String,scalaz.Unapply[scalaz.Traverse,scalaz.Tree[scalaz.Validation[String,Int]]]{type M[X] = scalaz.Tree[X]; type A = scalaz.Validation[String,Int]}#M[Int]] = Success(<tree>)

scala> val failedTree: Tree[Validation[String, Int]] = 1.success[String].node(
         2.success[String].leaf, "boom".failure[Int].leaf)
failedTree: scalaz.Tree[scalaz.Validation[String,Int]] = <tree>

scala> failedTree.sequence[({type l[X]=Validation[String, X]})#l, Int]
res163: scalaz.Validation[String,scalaz.Unapply[scalaz.Traverse,scalaz.Tree[scalaz.Validation[String,Int]]]{type M[X] = scalaz.Tree[X]; type A = scalaz.Validation[String,Int]}#M[Int]] = Failure(boom)
```

### 収集と拡散

EIP:

> We have found it convenient to consider special cases of effectful traversals, in which the mapping aspect is independent of the accumulation, and vice versa. The ﬁrst of these traversals accumulates elements effectfully, with an operation of type `a → m ()`, but modiﬁes those elements purely and independently of this accumulation, with a function of type `a → b`.

これは `for` ループの外側で可変な変数に累積を行なうことを真似している。`Traverse` は `traverse` を `State`モナドに特殊化した `traverseS` も導入する。これを使うと `collect` は以下のように書ける:

```scala
scala> def collect[F[_]: Traverse, A, S, B](t: F[A])(f: A => B)(g: S => S) =
         t.traverseS[S, B] { a => State { (s: S) => (g(s), f(a)) } }
collect: [F[_], A, S, B](t: F[A])(f: A => B)(g: S => S)(implicit evidence\$1: scalaz.Traverse[F])scalaz.State[S,scalaz.Unapply[scalaz.Traverse,F[A]]{type M[X] = F[X]; type A = A}#M[B]]

scala> val loop = collect(List(1, 2, 3, 4)) {(_: Int) * 2} {(_: Int) + 1}
loop: scalaz.State[Int,scalaz.Unapply[scalaz.Traverse,List[Int]]{type M[X] = List[X]; type A = Int}#M[Int]] = scalaz.package\$State\$\$anon\$1@3926008a

scala> loop(0)
res165: (Int, scalaz.Unapply[scalaz.Traverse,List[Int]]{type M[X] = List[X]; type A = Int}#M[Int]) = (4,List(2, 4, 6, 8))
```

EIP:

> The second kind of traversal modiﬁes elements purely but dependent on the state, with a binary function of type `a → b → c`, evolving this state independently of the elements, via a computation of type `m b`.

これはやっていることは `traverseS` と変わらない。`label` の実装はこうなる:

```scala
scala> def label[F[_]: Traverse, A](f: F[A]): F[Int] =
         (f.traverseS {_ => for {
           n <- get[Int]
           x <- put(n + 1)
         } yield n}) eval 0
label: [F[_], A](f: F[A])(implicit evidence\$1: scalaz.Traverse[F])F[Int]
```

これはデータ構造の内容を無視して、0 から始まる数字で置換している。かなり副作用的 (effecty) だ。`List` と `Tree` で試してみる:

```scala
scala> label(List(10, 2, 8))
res176: List[Int] = List(0, 1, 2)

scala> label(tree).drawTree
res177: String = 
"0
|
1+- 
|
2`- 
"
```

### リンク

EIP は Scala の関数型をやってる人の間では人気の論文みたいだ。

[Eric Torreborre (@etorreborre)](https://twitter.com/etorreborre) さんの [The Essence of the Iterator Pattern](http://etorreborre.blogspot.com/2011/06/essence-of-iterator-pattern.html) が一番この論文を詳しく研究している。これは [Iterator パターンの本質](http://eed3si9n.com/ja/essence-of-iterator-pattern)として僕が訳させてもらった。基礎からみていっているので、じっくり読む価値がある。

[Debasish Ghosh (@debasishg)](https://twitter.com/debasishg) さんの [Iteration in Scala - effectful yet functional](http://debasishg.blogspot.in/2011/01/iteration-in-scala-effectful-yet.html) は短めだけど、Scalaz に焦点を絞って良い所を持っていっている。

[Marc-Daniel Ortega (@patterngazer)](https://twitter.com/patterngazer) さんの [Where we traverse, accumulate and collect in Scala](http://patterngazer.blogspot.com/2012/03/where-we-traverse-accumulate-and.html) も Scalaz を使って `sequence` や `collect` をみている。

続きはまた後で。
