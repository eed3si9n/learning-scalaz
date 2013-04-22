  [day8]: http://eed3si9n.com/learning-scalaz-day8

On [day 8][day8] we reviewed monadic functions `join`, `filterM`, and `foldLeftM`, implemented safe RPN calculator, looked at `Kleisli` to compose monadic functions, and implemented our own monad `Prob`.

### Tree

Let's start the final chapter of Learn You a Haskell for Great Good: [Zippers](http://learnyouahaskell.com/zippers):

> In this chapter, we'll see how we can take some data structure and focus on a part of it in a way that makes changing its elements easy and walking around it efficient.

I can see how this could be useful in Scala since equality of case classes are based on its content and not the heap location. This means that even if you just want to identify different nodes under a tree structure if they happen to have the same type and content Scala would treat the same.

Instead of implementing our own tree, let's use Scalaz's [`Tree`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Tree.scala):

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

This is a multi-way tree. To create a tree use `node` and `leaf` methods injected to all data types:

<scala>
trait TreeV[A] extends Ops[A] {
  def node(subForest: Tree[A]*): Tree[A] = Tree.node(self, subForest.toStream)

  def leaf: Tree[A] = Tree.leaf(self)
}
</scala>

Let's implement `freeTree` from the book using this:

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

Using `Tree.Node` extractor, we could implement `changeToP` as follows:

<scala>
scala> def changeToP(tree: Tree[Char]): Tree[Char] = tree match {
         case Tree.Node(x, Stream(
           l, Tree.Node(y, Stream(
             Tree.Node(_, Stream(m, n)), r)))) =>
           x.node(l, y.node('P'.node(m, n), r))
       }
changeToP: (tree: scalaz.Tree[Char])scalaz.Tree[Char]
</scala>

This was a pain to implement. Let's look at the zipper.

### TreeLoc

LYAHFGG:

> With a pair of `Tree a` and `Breadcrumbs a`, we have all the information to rebuild the whole tree and we also have a focus on a sub-tree. This scheme also enables us to easily move up, left and right. Such a pair that contains a focused part of a data structure and its surroundings is called a *zipper*, because moving our focus up and down the data structure resembles the operation of a zipper on a regular pair of pants. 

The zipper for `Tree` in Scalaz is called [`TreeLoc`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/TreeLoc.scala):

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

A zipper data structure represents a hole. We have the current focus represented as `tree`, but everything else that can construct the entire tree back up is also preserved. To create `TreeLoc` call `loc` method on a `Tree`:

<scala>
scala> freeTree.loc
res0: scalaz.TreeLoc[Char] = scalaz.TreeLocFunctions$$anon$2@6439ca7b
</scala>

`TreeLoc` implements various methods to move the focus around, similar to DOM API:

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

To move focus to `W` of `freeTree`, we can write something like:

<scala>
scala> freeTree.loc.getChild(2) >>= {_.getChild(1)}
res8: Option[scalaz.TreeLoc[Char]] = Some(scalaz.TreeLocFunctions$$anon$2@417ef051)

scala> freeTree.loc.getChild(2) >>= {_.getChild(1)} >>= {_.getLabel.some}
res9: Option[Char] = Some(W)
</scala>

Note `getChild` returns an `Option[TreeLoc[A]]` so we need to use monadic chaining `>>=`, which is the same as `flatMap`. The odd thing is that `getChild` uses 1-based index! There are various methods to create a new `TreeLoc` with modification, but useful looking ones are:

<scala>
  /** Modify the current node with the given function. */
  def modifyTree(f: Tree[A] => Tree[A]): TreeLoc[A] = ...
  /** Modify the label at the current node with the given function. */
  def modifyLabel(f: A => A): TreeLoc[A] = ...
  /** Insert the given node as the last child of the current node and give it focus. */
  def insertDownLast(t: Tree[A]): TreeLoc[A] = ...
</scala>

So let's modify the label to `'P'`:

<scala>
scala> val newFocus = freeTree.loc.getChild(2) >>= {_.getChild(1)} >>= {_.modifyLabel({_ => 'P'}).some}
newFocus: Option[scalaz.TreeLoc[Char]] = Some(scalaz.TreeLocFunctions$$anon$2@107a26d0)
</scala>

To reconstruct a new tree from `newFocus` we just call `toTree` method:

<scala>
scala> newFocus.get.toTree
res19: scalaz.Tree[Char] = <tree>

scala> newFocus.get.toTree.draw foreach {_.print}
P|O+- ||  L+- |  ||  |  N+- |  |  ||  |  T`- |  |  ||  Y`- |  |   |  S+-    |  |   |  A`-    |  |L`- |   P+-    ||     C+- |     ||     R`- |     |   A`-    |      A+-       |      C`- 
</scala>

To see check what's inside the tree there's `draw` method on `Tree`, but it looks odd printed with or without newline.

### Focusing on Streams

LYAHFGG:

> Zippers can be used with pretty much any data structure, so it's no surprise that they can be used to focus on sub-lists of lists.

Instead of a list zipper, Scalaz provides a zipper for `Stream`. Due to Haskell's laziness, it might actually make sense to think of Scala's `Stream` as Haskell's list. Here's [`Zipper`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Zipper.scala):

<scala>
sealed trait Zipper[+A] {
  val focus: A
  val lefts: Stream[A]
  val rights: Stream[A]
  ...
}
</scala>

To create a zipper use `toZipper` or `zipperEnd` method injected to `Stream`:

<scala>
trait StreamOps[A] extends Ops[Stream[A]] {
  final def toZipper: Option[Zipper[A]] = s.toZipper(self)
  final def zipperEnd: Option[Zipper[A]] = s.zipperEnd(self)
  ...
}
</scala>

Let's try using it.

<scala>
scala> Stream(1, 2, 3, 4)
res23: scala.collection.immutable.Stream[Int] = Stream(1, ?)

scala> Stream(1, 2, 3, 4).toZipper
res24: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 1, <rights>))
</scala>

As with `TreeLoc` there are lots of methods on `Zipper` to move around:

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

Here are these functions in action:

<scala>
scala> Stream(1, 2, 3, 4).toZipper >>= {_.next}
res25: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 2, <rights>))

scala> Stream(1, 2, 3, 4).toZipper >>= {_.next} >>= {_.next}
res26: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 3, <rights>))

scala> Stream(1, 2, 3, 4).toZipper >>= {_.next} >>= {_.next} >>= {_.previous}
res27: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 2, <rights>))
</scala>

To modify the current focus and bring it back to a `Stream`, use `modify` and `toStream` method:

<scala>
scala> Stream(1, 2, 3, 4).toZipper >>= {_.next} >>= {_.next} >>= {_.modify {_ => 7}.some}
res31: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 7, <rights>))

scala> res31.get.toStream.toList
res32: List[Int] = List(1, 2, 7, 4)
</scala>

We can also write this using `for` syntax:

<scala>
scala> for {
         z <- Stream(1, 2, 3, 4).toZipper
         n1 <- z.next
         n2 <- n1.next
       } yield { n2.modify {_ => 7} }
res33: Option[scalaz.Zipper[Int]] = Some(Zipper(<lefts>, 7, <rights>))
</scala>

More readable, I guess, but it does take up lines so it's case by case.

This is pretty much the end of Learn You a Haskell for Great Good. It did not cover everything Scalaz has to offer, but I think it was an exellent way of gently getting introduced to the fundamentals. After looking up the corresponding Scalaz types for Haskell 
types, I am now comfortable enough to find my way around the source code and look things up as I go.

Anyway, let's see some of the typeclasses that we didn't have opportunity to cover.

### Id

Using [Hoogle](http://www.haskell.org/hoogle/?hoogle=Identity) we can look up Haskell typeclasses. For example, let's look at [`Control.Monad.Identity`](http://hackage.haskell.org/packages/archive/mtl/latest/doc/html/Control-Monad-Identity.html):

> The `Identity` monad is a monad that does not embody any computational strategy. It simply applies the bound function to its input without any modification. Computationally, there is no reason to use the `Identity` monad instead of the much simpler act of simply applying functions to their arguments. The purpose of the `Identity` monad is its fundamental role in the theory of monad transformers. Any monad transformer applied to the `Identity` monad yields a non-transformer version of that monad.

Here's the corresponding type in Scalaz:

<scala>
  /** The strict identity type constructor. Can be thought of as `Tuple1`, but with no
   *  runtime representation.
   */
  type Id[+X] = X
</scala>

We need to look at monad transformer later, but one thing that's interesting is that all data types can be `Id` of the type.

<scala>
scala> (0: Id[Int])
res39: scalaz.Scalaz.Id[Int] = 0
</scala>

Scalaz introduces several useful methods via `Id`:

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

`|>` lets you write the function application at the end of an expression:

<scala>
scala> 1 + 2 + 3 |> {_.point[List]}
res45: List[Int] = List(6)

scala> 1 + 2 + 3 |> {_ * 6}
res46: Int = 36
</scala>

`visit` is also kind of interesting:

<scala>
scala> 1 visit { case x@(2|3) => List(x * 2) }
res55: List[Int] = List(1)

scala> 2 visit { case x@(2|3) => List(x * 2) }
res56: List[Int] = List(4)
</scala>

### Length

There's a typeclass that expresses length. Here's [the typeclass contract of `Length`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Length.scala):

<scala>
trait Length[F[_]]  { self =>
  def length[A](fa: F[A]): Int
}
</scala>

This introduces `length` method. In Scala standard library it's introduced by `SeqLike`, so it could become useful if there were data structure that does not extend `SeqLike` that has length.

### Index

For random access into a container, there's [`Index`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Index.scala):

<scala>
trait Index[F[_]]  { self =>
  def index[A](fa: F[A], i: Int): Option[A]
}
</scala>

This introduces `index` and `indexOr` methods:

<scala>
trait IndexOps[F[_],A] extends Ops[F[A]] {
  final def index(n: Int): Option[A] = F.index(self, n)
  final def indexOr(default: => A, n: Int): A = F.indexOr(self, default, n)
}
</scala>

This is similar to `List(n)` except it returns `None` for an out-of-range index:

<scala>
scala> List(1, 2, 3)(3)
java.lang.IndexOutOfBoundsException: 3
        ...

scala> List(1, 2, 3) index 3
res62: Option[Int] = None
</scala>

We'll pick it up from here later.
