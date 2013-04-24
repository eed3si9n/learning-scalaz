---
out: Kinds.html
---

  [moott]: http://learnyouahaskell.com/making-our-own-types-and-typeclasses

### Kinds and some type-foo

One section I should've covered yesterday from [Making Our Own Types and Typeclasses][moott] but didn't is about kinds and types. I thought it wouldn't matter much to understand Scalaz, but it does, so we need to have the talk.

[Learn You a Haskell For Great Good][moott] says:

> Types are little labels that values carry so that we can reason about the values. But types have their own little labels, called kinds. A kind is more or less the type of a type. 
> ...
> What are kinds and what are they good for? Well, let's examine the kind of a type by using the :k command in GHCI.

I did not find `:k` command for Scala REPL so I wrote one for Scala 2.10.0-M7. <s>Unlike Haskell's version, it only accepts proper type as input but it's better than nothing!</s> For type constructors, pass in the companion type. (Thanks paulp for the suggestion)

```scala
// requires Scala 2.10.0-M7
def kind[A: scala.reflect.TypeTag]: String = {
  import scala.reflect.runtime.universe._
  def typeKind(sig: Type): String = sig match {
    case PolyType(params, resultType) =>
      (params map { p =>
        typeKind(p.typeSignature) match {
          case "*" => "*"
          case s   => "(" + s + ")"
        }
      }).mkString(" -> ") + " -> *"
    case _ => "*"
  }
  def typeSig(tpe: Type): Type = tpe match {
    case SingleType(pre, sym) => sym.companionSymbol.typeSignature
    case ExistentialType(q, TypeRef(pre, sym, args)) => sym.typeSignature
    case TypeRef(pre, sym, args) => sym.typeSignature
  }
  val sig = typeSig(typeOf[A])
  val s = typeKind(sig)
  sig.typeSymbol.name + "'s kind is " + s + ". " + (s match {
    case "*" =>
      "This is a proper type."
    case x if !(x contains "(") =>
      "This is a type constructor: a 1st-order-kinded type."
    case x =>
      "This is a type constructor that takes type constructor(s): a higher-kinded type."
  })
}
```

Run `sbt console` using `build.sbt` that I posted on day 1, and copy paste the above function. Let's try using it:

```
scala> kind[Int]
res0: String = Int's kind is *. This is a proper type.

scala> kind[Option.type]
res1: String = Option's kind is * -> *. This is a type constructor: a 1st-order-kinded type.

scala> kind[Either.type]
res2: String = Either's kind is * -> * -> *. This is a type constructor: a 1st-order-kinded type.

scala> kind[Equal.type]
res3: String = Equal's kind is * -> *. This is a type constructor: a 1st-order-kinded type.

scala> kind[Functor.type]
res4: String = Functor's kind is (* -> *) -> *. This is a type constructor that takes type constructor(s): a higher-kinded type.
```

From the top. `Int` and every other types that you can make a value out of is called a proper type and denoted with a symbol `*` (read "type"). This is analogous to value `1` at value-level.

A first-order value, or a value constructor like `(_: Int) + 3`, is normally called a function. Similarly, a first-order-kinded type is a type that accepts other types to create a proper type. This is normally called a type constructor. `Option`, `Either`, and `Equal` are all first-order-kinded. To denote that these accept other types, we use curried notation like `* -> *` and `* -> * -> *`. Note, `Option[Int]` is `*`; `Option` is `* -> *`.

A higher-order value like `(f: Int => Int, list: List[Int]) => list map {f}`, a function that accepts other functions is normally called higher-order function. Similarly, a higher-kinded type is a type constructor that accepts other type constructors. It probably should be called a higher-kinded type constructor but the name is not used. These are denoted as `(* -> *) -> *`. 

In case of Scalaz 7, `Equal` and others have the kind `* -> *` while `Functor` and all its derivatives have the kind `(* -> *) -> *`. You wouldn't worry about this if you are using injected operators like:

```scala
scala> List(1, 2, 3).shows
res11: String = [1,2,3]
```

But if you want to use `Show[A].shows`, you have to know it's `Show[List[Int]]`, not `Show[List]`. Similarly, if you want to lift a function, you need to know that it's `Functor[F]` (`F` is for `Functor`):

```scala
scala> Functor[List[Int]].lift((_: Int) + 2)
<console>:14: error: List[Int] takes no type parameters, expected: one
              Functor[List[Int]].lift((_: Int) + 2)
                      ^

scala> Functor[List].lift((_: Int) + 2)
res13: List[Int] => List[Int] = <function1>
```

In [the cheat sheet](http://eed3si9n.com/scalaz-cheat-sheet) I started I originally had type parameters for `Equal` written as `Equal[F]`, which is the same as Scalaz 7's source code. [Adam Rosien @arosien](http://twitter.com/arosien/status/241990437269815296) pointed out to me that it should be `Equal[A]`. Now it makes sense why!
