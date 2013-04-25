
### Unapply

One thing that I've been fighting the Scala compiler over is the lack of type inference support across the different kinded types like `F[M[_, _]]` and `F[M[_]]`, and `M[_]` and `F[M[_]]`.

For example, an instance of `Applicative[M[_]]` is `(* -> *) -> *` (a type constructor that takes another type constructor that that takes exactly one type). It's known that `Int => Int` can be treated as an applicative by treating it as `Int => A`:

```scala
scala> Applicative[Function1[Int, Int]]
<console>:14: error: Int => Int takes no type parameters, expected: one
              Applicative[Function1[Int, Int]]
                          ^

scala> Applicative[({type l[A]=Function1[Int, A]})#l]
res14: scalaz.Applicative[[A]Int => A] = scalaz.std.FunctionInstances$$anon$2@56ae78ac
```

This becomes annoying for `M[_,_]` like `Validation`. One of the way Scalaz helps you out is to provide meta-instances of typeclass instance called [`Unapply`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Unapply.scala).

```scala
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
```

When Scalaz method like `traverse` requires you to pass in `Applicative[M[_]]`, it instead could ask for `Unapply[Applicative, X]`. During compile time, Scalac can look through all the implicit converters to see if it can coerce `Function1[Int, Int]` into `M[A]` by fixing or adding a parameter and of course using an existing typeclass instance.

```scala
scala> implicitly[Unapply[Applicative, Function1[Int, Int]]]
res15: scalaz.Unapply[scalaz.Applicative,Int => Int] = scalaz.Unapply_0$$anon$9@2e86566f
```

The feature I added yesterday allows type `A` to be promoted as `M[A]` by adding a fake type constructor. This let us treat `Int` as `Applicative` easier. But because it still requires `TC0: TC[({type λ[α] = A0})#λ]` implicitly, it does not allow just any type to be promoted as `Applicative`.

```scala
scala> implicitly[Unapply[Applicative, Int]]
res0: scalaz.Unapply[scalaz.Applicative,Int] = scalaz.Unapply_3$$anon$1@5179dc20

scala> implicitly[Unapply[Applicative, Any]]
<console>:14: error: Unable to unapply type `Any` into a type constructor of kind `M[_]` that is classified by the type class `scalaz.Applicative`
1) Check that the type class is defined by compiling `implicitly[scalaz.Applicative[<type constructor>]]`.
2) Review the implicits in object Unapply, which only cover common type 'shapes'
(implicit not found: scalaz.Unapply[scalaz.Applicative, Any])
              implicitly[Unapply[Applicative, Any]]
                        ^
```

Works. The upshot of all this is that we can now rewrite the following a bit cleaner:

```scala
scala> val failedTree: Tree[Validation[String, Int]] = 1.success[String].node(
         2.success[String].leaf, "boom".failure[Int].leaf)
failedTree: scalaz.Tree[scalaz.Validation[String,Int]] = <tree>

scala> failedTree.sequence[({type l[X]=Validation[String, X]})#l, Int]
res2: scalaz.Validation[java.lang.String,scalaz.Tree[Int]] = Failure(boom)
```scala

Here's `sequenceU`:

```scala
scala> failedTree.sequenceU
res3: scalaz.Validation[String,scalaz.Tree[Int]] = Failure(boom)
```

Boom.

