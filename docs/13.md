  [day12]: http://eed3si9n.com/learning-scalaz-day12

<div class="floatingimage">
<img src="http://eed3si9n.com/images/hambeifutenbw.jpg">
<div class="credit">e.e d3si9n</div>
</div>

[Yesterday][day12] we skimmed two papers by Jeremy Gibbons and quickly looked at origami programming and applicative traversal. Instead of reading something, why don't we focus on using Scalaz today.

### implicits review

Scalaz makes heavy use of implicits. Both as a user and an extender of the library, it's important to have general idea on where things are coming from. Let's quickly review Scala's imports and implicits!

In Scala, imports are used for two purposes:
1. To include names of values and types into the scope.
2. To include implicits into the scope.

Implicits are for 4 purposes that I can think of:
1. To provide typeclass instances.
2. To inject methods and operators. (static monkey patching)
3. To declare type constraints.
4. To retrieve type information from compiler.

Implicits are selected in the following precedence:
1. Values and converters accessible without prefix via local declaration, imports, outer scope, inheritance, and current package object. Inner scope can shadow values when they are named the same. 
2. Implicit scope. Values and converters declared in companion objects and package object of the type, its parts, or super types.

### import scalaz._

Now let's see what gets imported with `import scalaz._`.

First, the names. Typeclasses like `Equal[A]` and `Functor[F[_]]` are implemented as trait, and are defined under `scalaz` package. So instead of writing `scalaz.Equal[A]` we can write `Equal[A]`.

Next, also the names, but type aliases. `scalaz`'s package object declares most of the major type aliases like `@@[T, Tag]` and `Reader[E, A]`, which is treated as a specialization of `ReaderT` transformer. Again, these can also be accessed as `scalaz.Reader[E, A]` if you want.

Finally, `idInstance` is defined as typeclass instance of `Id[A]` for `Traverse[F[_]]`, `Monad[F[_]]` etc, but it's not relevant. By virtue of declaring an instance within its package object it will be available, so importing doesn't add much. Let's check this:

<scala>
scala> scalaz.Monad[scalaz.Id.Id]
res1: scalaz.Monad[scalaz.Id.Id] = scalaz.IdInstances$$anon$1@fc98c94
</scala>

No import needed, which is a good thing. So, the merit of `import scalaz._` is for convenience, and it's optional.

### import Scalaz._

What then is `import Scalaz._` doing? Here's the definition of [`Scalaz` object](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Scalaz.scala):

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

This is quite a nice way of organizing the imports. `Scalaz` object itself doesn't define anythig and it just mixes in the traits. We are going to look at each traits in detail, but they can also be imported a la carte, dim sum style. Back to the full course.

#### StateFunctions

Remember, import brings in names and implicits. First, the names. `StateFunctions` defines several functions:

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

By bringing these functions in we can treat `get` and `put` like a global function. Why? This enables DSL we saw on [day 7](http://eed3si9n.com/learning-scalaz-day7):

<scala>
for {
  xs <- get[List[Int]]
  _ <- put(xs.tail)
} yield xs.head
</scala>

#### std.AllFunctions

Second, the names again. [`std.AllFunctions`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/std/AllFunctions.scala) is actually a mixin of traits itself:

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

Each of the above trait bring in various functions into the scope that acts as a global function. For example, `ListFunctions` bring in `intersperse` function that puts a given element in ever other position:

<scala>
scala> intersperse(List(1, 2, 3), 7)
res3: List[Int] = List(1, 7, 2, 7, 3)
</scala>

It's ok. Since I personally use injected methods, I don't have much use to these functions.

#### IdInstances

Although it's named `IdInstances`, it also defines the type alias `Id[A]` as follows:

<scala>
  type Id[+X] = X
</scala>

That's it for the names. Imports can bring in implicits, and I said there are four uses for the implicits. We mostly care about the first two: typeclass instances and injected methods and operators.

#### std.AllInstances

Thus far, I have been intentionally conflating the concept of typeclass instances and method injection (aka enrich my library). But the fact that `List` is a `Monad` and that `Monad` introduces `>>=` operator are two different things.

One of the most interesting design of Scalaz 7 is that it rigorously separates the two concepts into "instance" and "syntax." Even if it makes logical sense to some users, the choice of symbolic operators can often be a point of contention with any libraries. Libraries and tools such as sbt, dispatch, and specs introduce its own DSL, and their effectiveness have been hotly debated. To make the matter complicated, injected methods may conflict with each other when more than one DSLs are used together.

[`std.AllInstances`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/std/AllInstances.scala) is a mixin of typeclass instances for built-in (`std`) data structures:

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

Next are the injected methods and operators. All of them are defined under `scalaz.syntax` package. [`syntax.ToTypeClassOps`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/Syntax.scala) introduces all the injected methods for typeclasses:

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

For example, [`syntax.ToBindOps`] implicitly converts `F[A]` where `[F: Bind]` into `BindOps[F, A]` that implements `>>=` operator.

#### syntax.ToDataOps

[`syntax.ToDataOps`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/Syntax.scala) introduces injected methods for data structures defined in Scalaz:

<scala>
trait ToDataOps extends ToIdOps with ToTreeOps with ToWriterOps with ToValidationOps with ToReducerOps with ToKleisliOps
</scala>

[`IdOps`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/IdOps.scala) methods are injected to all types, and are mostly there for convenience:

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

Interestingly, `ToTreeOps` converts all data types to [`TreeV[A]`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/ToTreeOps.scala) injecting two methods:

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

So these are injected methods to create `Tree`.

<scala>
scala> 1.node(2.leaf)
res7: scalaz.Tree[Int] = <tree>
</scala>

The same goes for [`WriterV[A]`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/ToWriterOps.scala), [`ValidationV[A]`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/ValidationV.scala), [`ReducerV[A]`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/ReducerV.scala), and [`KleisliIdOps[A]`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/KleisliV.scala):

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

So most of the mixins under `syntax.ToDataOps` introduces methods to all types to create Scalaz data structure.

#### syntax.std.ToAllStdOps

Finally, we have [`syntax.std.ToAllStdOps`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/std/ToAllStdOps.scala), which introduces methods and operators to Scala's standard types.

<scala>
package scalaz
package syntax
package std

trait ToAllStdOps
  extends ToBooleanOps with ToOptionOps with ToOptionIdOps with ToListOps with ToStreamOps
  with ToFunction2Ops with ToFunction1Ops with ToStringOps with ToTupleOps with ToMapOps with ToEitherOps
</scala>

This is the fun stuff. [`BooleanOps`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/std/BooleanOps.scala) introduces shorthands for all sorts of things:

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

The `option` operator is very useful. The ternary operator looks like a shorter notation than if-else.

[`OptionOps`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/std/OptionOps.scala) also introduces something similar:

<scala>
scala> 1.some? "foo" | "bar"
res28: String = foo

scala> 1.some | 2
res30: Int = 1
</scala>

On the other hand [`ListOps`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/std/ListOps.scala) introduced traditional Monad related things:

<scala>
scala> List(1, 2) filterM {_ => List(true, false)}
res37: List[List[Int]] = List(List(1, 2), List(1), List(2), List())
</scala>

### a la carte style

Or, I'd like to call dim sum style, where they bring in a cart load of chinese dishes and you pick what you want.

If for whatever reason if you do not wish to import the entire `Scalaz._`, you can pick and choose.

#### typeclass instances and functions

Typeclass instances are broken down by the data structures. Here's how to get all typeclass instances for `Option`:

<scala>
// fresh REPL
scala> import scalaz.std.option._
import scalaz.std.option._

scala> scalaz.Monad[Option].point(0)
res0: Option[Int] = Some(0)
</scala>

This also brings in the "global" helper functions related to `Option`. Scala standard data structures are found under `scalaz.std` package.

If you just want all instances, here's how to load them all:

<scala>
scala> import scalaz.std.AllInstances._
import scalaz.std.AllInstances._

scala> scalaz.Monoid[Int]
res2: scalaz.Monoid[Int] = scalaz.std.AnyValInstances$$anon$3@784e6f7c
</scala>

Because we have not injected any operators, you would have to work more with helper functions and functions under typeclass instances, which could be exactly what you want.

#### Scalaz typeclass syntax

Typeclass syntax are broken down by the typeclass. Here's how to get injected methods and operators for `Monad`s:

<scala>
scala> import scalaz.syntax.monad._
import scalaz.syntax.monad._

scala> import scalaz.std.option._
import scalaz.std.option._

scala> 0.point[Option]
res0: Option[Int] = Some(0)
</scala>

As you can see, not only `Monad` method was injected but also `Pointed` methods got in too.

Scalaz data structure syntax like `Tree` are also available under `scalaz.syntax` package. Here's how to load all syntax for both the typeclasses and Scalaz's data structure:

<scala>
scala> import scalaz.syntax.all._
import scalaz.syntax.all._

scala> 1.leaf
res0: scalaz.Tree[Int] = <tree>
</scala>

#### standard data structure syntax

Standard data structure syntax are broken down by the data structure. Here's how to get injected methods and operators for `Boolean`:

<scala>
// fresh REPL
scala> import scalaz.syntax.std.boolean._
import scalaz.syntax.std.boolean._

scala> (1 > 10)? "foo" | "bar"
res0: String = bar
</scala>

To load all the standard data structure syntax in:

<scala>
// fresh REPL
scala> import scalaz.syntax.std.all._
import scalaz.syntax.std.all._

scala> 1.some | 2
res1: Int = 1
</scala>

I thought this would be a quick thing, but it turned out to be an entire post.
We'll pick it up from here.
