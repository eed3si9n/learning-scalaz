
### Tree

Let's start the final chapter of Learn You a Haskell for Great Good: [Zippers](http://learnyouahaskell.com/zippers):

> In this chapter, we'll see how we can take some data structure and focus on a part of it in a way that makes changing its elements easy and walking around it efficient.

I can see how this could be useful in Scala since equality of case classes are based on its content and not the heap location. This means that even if you just want to identify different nodes under a tree structure if they happen to have the same type and content Scala would treat the same.

Instead of implementing our own tree, let's use Scalaz's [`Tree`]($scalazBaseUrl$/core/src/main/scala/scalaz/Tree.scala):

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

This is a multi-way tree. To create a tree use `node` and `leaf` methods injected to all data types:

```scala
trait TreeV[A] extends Ops[A] {
  def node(subForest: Tree[A]*): Tree[A] = Tree.node(self, subForest.toStream)

  def leaf: Tree[A] = Tree.leaf(self)
}
```

Let's implement `freeTree` from the book using this:

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

> Notice that `W` in the tree there? Say we want to change it into a `P`.

Using `Tree.Node` extractor, we could implement `changeToP` as follows:

```scala
scala> def changeToP(tree: Tree[Char]): Tree[Char] = tree match {
         case Tree.Node(x, Stream(
           l, Tree.Node(y, Stream(
             Tree.Node(_, Stream(m, n)), r)))) =>
           x.node(l, y.node('P'.node(m, n), r))
       }
changeToP: (tree: scalaz.Tree[Char])scalaz.Tree[Char]
```

This was a pain to implement. Let's look at the zipper.

### TreeLoc

LYAHFGG:

> With a pair of `Tree a` and `Breadcrumbs a`, we have all the information to rebuild the whole tree and we also have a focus on a sub-tree. This scheme also enables us to easily move up, left and right. Such a pair that contains a focused part of a data structure and its surroundings is called a *zipper*, because moving our focus up and down the data structure resembles the operation of a zipper on a regular pair of pants.

The zipper for `Tree` in Scalaz is called [`TreeLoc`]($scalazBaseUrl$/core/src/main/scala/scalaz/TreeLoc.scala):

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

A zipper data structure represents a hole. We have the current focus represented as `tree`, but everything else that can construct the entire tree back up is also preserved. To create `TreeLoc` call `loc` method on a `Tree`:

```scala
scala> freeTree.loc
res0: scalaz.TreeLoc[Char] = scalaz.TreeLocFunctions\$\$anon\$2@6439ca7b
```

`TreeLoc` implements various methods to move the focus around, similar to DOM API:

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

To move focus to `W` of `freeTree`, we can write something like:

```scala
scala> freeTree.loc.getChild(2) >>= {_.getChild(1)}
res8: Option[scalaz.TreeLoc[Char]] = Some(scalaz.TreeLocFunctions\$\$anon\$2@417ef051)

scala> freeTree.loc.getChild(2) >>= {_.getChild(1)} >>= {_.getLabel.some}
res9: Option[Char] = Some(W)
```

Note `getChild` returns an `Option[TreeLoc[A]]` so we need to use monadic chaining `>>=`, which is the same as `flatMap`. The odd thing is that `getChild` uses 1-based index! There are various methods to create a new `TreeLoc` with modification, but useful looking ones are:

```scala
  /** Modify the current node with the given function. */
  def modifyTree(f: Tree[A] => Tree[A]): TreeLoc[A] = ...
  /** Modify the label at the current node with the given function. */
  def modifyLabel(f: A => A): TreeLoc[A] = ...
  /** Insert the given node as the last child of the current node and give it focus. */
  def insertDownLast(t: Tree[A]): TreeLoc[A] = ...
```

So let's modify the label to `'P'`:

```scala
scala> val newFocus = freeTree.loc.getChild(2) >>= {_.getChild(1)} >>= {_.modifyLabel({_ => 'P'}).some}
newFocus: Option[scalaz.TreeLoc[Char]] = Some(scalaz.TreeLocFunctions\$\$anon\$2@107a26d0)
```

To reconstruct a new tree from `newFocus` we just call `toTree` method:

```scala
scala> newFocus.get.toTree
res19: scalaz.Tree[Char] = <tree>

scala> newFocus.get.toTree.draw foreach {_.print}
P|O+- ||  L+- |  ||  |  N+- |  |  ||  |  T`- |  |  ||  Y`- |  |   |  S+-    |  |   |  A`-    |  |L`- |   P+-    ||     C+- |     ||     R`- |     |   A`-    |      A+-       |      C`-
```

To see check what's inside the tree there's `draw` method on `Tree`, but it looks odd printed with or without newline.
