  [day8]: http://eed3si9n.com/ja/learning-scalaz-day8

[8日目][day8]は、モナディック関数の `join`、`filterM`、と `foldLeftM` をみて、安全な RPN 電卓を実装して、`Kleisli` を使ってモナディック関数を合成する方法をみた後で、独自のモナド `Prob` を実装した。

### Tree

Learn You a Haskell for Great Good の最終章 [Zippers](http://learnyouahaskell.com/zippers) を始めよう:

> In this chapter, we'll see how we can take some data structure and focus on a part of it in a way that makes changing its elements easy and walking around it efficient.

Scala の case class の等価性はヒープ内の位置じゃなくて内容で決まる。そのため、木構造内の複数のノードを識別するだけでももし偶然同じ型と内容があれば Scala は同じもの扱いしてしまう。

独自の木を実装するかわりに、Scalaz の [`Tree`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Tree.scala) を使おう:

<scala>
sealed trait Tree[A] {
  /** The label at the root of this tree. */
  def rootLabel: A
  /** The child nodes of this tree. */
  def subForest: Stream[Tree[A]]
}

object Tree extends TreeFunctions with TreeInstances {
  /** Construct a tree node with no children. */
  def apply[A](root: => A): Tree[A] = leaf(root)

  object Node {
    def unapply[A](t: Tree[A]): Option[(A, Stream[Tree[A]])] = Some((t.rootLabel, t.subForest))
  }
}

trait TreeFunctions {
  /** Construct a new Tree node. */
  def node[A](root: => A, forest: => Stream[Tree[A]]): Tree[A] = new Tree[A] {
    lazy val rootLabel = root
    lazy val subForest = forest
    override def toString = "<tree>"
  }
  /** Construct a tree node with no children. */
  def leaf[A](root: => A): Tree[A] = node(root, Stream.empty)
  ...
}
</scala>

これは**多分木** (multi-way tree) だ。木を作るためには全てのデータ型に注入された `node` メソッドと `leaf` メソッドを使う:

<scala>
trait TreeV[A] extends Ops[A] {
  def node(subForest: Tree[A]*): Tree[A] = Tree.node(self, subForest.toStream)

  def leaf: Tree[A] = Tree.leaf(self)
}
</scala>

本にある `freeTree` を実装してみる:

<scala>
scala> def freeTree: Tree[Char] =
         'P'.node(
           'O'.node(
             'L'.node('N'.leaf, 'T'.leaf),
             'Y'.node('S'.leaf, 'A'.leaf)),
           'L'.node(
             'W'.node('C'.leaf, 'R'.leaf),
             'A'.node('A'.leaf, 'C'.leaf)))
freeTree: scalaz.Tree[Char]
</scala>

LYAHFGG:

> Notice that `W` in the tree there? Say we want to change it into a `P`. 

`Tree.Node` という抽出子があるので、`changeToP` は以下のように実装できる:

<scala>
scala> def changeToP(tree: Tree[Char]): Tree[Char] = tree match {
         case Tree.Node(x, Stream(
           l, Tree.Node(y, Stream(
             Tree.Node(_, Stream(m, n)), r)))) =>
           x.node(l, y.node('P'.node(m, n), r))
       }
changeToP: (tree: scalaz.Tree[Char])scalaz.Tree[Char]
</scala>

これを実装するのはかなり面倒だった。Zipper をみてみよう。

### TreeLoc

LYAHFGG:

> With a pair of `Tree a` and `Breadcrumbs a`, we have all the information to rebuild the whole tree and we also have a focus on a sub-tree. This scheme also enables us to easily move up, left and right. Such a pair that contains a focused part of a data structure and its surroundings is called a *zipper*, because moving our focus up and down the data structure resembles the operation of a zipper on a regular pair of pants. 

`Tree` のための Zipper は Scalaz では [`TreeLoc`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/TreeLoc.scala) と呼ばれている:

<scala>
sealed trait TreeLoc[A] {
  import TreeLoc._
  import Tree._

  /** The currently selected node. */
  val tree: Tree[A]
  /** The left siblings of the current node. */
  val lefts: TreeForest[A]
  /** The right siblings of the current node. */
  val rights: TreeForest[A]
  /** The parent contexts of the current node. */
  val parents: Parents[A]
  ...
}

object TreeLoc extends TreeLocFunctions with TreeLocInstances {
  def apply[A](t: Tree[A], l: TreeForest[A], r: TreeForest[A], p: Parents[A]): TreeLoc[A] =
    loc(t, l, r, p)
}

trait TreeLocFunctions {
  type TreeForest[A] = Stream[Tree[A]]
  type Parent[A] = (TreeForest[A], A, TreeForest[A])
  type Parents[A] = Stream[Parent[A]]
}
</scala>

Zipper のデータ構造は一般的に穴を表現する。現在フォーカスがあるノードは `tree` で表されているけど、木全体を再び構築するのに必要なその他全ても保存されている。`TreeLoc` を作るには `Tree` から `loc` メソッドを呼び出す:

<scala>
scala> freeTree.loc
res0: scalaz.TreeLoc[Char] = scalaz.TreeLocFunctions$$anon$2@6439ca7b
</scala>

`TreeLoc` はフォーカスを移動するのに DOM API のような様々なメソッドを実装する:

<scala>
sealed trait TreeLoc[A] {
  ...
  /** Select the parent of the current node. */
  def parent: Option[TreeLoc[A]] = ...
  /** Select the root node of the tree. */
  def root: TreeLoc[A] = ...
  /** Select the left sibling of the current node. */
  def left: Option[TreeLoc[A]] = ...
  /** Select the right sibling of the current node. */
  def right: Option[TreeLoc[A]] = ...
  /** Select the leftmost child of the current node. */
  def firstChild: Option[TreeLoc[A]] = ...
  /** Select the rightmost child of the current node. */
  def lastChild: Option[TreeLoc[A]] = ...
  /** Select the nth child of the current node. */
  def getChild(n: Int): Option[TreeLoc[A]] = ...
  /** Select the first immediate child of the current node that satisfies the given predicate. */
  def findChild(p: Tree[A] => Boolean): Option[TreeLoc[A]] = ...
  /** Get the label of the current node. */
  def getLabel: A = ...
  ...
}
</scala>

`freeTree` の `W` にフォーカスを移動するのは以下のように書ける:

<scala>
scala> freeTree.loc.getChild(2) >>= {_.getChild(1)}
res8: Option[scalaz.TreeLoc[Char]] = Some(scalaz.TreeLocFunctions$$anon$2@417ef051)

scala> freeTree.loc.getChild(2) >>= {_.getChild(1)} >>= {_.getLabel.some}
res9: Option[Char] = Some(W)
</scala>

`getChild` が `Option[TreeLoc[A]]` を返すためにモナディック連鎖の `>>=` を使っているけど、これは `flatMap` と同じことだ。`getChild` がちょっと変わっているのが 1-base の添字を使っていることだ。変更を加えて新しい `TreeLoc` を作るメソッドも色々あるけど、便利そうなのは以下のものだ:

<scala>
  /** Modify the current node with the given function. */
  def modifyTree(f: Tree[A] => Tree[A]): TreeLoc[A] = ...
  /** Modify the label at the current node with the given function. */
  def modifyLabel(f: A => A): TreeLoc[A] = ...
  /** Insert the given node as the last child of the current node and give it focus. */
  def insertDownLast(t: Tree[A]): TreeLoc[A] = ...
</scala>

ラベルを `'P'` 変更してみよう:

<scala>
scala> val newFocus = freeTree.loc.getChild(2) >>= {_.getChild(1)} >>= {_.modifyLabel({_ => 'P'}).some}
newFocus: Option[scalaz.TreeLoc[Char]] = Some(scalaz.TreeLocFunctions$$anon$2@107a26d0)
</scala>

`newFocus` から木を再構築するには `toTree` メソッドを呼ぶだけでいい:

<scala>
scala> newFocus.get.toTree
res19: scalaz.Tree[Char] = <tree>

scala> newFocus.get.toTree.draw foreach {_.print}
P|O+- ||  L+- |  ||  |  N+- |  |  ||  |  T`- |  |  ||  Y`- |  |   |  S+-    |  |   |  A`-    |  |L`- |   P+-    ||     C+- |     ||     R`- |     |   A`-    |      A+-       |      C`- 
</scala>

木の中身を検証するのに `Tree` の `draw` があるみたいだけど、改行を入れても入れなくても変な表示になった。

### Stream に注目する

LYAHFGG:

> Zippers can be used with pretty much any data structure, so it's no surprise that they can be used to focus on sub-lists of lists.

リストの Zipper のかわりに、Scalaz は `Stream` 向けのものを提供する。Haskell の遅延評価のため、Scala の `Stream` を Haskell のリストを考えるのは理にかなっているのかもしれない。これが [`Zipper`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Zipper.scala) だ:

<scala>
sealed trait Zipper[+A] {
  val focus: A
  val lefts: Stream[A]
  val rights: Stream[A]
  ...
}
</scala>

Zipper を作るには `Stream` に注入された `toZipper` か `zipperEnd` メソッドを使う:

<scala>
trait StreamOps[A] extends Ops[Stream[A]] {
  final def toZipper: Option[Zipper[A]] = s.toZipper(self)
  final def zipperEnd: Option[Zipper[A]] = s.zipperEnd(self)
  ...
}
</scala>

使ってみる:

<scala>
scala> Stream(1, 2, 3, 4)
res23: scala.collection.immutable.Stream[Int] = Stream(1, ?)

scala> Stream(1, 2, 3, 4).toZipper
res24: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 1, <rights>))
</scala>

`TreeLoc` 同様に `Zipper` にも移動のために多くのメソッドが用意されてある:

<scala>
sealed trait Zipper[+A] {
  ...
  /** Possibly moves to next element to the right of focus. */
  def next: Option[Zipper[A]] = ...
  def nextOr[AA >: A](z: => Zipper[AA]): Zipper[AA] = next getOrElse z
  def tryNext: Zipper[A] = nextOr(sys.error("cannot move to next element"))
  /** Possibly moves to the previous element to the left of focus. */
  def previous: Option[Zipper[A]] = ...
  def previousOr[AA >: A](z: => Zipper[AA]): Zipper[AA] = previous getOrElse z
  def tryPrevious: Zipper[A] = previousOr(sys.error("cannot move to previous element"))
  /** Moves focus n elements in the zipper, or None if there is no such element. */
  def move(n: Int): Option[Zipper[A]] = ...
  def findNext(p: A => Boolean): Option[Zipper[A]] = ...
  def findPrevious(p: A => Boolean): Option[Zipper[A]] = ...
  
  def modify[AA >: A](f: A => AA) = ...
  def toStream: Stream[A] = ...
  ...
}
</scala>

使ってみるとこうなる:

<scala>
scala> Stream(1, 2, 3, 4).toZipper >>= {_.next}
res25: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 2, <rights>))

scala> Stream(1, 2, 3, 4).toZipper >>= {_.next} >>= {_.next}
res26: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 3, <rights>))

scala> Stream(1, 2, 3, 4).toZipper >>= {_.next} >>= {_.next} >>= {_.previous}
res27: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 2, <rights>))
</scala>

現在のフォーカスを変更して `Stream` に戻すには `modify` と `toStream` メソッドを使う:

<scala>
scala> Stream(1, 2, 3, 4).toZipper >>= {_.next} >>= {_.next} >>= {_.modify {_ => 7}.some}
res31: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 7, <rights>))

scala> res31.get.toStream.toList
res32: List[Int] = List(1, 2, 7, 4)
</scala>

これは `for` 構文を使って書くこともできる:

<scala>
scala> for {
         z <- Stream(1, 2, 3, 4).toZipper
         n1 <- z.next
         n2 <- n1.next
       } yield { n2.modify {_ => 7} }
res33: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 7, <rights>))
</scala>

読みやすいとは思うけど、何行も使うから場合によりけりだと思う。

Learn You a Haskell for Great Good はひとまずこれでおしまい。Scalaz が提供する全てのものはカバーしなかったけど、基礎からゆっくりと入っていくのにとても良い本だったと思う。Haskell に対応する型を探しているうちに Scalaz のソースを読む勘がついてきたので、あとは色々調べながらでもいけそうだ。

とりあえず、紹介する機会を逸した型クラスがいくつかあるので紹介したい。

### Id

[Hoogle](http://www.haskell.org/hoogle/?hoogle=Identity) を使って Haskell の型クラスを調べることができる。例えば、[`Control.Monad.Identity`](http://hackage.haskell.org/packages/archive/mtl/latest/doc/html/Control-Monad-Identity.html) をみてみよう:

> The `Identity` monad is a monad that does not embody any computational strategy. It simply applies the bound function to its input without any modification. Computationally, there is no reason to use the `Identity` monad instead of the much simpler act of simply applying functions to their arguments. The purpose of the `Identity` monad is its fundamental role in the theory of monad transformers. Any monad transformer applied to the `Identity` monad yields a non-transformer version of that monad.

Scalaz に対応する型はこれだ:

<scala>
  /** The strict identity type constructor. Can be thought of as `Tuple1`, but with no
   *  runtime representation.
   */
  type Id[+X] = X
</scala>

モナド変換子は後回しにするとして、面白いのは全てのデータ型はその型の `Id` となれることだ:

<scala>
scala> (0: Id[Int])
res39: scalaz.Scalaz.Id[Int] = 0
</scala>

Scalaz はこの `Id` 経由でいくつかの便利なメソッドを導入する:

<scala>
trait IdOps[A] extends Ops[A] {
  /**Returns `self` if it is non-null, otherwise returns `d`. */
  final def ??(d: => A)(implicit ev: Null <:< A): A =
    if (self == null) d else self
  /**Applies `self` to the provided function */
  final def |>[B](f: A => B): B = f(self)
  final def squared: (A, A) = (self, self)
  def left[B]: (A \/ B) = \/.left(self)
  def right[B]: (B \/ A) = \/.right(self)
  final def wrapNel: NonEmptyList[A] = NonEmptyList(self)
  /** @return the result of pf(value) if defined, otherwise the the Zero element of type B. */
  def matchOrZero[B: Monoid](pf: PartialFunction[A, B]): B = ...
  /** Repeatedly apply `f`, seeded with `self`, checking after each iteration whether the predicate `p` holds. */
  final def doWhile(f: A => A, p: A => Boolean): A = ...
  /** Repeatedly apply `f`, seeded with `self`, checking before each iteration whether the predicate `p` holds. */
  final def whileDo(f: A => A, p: A => Boolean): A = ...
  /** If the provided partial function is defined for `self` run this,
   * otherwise lift `self` into `F` with the provided [[scalaz.Pointed]]. */
  def visit[F[_] : Pointed](p: PartialFunction[A, F[A]]): F[A] = ...
}
</scala>

`|>` で式の後に関数の適用を書くことができる:

<scala>
scala> 1 + 2 + 3 |> {_.point[List]}
res45: List[Int] = List(6)

scala> 1 + 2 + 3 |> {_ * 6}
res46: Int = 36
</scala>

`visit` も興味深い:

<scala>
scala> 1 visit { case x@(2|3) => List(x * 2) }
res55: List[Int] = List(1)

scala> 2 visit { case x@(2|3) => List(x * 2) }
res56: List[Int] = List(4)
</scala>

### Length

長さを表現した型クラスもある。以下が [`Length` 型クラスのコントラクト](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Length.scala)だ:

<scala>
trait Length[F[_]]  { self =>
  def length[A](fa: F[A]): Int
}
</scala>

これは `length` メソッドを導入する。Scala 標準ライブラリだと `SeqLike` で入ってくるため、`SeqLike` を継承しないけど長さを持つデータ構造があれば役に立つのかもしれない。

### Index

コンテナへのランダムアクセスを表すのが [`Index`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Index.scala) だ:

<scala>
trait Index[F[_]]  { self =>
  def index[A](fa: F[A], i: Int): Option[A]
}
</scala>

これは `index` と `indexOr` メソッドを導入する:

<scala>
trait IndexOps[F[_],A] extends Ops[F[A]] {
  final def index(n: Int): Option[A] = F.index(self, n)
  final def indexOr(default: => A, n: Int): A = F.indexOr(self, default, n)
}
</scala>

これは `List(n)` に似ているけど、範囲外の添字で呼び出すと `None` が返る:

<scala>
scala> List(1, 2, 3)(3)
java.lang.IndexOutOfBoundsException: 3
        ...

scala> List(1, 2, 3) index 3
res62: Option[Int] = None
</scala>

続きはまた後で。
