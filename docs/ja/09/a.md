
### Tree

[すごいHaskellたのしく学ぼう](http://www.amazon.co.jp/dp/4274068854) の最終章 [Zippers](http://learnyouahaskell.com/zippers) を始めよう:

> この章では、いくつかのデータ構造に、そのデータ構造の一部分に注目するための **Zipper** を備える方法を紹介します。Zipper はデータ構造の要素の更新を簡単にし、データ構造を辿るという操作を効率的にしてくれるんです。

Scala の case class の等価性はヒープ内の位置じゃなくて内容で決まる。そのため、木構造内の複数のノードを識別するだけでももし偶然同じ型と内容があれば Scala は同じもの扱いしてしまう。

独自の木を実装するかわりに、Scalaz の [`Tree`]($scalazBaseUrl$/core/src/main/scala/scalaz/Tree.scala) を使おう:

```scala
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
```

これは**多分木** (multi-way tree) だ。木を作るためには全てのデータ型に注入された `node` メソッドと `leaf` メソッドを使う:

```scala
trait TreeV[A] extends Ops[A] {
  def node(subForest: Tree[A]*): Tree[A] = Tree.node(self, subForest.toStream)

  def leaf: Tree[A] = Tree.leaf(self)
}
```

本にある `freeTree` を実装してみる:

```scala
scala> def freeTree: Tree[Char] =
         'P'.node(
           'O'.node(
             'L'.node('N'.leaf, 'T'.leaf),
             'Y'.node('S'.leaf, 'A'.leaf)),
           'L'.node(
             'W'.node('C'.leaf, 'R'.leaf),
             'A'.node('A'.leaf, 'C'.leaf)))
freeTree: scalaz.Tree[Char]
```

LYAHFGG:

> ほら、木の中に `W` が見えますか？あれを `P` に変えたいな。どうすればできるでしょう？

`Tree.Node` という抽出子があるので、`changeToP` は以下のように実装できる:

```scala
scala> def changeToP(tree: Tree[Char]): Tree[Char] = tree match {
         case Tree.Node(x, Stream(
           l, Tree.Node(y, Stream(
             Tree.Node(_, Stream(m, n)), r)))) =>
           x.node(l, y.node('P'.node(m, n), r))
       }
changeToP: (tree: scalaz.Tree[Char])scalaz.Tree[Char]
```

これを実装するのはかなり面倒だった。Zipper をみてみよう。

### TreeLoc

LYAHFGG:

> `Tree a` と `Breadcrumbs a` のペアは、元の木全体を復元するのに必要な情報に加えて、ある部分木に注目した状態というのを表現しています。このスキームなら、木の中を上、左、右へと自由自在に移動できます。
> あるデータ構造の注目点、および周辺の情報を含んでいるデータ構造は **Zipper** と呼ばれます。注目点をデータ構造に沿って上下させる操作は、ズボンのジッパーを上下させる操作に似ているからです。

`Tree` のための Zipper は Scalaz では [`TreeLoc`]($scalazBaseUrl$/core/src/main/scala/scalaz/TreeLoc.scala) と呼ばれている:

```scala
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
```

Zipper のデータ構造は一般的に穴を表現する。現在フォーカスがあるノードは `tree` で表されているけど、木全体を再び構築するのに必要なその他全ても保存されている。`TreeLoc` を作るには `Tree` から `loc` メソッドを呼び出す:

```scala
scala> freeTree.loc
res0: scalaz.TreeLoc[Char] = scalaz.TreeLocFunctions\$\$anon\$2@6439ca7b
```

`TreeLoc` はフォーカスを移動するのに DOM API のような様々なメソッドを実装する:

```scala
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
```

`freeTree` の `W` にフォーカスを移動するのは以下のように書ける:

```scala
scala> freeTree.loc.getChild(2) >>= {_.getChild(1)}
res8: Option[scalaz.TreeLoc[Char]] = Some(scalaz.TreeLocFunctions\$\$anon\$2@417ef051)

scala> freeTree.loc.getChild(2) >>= {_.getChild(1)} >>= {_.getLabel.some}
res9: Option[Char] = Some(W)
```

`getChild` が `Option[TreeLoc[A]]` を返すためにモナディック連鎖の `>>=` を使っているけど、これは `flatMap` と同じことだ。`getChild` がちょっと変わっているのが 1-base の添字を使っていることだ。変更を加えて新しい `TreeLoc` を作るメソッドも色々あるけど、便利そうなのは以下のものだ:

```scala
  /** Modify the current node with the given function. */
  def modifyTree(f: Tree[A] => Tree[A]): TreeLoc[A] = ...
  /** Modify the label at the current node with the given function. */
  def modifyLabel(f: A => A): TreeLoc[A] = ...
  /** Insert the given node as the last child of the current node and give it focus. */
  def insertDownLast(t: Tree[A]): TreeLoc[A] = ...
```

ラベルを `'P'` 変更してみよう:

```scala
scala> val newFocus = freeTree.loc.getChild(2) >>= {_.getChild(1)} >>= {_.modifyLabel({_ => 'P'}).some}
newFocus: Option[scalaz.TreeLoc[Char]] = Some(scalaz.TreeLocFunctions\$\$anon\$2@107a26d0)
```

`newFocus` から木を再構築するには `toTree` メソッドを呼ぶだけでいい:

```scala
scala> newFocus.get.toTree
res19: scalaz.Tree[Char] = <tree>

scala> newFocus.get.toTree.draw foreach {_.print}
P|O+- ||  L+- |  ||  |  N+- |  |  ||  |  T`- |  |  ||  Y`- |  |   |  S+-    |  |   |  A`-    |  |L`- |   P+-    ||     C+- |     ||     R`- |     |   A`-    |      A+-       |      C`- 
```

木の中身を検証するのに `Tree` の `draw` があるみたいだけど、改行を入れても入れなくても変な表示になった。
